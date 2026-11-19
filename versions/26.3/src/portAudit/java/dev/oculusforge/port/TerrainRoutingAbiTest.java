package dev.oculusforge.port;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;

/** Pin integration seams to actual 66.1.2 classes without initializing Minecraft. */
class TerrainRoutingAbiTest {
    record Method(String descriptor, List<String> calls) {}
    private Map<String, Method> methods(String resource) throws Exception {
        var methods=new HashMap<String,Method>();
        try(var input=Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream(resource))) {
            new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public MethodVisitor visitMethod(int access,String name,String descriptor,String signature,String[] exceptions) {
                    var calls=new ArrayList<String>();methods.put(name,new Method(descriptor,calls));
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override public void visitMethodInsn(int opcode,String owner,String name,String descriptor,boolean itf) {
                            calls.add(owner+"."+name+descriptor);
                        }
                    };
                }
            },ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
        }
        return methods;
    }

    @Test void pinnedMainLambdaCreatesExactlyOneRestartableCandidate() throws Exception {
        var methods=methods("net/minecraft/client/renderer/LevelRenderer.class");
        String create="com/mojang/renderpearl/api/commands/CommandEncoder.createRenderPass(Ljava/util/function/Supplier;Lcom/mojang/renderpearl/api/textures/GpuTextureView;Ljava/util/Optional;Lcom/mojang/renderpearl/api/textures/GpuTextureView;Ljava/util/OptionalDouble;)Lcom/mojang/renderpearl/api/commands/RenderPass;";
        var lambda=Objects.requireNonNull(methods.get("lambda$addMainPass$0"));
        assertEquals(1,Collections.frequency(lambda.calls(),create));
        assertEquals(1,lambda.calls().stream().filter(c->c.contains("LevelRenderer.executeSolid(")).count());
        assertEquals(1,lambda.calls().stream().filter(c->c.contains("LevelRenderer.executeClassicTransparency(")).count());
        String prepare="net/minecraft/client/renderer/LevelRenderer.addMainPass"+methods.get("addMainPass").descriptor();
        assertEquals(1,Collections.frequency(methods.get("render").calls(),prepare));
        assertEquals("Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;",
            Type.getArgumentTypes(methods.get("addMainPass").descriptor())[3].getDescriptor());
    }

    @Test void bothClassicGroupCallsHaveThePinnedCallerOwnedPassSignature() throws Exception {
        var level=methods("net/minecraft/client/renderer/LevelRenderer.class");
        var terrain=methods("net/minecraft/client/renderer/chunk/ChunkSectionsToRender.class");
        String target="net/minecraft/client/renderer/chunk/ChunkSectionsToRender.renderGroup"+terrain.get("renderGroup").descriptor();
        for(String caller:List.of("executeSolid","executeClassicTransparency")) {
            assertEquals(1,Collections.frequency(level.get(caller).calls(),target),caller);
            assertEquals("Lcom/mojang/renderpearl/api/commands/RenderPass;",
                Type.getArgumentTypes(level.get(caller).descriptor())[2].getDescriptor());
        }
        assertEquals("([Lnet/minecraft/client/renderer/chunk/ChunkSectionLayer;Lcom/mojang/renderpearl/api/textures/GpuSampler;Lcom/mojang/renderpearl/api/commands/RenderPass;Lcom/mojang/renderpearl/api/textures/GpuTextureView;Lcom/mojang/renderpearl/api/textures/GpuTextureView;Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;)V",
            terrain.get("renderLayers").descriptor());
    }

    @Test void eachBackendHasOneCompiledSelectionAndKeepsItsOwnSubmissionKind() throws Exception {
        String select="com/mojang/renderpearl/api/commands/RenderPass.setPipeline(Lcom/mojang/renderpearl/api/pipeline/CompiledRenderPipeline;)V";
        Method separate=methods("net/minecraft/client/renderer/chunk/ChunkSectionsToRender$DrawSeparate.class").get("render");
        Method indirect=methods("net/minecraft/client/renderer/chunk/ChunkSectionsToRender$DrawIndirect.class").get("render");
        assertEquals(separate.descriptor(),indirect.descriptor());
        assertEquals(1,Collections.frequency(separate.calls(),select));
        assertEquals(1,Collections.frequency(indirect.calls(),select));
        assertTrue(separate.calls().stream().anyMatch(c->c.contains("RenderPass.drawMultipleIndexed(")));
        assertTrue(indirect.calls().stream().anyMatch(c->c.contains("RenderPass.drawIndexedIndirect(")));
        assertFalse(indirect.calls().stream().anyMatch(c->c.contains("RenderPass.drawMultipleIndexed(")));
    }

    @Test void gameCameraExtractionUsesWorldPartialTicksNotRemovedCameraTicks() throws Exception {
        assertEquals("(Lnet/minecraft/client/DeltaTracker;F)V",
            methods("net/minecraft/client/renderer/GameRenderer.class").get("extractCamera").descriptor());
    }
}
