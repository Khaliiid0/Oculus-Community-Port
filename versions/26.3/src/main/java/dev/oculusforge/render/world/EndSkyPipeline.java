package dev.oculusforge.render.world;

import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.*;
import com.mojang.renderpearl.api.pipeline.ShaderType;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import dev.oculusforge.render.program.*;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/** Dedicated textured End sky; no auxiliary reads or per-draw resource allocation. */
public record EndSkyPipeline(dev.oculusforge.port.OwnedPipeline owner, boolean usesWorldMatrices) implements AutoCloseable {
    public EndSkyPipeline { java.util.Objects.requireNonNull(owner, "owner"); }
    public CompiledRenderPipeline pipeline() { return owner.get(); }
    @Override public void close() { owner.close(); }
    static EndSkyPipeline compile(NativeSkyProgram program, PipelineDescriptorCache<RenderPipeline> cache)
        throws ShaderCompatibilityException {
        if (!SkyRenderStages.endSkyEligible(program))
            throw new ShaderCompatibilityException("End sky requires only the bound vanilla sky texture");
        String vertex = SkyRenderStages.specializeEndSky(program.vertex().source());
        String fragment = SkyRenderStages.specializeEndSky(program.fragmentSource());
        String key = "end-sky/" + NativeTerrainProgram.hash(vertex + '\0' + fragment);
        var vertexId = Identifier.fromNamespaceAndPath("oculus", key + "/vertex");
        var fragmentId = Identifier.fromNamespaceAndPath("oculus", key + "/fragment");
        var pipeline = cache.getOrCreate(key, () -> {
            var builder = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
                .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
                .withLocation(Identifier.fromNamespaceAndPath("oculus", key))
                .withVertexShader(vertexId).withFragmentShader(fragmentId)
                .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
                .withPrimitiveTopology(PrimitiveTopology.QUADS);
            if (program.usesWorldMatrices()) builder.withBindGroupLayout(BindGroupLayout.builder()
                .withUniform(NativeWorldBindings.BLOCK_NAME, UniformType.UNIFORM_BUFFER).build());
            return builder.build();
        });
        final dev.oculusforge.port.OwnedPipeline compiled;
        try {
            var pair = dev.oculusforge.port.ShadercInterfacePair.adapt(vertex,fragment);
            var source = dev.oculusforge.port.PreparedShaderSource.pair(vertexId,pair.vertex(),fragmentId,pair.fragment());
            compiled = dev.oculusforge.port.OwnedPipeline.compile(source,
                sources -> RenderSystem.getDevice().compilePipeline(pipeline, sources, net.minecraft.util.Util.backgroundExecutor()));
        } catch (RuntimeException failure) {
            var error = new ShaderCompatibilityException("Shader compiler rejected native End sky; see latest.log");
            error.initCause(failure);
            throw error;
        }
        try { return new EndSkyPipeline(compiled, program.usesWorldMatrices()); }
        catch (RuntimeException | Error failure) {
            try { compiled.close(); } catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }
}
