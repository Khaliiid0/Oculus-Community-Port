package dev.oculusforge.render.program;

import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.ShaderType;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import java.util.Optional;
import net.minecraft.resources.Identifier;

/** Pixel-exact format conversion for presentation when no explicit final program exists. */
public final class FullscreenColorTransfer {
    private static final Identifier VERTEX = Identifier.fromNamespaceAndPath("oculus", "transfer/vertex");
    private static final Identifier FRAGMENT = Identifier.fromNamespaceAndPath("oculus", "transfer/fragment");
    private static final java.util.Map<ColorBufferFormat,RenderPipeline> PIPELINES=new java.util.EnumMap<>(ColorBufferFormat.class);
    // Service-scoped and bounded by the enum; disposed on full reload/shutdown.
    private static dev.oculusforge.port.PipelineGeneration<ColorBufferFormat> compiled =
        new dev.oculusforge.port.PipelineGeneration<>(ColorBufferFormat.values().length);
    public static synchronized RenderPipeline pipeline(ColorBufferFormat format) {
        return PIPELINES.computeIfAbsent(format,key->RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("oculus","transfer/"+key.name().toLowerCase(java.util.Locale.ROOT)))
            .withVertexShader(VERTEX).withFragmentShader(FRAGMENT)
            .withBindGroupLayout(BindGroupLayout.builder().withUniform("OculusSource", com.mojang.renderpearl.api.pipeline.UniformType.COMBINED_IMAGE_SAMPLER).build())
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
        var descriptor = pipeline(format);
        try {
            compiled.acquire(format, () -> {
                var pair = dev.oculusforge.port.ShadercInterfacePair.adapt(LegacyFinalVertexAdapter.DEFAULT_SOURCE,FRAGMENT_SOURCE);
                return dev.oculusforge.port.OwnedPipeline.compile(
                    dev.oculusforge.port.PreparedShaderSource.pair(VERTEX,pair.vertex(),FRAGMENT,pair.fragment()),
                    source -> RenderSystem.getDevice().compilePipeline(descriptor,source,net.minecraft.util.Util.backgroundExecutor()));
            });
        } catch (RuntimeException failure) {
            var error = new ShaderCompatibilityException("Shader compiler rejected the color-format presentation transfer; see latest.log");
            error.initCause(failure);
            throw error;
        }
    }

    public static void reset() {
        RenderSystem.assertOnRenderThread();
        var previous = compiled;
        compiled = new dev.oculusforge.port.PipelineGeneration<>(ColorBufferFormat.values().length);
        PIPELINES.clear();
        previous.close();
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
            pass.setPipeline(compiled.get(format));
            pass.setUniform("OculusSource", source, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            pass.draw(3, 1, 0, 0);
        }
    }
}
