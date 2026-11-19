package dev.oculusforge.render.pipeline;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.pipeline.*;
import com.mojang.renderpearl.api.textures.*;
import dev.oculusforge.port.*;
import dev.oculusforge.render.program.*;
import java.nio.ByteOrder;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.resources.Identifier;

/** One 1x1 draw per consuming variant/frame. No readback and no interaction ray. */
public final class CenterDepthPass implements AutoCloseable {
    private static final DeferredScratchTargets.Accounting ACCOUNTING=new DeferredScratchTargets.Accounting();
    private static final AtomicInteger RINGS=new AtomicInteger();
    private static final Identifier VERTEX=Identifier.fromNamespaceAndPath("oculus","center_depth/vertex");
    private static final Identifier FRAGMENT=Identifier.fromNamespaceAndPath("oculus","center_depth/fragment");
    private static final String FRAGMENT_SOURCE="""
        #version 330 core
        uniform sampler2D OculusCenterSource;
        uniform sampler2D OculusCenterHistory;
        layout(std140) uniform OculusCenterState { vec4 State; };
        layout(location=0) out vec4 OculusCenterResult;
        void main() {
            float currentDepth=1.0-texelFetch(OculusCenterSource,textureSize(OculusCenterSource,0)/2,0).r;
            float result=currentDepth;
            if(State.y>0.5) result=mix(texelFetch(OculusCenterHistory,ivec2(0),0).r,currentDepth,State.x);
            OculusCenterResult=vec4(result,0.0,0.0,1.0);
        }
        """;
    private final float halfLife;
    private final CenterDepthHistory history=new CenterDepthHistory();
    private final DeferredScratchTargets targets;
    private OwnedPipeline pipeline;
    private MappableRingBuffer uniforms;
    private boolean closed,initialized,logged;

    public static CenterDepthPass create(float halfLife) {
        RenderSystem.assertOnRenderThread();CenterDepthHistory.blend(0,halfLife);
        return new CenterDepthPass(halfLife);
    }
    private CenterDepthPass(float halfLife) {
        this.halfLife=halfLife;
        targets=new DeferredScratchTargets(List.of(ColorBufferFormat.R32F,ColorBufferFormat.R32F),new DeferredScratchTargets.Factory() {
            public GpuTexture texture(ColorBufferFormat format,int width,int height) {
                return RenderSystem.getDevice().createTexture(()->"Oculus center-depth history",
                    GpuTexture.USAGE_RENDER_ATTACHMENT|GpuTexture.USAGE_TEXTURE_BINDING|GpuTexture.USAGE_COPY_DST,
                    format.gpu(),width,height,1,1);
            }
            public GpuTextureView view(GpuTexture texture){return RenderSystem.getDevice().createTextureView(texture);}
        },ACCOUNTING);
        try {
            var descriptor=RenderPipeline.builder().withLocation(Identifier.fromNamespaceAndPath("oculus","center_depth"))
                .withVertexShader(VERTEX).withFragmentShader(FRAGMENT)
                .withBindGroupLayout(BindGroupLayout.builder()
                    .withUniform("OculusCenterSource",UniformType.COMBINED_IMAGE_SAMPLER)
                    .withUniform("OculusCenterHistory",UniformType.COMBINED_IMAGE_SAMPLER)
                    .withUniform("OculusCenterState",UniformType.UNIFORM_BUFFER).build())
                .withPrimitiveTopology(PrimitiveTopology.TRIANGLES).withCull(false)
                .withColorTargetState(new ColorTargetState(Optional.empty(),GpuFormat.R32_FLOAT,15)).build();
            var pair=ShadercInterfacePair.adapt(LegacyFinalVertexAdapter.DEFAULT_SOURCE,FRAGMENT_SOURCE);
            pipeline=OwnedPipeline.compile(PreparedShaderSource.pair(VERTEX,pair.vertex(),FRAGMENT,pair.fragment()),
                source->RenderSystem.getDevice().compilePipeline(descriptor,source,net.minecraft.util.Util.backgroundExecutor()));
            uniforms=new MappableRingBuffer(()->"Oculus center-depth state",GpuBuffer.USAGE_UNIFORM|GpuBuffer.USAGE_MAP_WRITE,16);
            RINGS.incrementAndGet();
        } catch(RuntimeException|Error failure) {
            ResourceDisposal.closeAfter(failure,this);throw failure;
        }
    }
    public void reset(){history.reset();}
    public void capture(CommandEncoder encoder,GpuTextureView source,Object world,AnimationClock.Snapshot animation) {
        RenderSystem.assertOnRenderThread();if(closed)throw new IllegalStateException("Closed center-depth pass");
        if(source==null||source.isClosed()||source.texture().getFormat()!=GpuFormat.D32_FLOAT)
            throw new IllegalStateException("Center depth requires the current D32 world depth");
        var next=history.advance(world,animation,source.getWidth(0),source.getHeight(0),animation.frameTime(),halfLife);
        if(next==null)return;
        targets.ensure(1,1);
        if(!initialized) {
            encoder.clearColorTexture(targets.texture(0),new org.joml.Vector4f(1));
            encoder.clearColorTexture(targets.texture(1),new org.joml.Vector4f(1));
            initialized=true;
        }
        try(var mapped=uniforms.currentBuffer().map(false,true)) {
            mapped.data().order(ByteOrder.nativeOrder()).putFloat(0,next.blend()).putFloat(4,next.previous()?1:0)
                .putFloat(8,0).putFloat(12,0);
        }
        try {
            try(var pass=encoder.createRenderPass(()->"Oculus center-depth sample",targets.view(next.destination()),Optional.empty())) {
                pass.setPipeline(pipeline.get());
                var sampler=RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
                pass.setUniform("OculusCenterSource",source,sampler);
                pass.setUniform("OculusCenterHistory",targets.view(next.source()),sampler);
                pass.setUniform("OculusCenterState",uniforms.currentBuffer());
                pass.draw(3,1,0,0);
            }
            history.commit(next);
            if(!logged){logged=true;dev.oculusforge.OculusForge.LOGGER.info(
                "Oculus GPU center depth active: 1x1 R32F ping-pong, halfLife={}, readback=false",halfLife);}
        } finally {uniforms.rotate();}
    }
    public GpuTextureView view(Object world,Object frame) {
        if(closed||!history.current(world,frame))throw new IllegalStateException("Center depth was not sampled for this world/frame");
        return targets.view(history.slot());
    }
    public static int liveTextures(){return ACCOUNTING.textureCount();}
    public static long liveBytes(){return ACCOUNTING.byteCount();}
    public static int liveRings(){return RINGS.get();}
    @Override public void close() {
        RenderSystem.assertOnRenderThread();closed=true;history.reset();
        // The reusable target owner preserves failed handles/accounting for retry.
        ResourceDisposal.closeAll(targets,()->{
            if(uniforms!=null){uniforms.close();uniforms=null;RINGS.decrementAndGet();}
        },()->{if(pipeline!=null){pipeline.close();pipeline=null;}});
    }
}
