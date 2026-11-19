package dev.oculusforge.render.world;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;

/** Integration seams only, not evidence that Mixin has transformed a running client. */
class TerrainBackendContractTest {
    @Test void redirectForwardsItsActualBackendKindInsteadOfGuessingFromLocationStrings() throws Exception {
        var calls=new ArrayList<String>();var checks=new ArrayList<String>();
        read("dev/oculusforge/mixin/TerrainBackendMixin.class",new ClassVisitor(Opcodes.ASM9) {
            @Override public MethodVisitor visitMethod(int access,String name,String desc,String signature,String[] exceptions) {
                if(!name.equals("oculus$select"))return null;
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override public void visitTypeInsn(int opcode,String type) { if(opcode==Opcodes.INSTANCEOF)checks.add(type); }
                    @Override public void visitMethodInsn(int opcode,String owner,String name,String descriptor,boolean itf) {
                        if(name.equals("selectTerrainPipeline"))calls.add(owner+descriptor);
                    }
                };
            }
        });
        assertEquals(List.of("net/minecraft/client/renderer/chunk/ChunkSectionsToRender$DrawIndirect"),checks);
        assertEquals(List.of("dev/oculusforge/render/world/WorldRenderHooks(Lcom/mojang/renderpearl/api/commands/RenderPass;Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Z)V"),calls);
    }

    @Test void activationCompilesBothBackendFamiliesIntoTheSameRollbackGeneration() throws Exception {
        var flags=new ArrayList<Integer>();
        read("dev/oculusforge/render/world/WorldRenderPipeline.class",new ClassVisitor(Opcodes.ASM9) {
            @Override public MethodVisitor visitMethod(int access,String name,String desc,String signature,String[] exceptions) {
                if(!name.equals("compile"))return null;
                return new MethodVisitor(Opcodes.ASM9) {
                    private int previous=-1;
                    @Override public void visitInsn(int opcode) { previous=opcode; }
                    @Override public void visitMethodInsn(int opcode,String owner,String name,String descriptor,boolean itf) {
                        if(owner.equals("dev/oculusforge/render/world/WorldRenderPipeline")&&name.equals("compileVariant")) {
                            assertEquals(Type.BOOLEAN_TYPE,Type.getArgumentTypes(descriptor)[4]);
                            flags.add(previous);
                        }
                        previous=-1;
                    }
                };
            }
        });
        assertEquals(List.of(Opcodes.ICONST_0,Opcodes.ICONST_1),flags);
    }

    private void read(String resource,ClassVisitor visitor) throws Exception {
        try(var input=Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream(resource))) {
            new ClassReader(input).accept(visitor,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
        }
    }
}
