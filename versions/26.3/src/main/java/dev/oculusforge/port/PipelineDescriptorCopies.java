package dev.oculusforge.port;

import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import java.util.Optional;

/** Copies descriptor state, not a compiled GPU pipeline or its ownership. */
public final class PipelineDescriptorCopies {
    private PipelineDescriptorCopies() {}

    public static RenderPipeline.Builder builder(RenderPipeline source) {
        var colors = source.getColorTargetStates().toArray(ColorTargetState[]::new);
        var vertices = source.getVertexFormatBindings().toArray(VertexFormat[]::new);
        var snippet = new RenderPipeline.Snippet(
            source.getShaders(), Optional.of(source.getShaderDefines()),
            Optional.of(source.getBindGroupLayouts()), colors, colors.length,
            Optional.ofNullable(source.getDepthStencilState()),
            Optional.of(source.getPolygonMode()), Optional.of(source.isCull()),
            vertices, Optional.of(source.getPrimitiveTopology()), source.pushConstantSize());
        return RenderPipeline.builder(snippet);
    }
}
