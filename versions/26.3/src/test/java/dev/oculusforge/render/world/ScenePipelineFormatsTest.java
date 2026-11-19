package dev.oculusforge.render.world;

import static org.junit.jupiter.api.Assertions.*;
import com.mojang.renderpearl.api.pipeline.*;
import dev.oculusforge.render.program.ColorBufferFormat;
import java.util.Optional;
import net.minecraft.client.renderer.RenderPipelines;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ScenePipelineFormatsTest {
    private static final RenderPipeline SOURCE=RenderPipeline.builder(RenderPipelines.TERRAIN_SNIPPET)
        .withLocation(net.minecraft.resources.Identifier.fromNamespaceAndPath("oculus","test_scene_source")).withVertexShader("core/terrain").withFragmentShader("core/terrain")
        .withColorTargetState(ColorTargetState.DEFAULT).build();
    @AfterEach void reset(){ScenePipelineFormats.reset();}
    @Test void promotesOnlyOwnedSlotsAndPreservesEveryOtherPipelineState() {
        var source=SOURCE;
        var promoted=ScenePipelineFormats.adapt(source,ColorBufferFormat.RGBA16F,1);
        assertNotSame(source,promoted);assertSame(promoted,ScenePipelineFormats.adapt(source,ColorBufferFormat.RGBA16F,1));
        assertEquals(ColorBufferFormat.RGBA16F.gpu(),promoted.getColorTargetStates().getFirst().format());
        assertEquals(source.getShaders().get(com.mojang.renderpearl.api.pipeline.ShaderType.VERTEX),promoted.getShaders().get(com.mojang.renderpearl.api.pipeline.ShaderType.VERTEX));assertEquals(source.getShaders().get(com.mojang.renderpearl.api.pipeline.ShaderType.FRAGMENT),promoted.getShaders().get(com.mojang.renderpearl.api.pipeline.ShaderType.FRAGMENT));
        assertEquals(source.getShaderDefines(),promoted.getShaderDefines());
        // 26.3's builder collects layouts in a Set; order is not part of its contract.
        assertEquals(java.util.Set.copyOf(source.getBindGroupLayouts()),java.util.Set.copyOf(promoted.getBindGroupLayouts()));
        assertEquals(source.getBindGroupLayouts().size(),promoted.getBindGroupLayouts().size());
        assertEquals(source.getDepthStencilState(),promoted.getDepthStencilState());assertEquals(source.getPrimitiveTopology(),promoted.getPrimitiveTopology());
        assertEquals(source.isCull(),promoted.isCull());assertEquals(source.getPolygonMode(),promoted.getPolygonMode());
        assertEquals(source.getVertexFormatBindings(),promoted.getVertexFormatBindings());
        assertEquals(source.getColorTargetStates().getFirst().blendFunction(),promoted.getColorTargetStates().getFirst().blendFunction());
        assertEquals(ColorBufferFormat.RGBA8.gpu(),source.getColorTargetStates().getFirst().format());
    }
    @Test void attachmentMaskAndFormatArePartOfTheDescriptorIdentity() {
        var source=RenderPipeline.builder(RenderPipelines.TERRAIN_SNIPPET).withLocation(net.minecraft.resources.Identifier.fromNamespaceAndPath("oculus","test_scene_mrt"))
            .withVertexShader("core/terrain").withFragmentShader("core/terrain")
            .withColorTargetState(ColorTargetState.DEFAULT).withColorTargetState(1,ColorTargetState.DEFAULT).build();
        var first=ScenePipelineFormats.adapt(source,ColorBufferFormat.RGBA16F,1);
        var second=ScenePipelineFormats.adapt(source,ColorBufferFormat.RGBA16F,2);
        assertNotSame(first,second);assertEquals(ColorBufferFormat.RGBA8.gpu(),first.getColorTargetStates().get(1).format());
        assertEquals(ColorBufferFormat.RGBA8.gpu(),second.getColorTargetStates().get(0).format());
        assertNotSame(first,ScenePipelineFormats.adapt(source,ColorBufferFormat.R11F_G11F_B10F,1));
    }
    @Test void unrelatedAlreadyMatchingInvalidAndForeignOutputsDoNotSilentlyChange() {
        var source=SOURCE;
        assertSame(source,ScenePipelineFormats.adapt(source,ColorBufferFormat.RGBA16F,0));
        assertSame(source,ScenePipelineFormats.adapt(source,ColorBufferFormat.RGBA8,1));
        assertThrows(IllegalArgumentException.class,()->ScenePipelineFormats.adapt(source,ColorBufferFormat.RGBA16F,2));
        assertThrows(IllegalArgumentException.class,()->ScenePipelineFormats.adapt(source,ColorBufferFormat.RGBA16F,-1));
        var foreign=RenderPipeline.builder().withLocation(net.minecraft.resources.Identifier.fromNamespaceAndPath("oculus","test_scene_foreign")).withVertexShader("core/terrain")
            .withFragmentShader("core/terrain").withPrimitiveTopology(com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES)
            .withColorTargetState(new ColorTargetState(Optional.empty(),ColorBufferFormat.R8.gpu(),15)).build();
        assertThrows(IllegalStateException.class,()->ScenePipelineFormats.adapt(foreign,ColorBufferFormat.RGBA16F,1));
    }
    @Test void paddedRgbNeverEnablesAnAlphaWriteAndCacheIsStrictlyBounded() {
        assertEquals(7,ScenePipelineFormats.adapt(SOURCE,ColorBufferFormat.RGB16F,1).getColorTargetStates().getFirst().writeMask());
        ScenePipelineFormats.reset();
        for(int i=0;i<ScenePipelineFormats.MAX_DESCRIPTORS;i++) {
            var source=RenderPipeline.builder(RenderPipelines.TERRAIN_SNIPPET).withLocation(net.minecraft.resources.Identifier.fromNamespaceAndPath("oculus","test_scene_capacity_"+i))
                .withVertexShader("core/terrain").withFragmentShader("core/terrain").withColorTargetState(ColorTargetState.DEFAULT).build();
            ScenePipelineFormats.adapt(source,ColorBufferFormat.RGBA16F,1);
        }
        assertEquals(ScenePipelineFormats.MAX_DESCRIPTORS,ScenePipelineFormats.size());
        assertThrows(IllegalStateException.class,()->ScenePipelineFormats.adapt(SOURCE,ColorBufferFormat.RGBA16F,1));
        ScenePipelineFormats.reset();assertEquals(0,ScenePipelineFormats.size());
    }
}
