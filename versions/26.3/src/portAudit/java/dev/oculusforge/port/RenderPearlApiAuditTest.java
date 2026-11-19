package dev.oculusforge.port;

import static org.junit.jupiter.api.Assertions.*;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.*;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import java.util.Map;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

/** Tests prerequisites only; these are not renderer compatibility certification. */
class RenderPearlApiAuditTest {
    @Test void pipelineCopyPreservesAllNewStateAndCanBeChangedIndependently() {
        var vertex = Identifier.fromNamespaceAndPath("oculus", "vertex");
        var fragment = Identifier.fromNamespaceAndPath("oculus", "fragment");
        var format = VertexFormat.builder(0).addAttribute("Position", GpuFormat.RGB32_FLOAT).build();
        var uniform = BindGroupLayout.builder().withUniform("source", UniformType.COMBINED_IMAGE_SAMPLER).build();
        var source = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("oculus", "source"))
            .withVertexShader(vertex).withFragmentShader(fragment)
            .withBindGroupLayout(uniform).withVertexBinding(0, format)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES).withCull(false)
            .withColorTargetState(0, new ColorTargetState(java.util.Optional.empty(), GpuFormat.RGBA16_FLOAT, 15))
            .withPushConstantSize(16).withShaderDefine("PORT_AUDIT", 3).build();
        var copy = PipelineDescriptorCopies.builder(source)
            .withLocation(Identifier.fromNamespaceAndPath("oculus", "copy"))
            .withColorTargetState(0, new ColorTargetState(java.util.Optional.empty(), GpuFormat.RGBA8_UNORM, 15)).build();
        assertEquals(Map.of(ShaderType.VERTEX, vertex, ShaderType.FRAGMENT, fragment), copy.getShaders());
        assertEquals(source.getShaderDefines(), copy.getShaderDefines());
        assertEquals(source.getBindGroupLayouts(), copy.getBindGroupLayouts());
        assertEquals(source.getVertexFormatBindings(), copy.getVertexFormatBindings());
        assertEquals(source.getDepthStencilState(), copy.getDepthStencilState());
        assertEquals(source.getPolygonMode(), copy.getPolygonMode());
        assertEquals(source.getPrimitiveTopology(), copy.getPrimitiveTopology());
        assertFalse(copy.isCull());
        assertEquals(16, copy.pushConstantSize());
        assertEquals(GpuFormat.RGBA16_FLOAT, source.getColorTargetStates().getFirst().format());
        assertEquals(GpuFormat.RGBA8_UNORM, copy.getColorTargetStates().getFirst().format());
    }

    @Test void renderPassNowRequiresAnOwnedCompiledPipeline() throws Exception {
        assertNotNull(RenderPass.class.getMethod("setPipeline", CompiledRenderPipeline.class));
        assertThrows(NoSuchMethodException.class, () -> RenderPass.class.getMethod("setPipeline", RenderPipeline.class));
        assertNotNull(CompiledRenderPipeline.class.getMethod("close"));
        assertNotNull(ShaderSource.class.getMethod("close"));
        assertNotNull(ShaderSource.class.getMethod("getInclude", Identifier.class));
    }

    @Test void bothTerrainSubmissionBackendsExistAndNeedExplicitIntegration() throws Exception {
        assertNotNull(Class.forName("net.minecraft.client.renderer.chunk.ChunkSectionsToRender$DrawSeparate"));
        assertNotNull(Class.forName("net.minecraft.client.renderer.chunk.ChunkSectionsToRender$DrawIndirect"));
        var terrain = net.minecraft.client.renderer.chunk.ChunkSectionsToRender.class;
        assertNotNull(terrain.getMethod("renderGroup",
            net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup.class, RenderPass.class,
            com.mojang.renderpearl.api.textures.GpuSampler.class,
            com.mojang.renderpearl.api.textures.GpuTextureView.class, boolean.class));
        assertThrows(NoSuchMethodException.class, () -> terrain.getMethod("renderGroup",
            net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup.class,
            com.mojang.renderpearl.api.textures.GpuSampler.class));
        assertNotNull(RenderPass.class.getMethod("drawIndexedIndirect",
            com.mojang.renderpearl.api.buffers.GpuBufferSlice.class, int.class));
        assertThrows(ClassNotFoundException.class, () -> Class.forName("com.mojang.blaze3d.opengl.GlCommandEncoder"));
    }

    @Test void endSkyHasExactlyOneCompiledPipelineSelectionCall() throws Exception {
        var calls = new java.util.ArrayList<String>();
        try (var input = java.util.Objects.requireNonNull(getClass().getClassLoader()
            .getResourceAsStream("net/minecraft/client/renderer/SkyRenderer.class"))) {
            new org.objectweb.asm.ClassReader(input).accept(new org.objectweb.asm.ClassVisitor(org.objectweb.asm.Opcodes.ASM9) {
                @Override public org.objectweb.asm.MethodVisitor visitMethod(int access, String name, String descriptor,
                    String signature, String[] exceptions) {
                    if (!name.equals("renderEndSky")) return null;
                    assertEquals("(Lcom/mojang/renderpearl/api/commands/RenderPass;)V", descriptor);
                    return new org.objectweb.asm.MethodVisitor(org.objectweb.asm.Opcodes.ASM9) {
                        @Override public void visitMethodInsn(int opcode, String owner, String name,
                            String descriptor, boolean isInterface) {
                            if (owner.equals("com/mojang/renderpearl/api/commands/RenderPass") && name.equals("setPipeline"))
                                calls.add(descriptor);
                        }
                    };
                }
            }, org.objectweb.asm.ClassReader.SKIP_DEBUG | org.objectweb.asm.ClassReader.SKIP_FRAMES);
        }
        assertEquals(java.util.List.of("(Lcom/mojang/renderpearl/api/pipeline/CompiledRenderPipeline;)V"), calls);
    }
}
