package dev.oculusforge.render.world;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

class SceneMixinContractTest {
    @Test void levelRenderWrapperUsesTheExactTargetSignatureNotAssignableInterfaceTypes() throws Exception {
        // Read class files without loading the MixinExtras-only runtime types or a GPU client.
        var wrappers=descriptors("dev/oculusforge/mixin/LevelRendererMixin.class","oculus$sceneScope");
        assertEquals(1,wrappers.size());
        var wrapper=Type.getMethodType(wrappers.getFirst());
        var parameters=wrapper.getArgumentTypes();
        assertEquals("Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;",parameters[parameters.length-1].getDescriptor());
        assertEquals(Type.VOID_TYPE,wrapper.getReturnType());
        assertEquals(Type.getType(org.joml.Vector4f.class),parameters[6]);
        var target=Type.getMethodDescriptor(Type.VOID_TYPE,Arrays.copyOf(parameters,parameters.length-1));
        assertTrue(descriptors("net/minecraft/client/renderer/LevelRenderer.class","render").contains(target),
            ()->"LevelRenderer.render does not match wrapper descriptor "+target);
    }

    private List<String> descriptors(String resource,String method) throws Exception {
        var result=new ArrayList<String>();
        try(var input=Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream(resource),resource)) {
            new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public MethodVisitor visitMethod(int access,String name,String descriptor,String signature,String[] exceptions) {
                    if(method.equals(name))result.add(descriptor);
                    return null;
                }
            },ClassReader.SKIP_CODE|ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
        }
        return result;
    }
}
