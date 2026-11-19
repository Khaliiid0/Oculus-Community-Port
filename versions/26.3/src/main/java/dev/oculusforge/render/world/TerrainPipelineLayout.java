package dev.oculusforge.render.world;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderPipelines;

/** CPU descriptors only. Vanilla supplies binding 1/base-instance offsets for indirect draws. */
final class TerrainPipelineLayout {
    private TerrainPipelineLayout() {}

    static RenderPipeline.Builder builder(boolean indirect, VertexFormat mesh) {
        return RenderPipeline.builder(indirect ? RenderPipelines.MULTIDRAW_TERRAIN_SNIPPET : RenderPipelines.TERRAIN_SNIPPET)
            .withVertexBinding(0, mesh);
    }
}
