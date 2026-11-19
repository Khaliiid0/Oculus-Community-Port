package dev.oculusforge.render.world;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import dev.oculusforge.render.program.*;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/** Dedicated textured End sky; no auxiliary reads or per-draw resource allocation. */
public record EndSkyPipeline(RenderPipeline pipeline, boolean usesWorldMatrices) {
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
                .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
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
        var compiled = RenderSystem.getDevice().precompilePipeline(pipeline, (id, type) -> {
            if (id.equals(vertexId) && type == ShaderType.VERTEX) return vertex;
            if (id.equals(fragmentId) && type == ShaderType.FRAGMENT) return fragment;
            return null;
        });
        if (!compiled.isValid()) throw new ShaderCompatibilityException("OpenGL rejected native End sky; see latest.log");
        return new EndSkyPipeline(pipeline, program.usesWorldMatrices());
    }
}
