package dev.oculusforge.render.program;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import java.util.Optional;
import net.minecraft.resources.Identifier;

/** Pixel-exact format conversion for presentation when no explicit final program exists. */
public final class FullscreenColorTransfer {
    private static final Identifier VERTEX = Identifier.fromNamespaceAndPath("oculus", "transfer/vertex");
    private static final Identifier FRAGMENT = Identifier.fromNamespaceAndPath("oculus", "transfer/fragment");
    private static final java.util.Map<ColorBufferFormat,RenderPipeline> PIPELINES=new java.util.EnumMap<>(ColorBufferFormat.class);
    public static synchronized RenderPipeline pipeline(ColorBufferFormat format) {
        return PIPELINES.computeIfAbsent(format,key->RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("oculus","transfer/"+key.name().toLowerCase(java.util.Locale.ROOT)))
            .withVertexShader(VERTEX).withFragmentShader(FRAGMENT)
            .withBindGroupLayout(BindGroupLayout.builder().withSampler("OculusSource").build())
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES).withCull(false)
            .withColorTargetState(new ColorTargetState(Optional.empty(),key.gpu(),key.writeMask())).build());
    }
    private static final String FRAGMENT_SOURCE = """
        #version 330 core
        uniform sampler2D OculusSource;
        layout(location=0) out vec4 oculusColor;
        void main() { oculusColor = texelFetch(OculusSource, ivec2(gl_FragCoord.xy), 0); }
        """;

    private FullscreenColorTransfer() {}

    public static void compile() throws ShaderCompatibilityException {
        compile(ColorBufferFormat.RGBA8);
    }
    public static void compile(ColorBufferFormat format) throws ShaderCompatibilityException {
        RenderSystem.assertOnRenderThread();
        var compiled = RenderSystem.getDevice().precompilePipeline(pipeline(format), (id, type) -> {
            if (id.equals(VERTEX) && type == ShaderType.VERTEX) return LegacyFinalVertexAdapter.DEFAULT_SOURCE;
            if (id.equals(FRAGMENT) && type == ShaderType.FRAGMENT) return FRAGMENT_SOURCE;
            return null;
        });
        if (!compiled.isValid()) throw new ShaderCompatibilityException("OpenGL rejected the color-format presentation transfer");
    }

    public static void present(CommandEncoder encoder, GpuTextureView source, GpuTextureView destination) {
        present(encoder,source,destination,ColorBufferFormat.RGBA8);
    }
    public static void present(CommandEncoder encoder, GpuTextureView source, GpuTextureView destination,ColorBufferFormat format) {
        if(destination.texture().getFormat()!=format.gpu() || source.texture()==destination.texture())
            throw new IllegalArgumentException("Color transfer requires a matching destination format and distinct source");
        if (source.getWidth(0) != destination.getWidth(0) || source.getHeight(0) != destination.getHeight(0)) {
            throw new IllegalArgumentException("Color transfer requires matching dimensions");
        }
        try (var pass = encoder.createRenderPass(() -> "Oculus color-format presentation", destination, Optional.empty())) {
            pass.setPipeline(pipeline(format));
            pass.bindTexture("OculusSource", source, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            pass.draw(3, 1, 0, 0);
        }
    }
}
