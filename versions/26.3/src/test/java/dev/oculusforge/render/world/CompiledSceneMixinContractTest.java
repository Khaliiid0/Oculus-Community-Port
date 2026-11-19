package dev.oculusforge.render.world;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;

class CompiledSceneMixinContractTest {
    record Info(String descriptor,int returns) {}
    private Info method(String resource,String name) throws Exception {
        var returnCount=new int[1];var descriptor=new String[1];
        try(var input=Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream(resource))) {
            new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public MethodVisitor visitMethod(int access,String method,String desc,String sig,String[] exceptions) {
                    if(!method.equals(name))return null;descriptor[0]=desc;
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override public void visitInsn(int opcode) { if(opcode==Opcodes.ARETURN)returnCount[0]++; }
                    };
                }
            },ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
        }
        assertNotNull(descriptor[0],name);return new Info(descriptor[0],returnCount[0]);
    }

    @Test void sceneInterceptorUsesActualCompiledArgumentAndReturnType() throws Exception {
        var handler=Type.getMethodType(method("dev/oculusforge/mixin/RenderPassMixin.class","oculus$sceneFormat").descriptor());
        var target=Type.getMethodType(method("com/mojang/renderpearl/frontend/FrontendRenderPass.class","setPipeline").descriptor());
        assertEquals(1,handler.getArgumentTypes().length);assertEquals(1,target.getArgumentTypes().length);
        assertEquals(target.getArgumentTypes()[0],handler.getArgumentTypes()[0]);
        assertEquals(target.getArgumentTypes()[0],handler.getReturnType());
        assertEquals("Lcom/mojang/renderpearl/api/pipeline/CompiledRenderPipeline;",handler.getReturnType().getDescriptor());
    }

    @Test void bothMinecraftCacheLookupPathsHaveExactlyThePinnedReturns() throws Exception {
        var strict=method("com/mojang/blaze3d/systems/RenderSystem.class","getCompiledPipeline");
        var nullable=method("com/mojang/blaze3d/systems/RenderSystem.class","getCompiledPipelineNullable");
        assertEquals(strict.descriptor(),nullable.descriptor());assertEquals(1,strict.returns());assertEquals(2,nullable.returns());
        assertEquals("(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;)Lcom/mojang/renderpearl/api/pipeline/CompiledRenderPipeline;",strict.descriptor());
        var callback="(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;)V";
        assertEquals(callback,method("dev/oculusforge/mixin/RenderSystemMixin.class","oculus$remember").descriptor());
        assertEquals(callback,method("dev/oculusforge/mixin/RenderSystemMixin.class","oculus$rememberNullable").descriptor());
    }
}
