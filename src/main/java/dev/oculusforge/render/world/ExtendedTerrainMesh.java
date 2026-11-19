package dev.oculusforge.render.world;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.oculusforge.render.program.TerrainMeshMode;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.Identifier;

/** Only chunk-layer pipelines use this layout; DefaultVertexFormat.BLOCK is untouched. */
public final class ExtendedTerrainMesh {
    public static final VertexFormat FORMAT = VertexFormat.builder(0)
        .addAttribute("Position", GpuFormat.RGB32_FLOAT)
        .addAttribute("Color", GpuFormat.RGBA8_UNORM)
        .addAttribute("UV0", GpuFormat.RG32_FLOAT)
        .addAttribute("UV2", GpuFormat.RG16_SINT)
        .addAttribute("OculusNormal", GpuFormat.RGBA8_SNORM)
        .addAttribute("OculusTangent", GpuFormat.RGBA8_SNORM)
        .addAttribute("OculusMidUV", GpuFormat.RG32_FLOAT)
        .addAttribute("OculusState", GpuFormat.R32_SINT).build();
    private static final Map<RenderPipeline, RenderPipeline> PIPELINES = new IdentityHashMap<>();

    private ExtendedTerrainMesh() { }

    public static VertexFormat format() { return TerrainMeshMode.EXTENDED ? FORMAT : DefaultVertexFormat.BLOCK; }

    public static synchronized RenderPipeline pipeline(RenderPipeline source) {
        if (!TerrainMeshMode.EXTENDED || source.getVertexFormatBinding(0) == FORMAT) return source;
        return PIPELINES.computeIfAbsent(source, original -> {
            if (PIPELINES.size() >= 16) throw new IllegalStateException("Extended chunk pipeline cache limit exceeded");
            if (!DefaultVertexFormat.BLOCK.equals(original.getVertexFormatBinding(0)))
                throw new IllegalStateException("Extended terrain cannot replace a foreign chunk vertex layout: " + original.getLocation());
            var colors = original.getColorTargetStates();
            var snippet = new RenderPipeline.Snippet(Optional.of(original.getVertexShader()),
                Optional.of(original.getFragmentShader()), Optional.of(original.getShaderDefines()),
                Optional.of(original.getBindGroupLayouts()), colors, colors.length,
                Optional.ofNullable(original.getDepthStencilState()), Optional.of(original.getPolygonMode()),
                Optional.of(original.isCull()), original.getVertexFormatBindings(), Optional.of(original.getPrimitiveTopology()));
            return RenderPipeline.builder(snippet)
                .withLocation(Identifier.fromNamespaceAndPath("oculus", "extended/" + original.getLocation().getNamespace() + "/" + original.getLocation().getPath()))
                .withVertexBinding(0, FORMAT).build();
        });
    }
}
