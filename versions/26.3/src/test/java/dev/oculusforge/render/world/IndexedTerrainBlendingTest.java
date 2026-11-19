package dev.oculusforge.render.world;

import static org.junit.jupiter.api.Assertions.*;
import com.mojang.renderpearl.api.pipeline.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;

class IndexedTerrainBlendingTest {
    @Test void differentEnableFlagsDoNotNeedIndependentFactorSupport() {
        var on = new ColorTargetState(BlendFunction.TRANSLUCENT);
        assertFalse(IndexedTerrainBlending.requiresIndependentFunctions(new ColorTargetState[]{ColorTargetState.DEFAULT,on,ColorTargetState.DEFAULT}));
        assertFalse(IndexedTerrainBlending.requiresIndependentFunctions(new ColorTargetState[]{on,on,null}));
        assertFalse(IndexedTerrainBlending.requiresIndependentFunctions(new ColorTargetState[0]));
    }
    @Test void distinctColorOrAlphaEquationsRequireExplicitRejectionUntilDescriptorsSupportThem() {
        assertTrue(IndexedTerrainBlending.requiresIndependentFunctions(new ColorTargetState[]{
            new ColorTargetState(BlendFunction.TRANSLUCENT),new ColorTargetState(BlendFunction.ADDITIVE)}));
        assertTrue(IndexedTerrainBlending.requiresIndependentFunctions(new ColorTargetState[]{
            new ColorTargetState(BlendFunction.TRANSLUCENT),new ColorTargetState(new BlendFunction(
                com.mojang.renderpearl.api.pipeline.BlendFactor.SRC_ALPHA,com.mojang.renderpearl.api.pipeline.BlendFactor.ONE_MINUS_SRC_ALPHA,
                com.mojang.renderpearl.api.pipeline.BlendFactor.ZERO,com.mojang.renderpearl.api.pipeline.BlendFactor.ONE))}));
    }
    @Test void supportedEnableDifferencesPassButDifferentFactorTuplesRejectBeforeBuilding() throws Exception {
        var supported=new ColorTargetState[]{ColorTargetState.DEFAULT,new ColorTargetState(BlendFunction.TRANSLUCENT)};
        assertDoesNotThrow(()->IndexedTerrainBlending.requireSupported(supported));
        var unsupported=new ColorTargetState[]{new ColorTargetState(BlendFunction.TRANSLUCENT),new ColorTargetState(BlendFunction.ADDITIVE)};
        var error=assertThrows(dev.oculusforge.render.program.ShaderCompatibilityException.class,
            ()->IndexedTerrainBlending.requireSupported(unsupported));
        assertTrue(error.getMessage().contains("pipeline descriptors"));
    }
    @Test void pinnedBackendAppliesCachedPerAttachmentEnablesAndSharedFactors() throws Exception {
        Map<String,Integer> calls=new HashMap<>();
        Set<Integer> intLocalSlots=new HashSet<>();
        try(var input=Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream(
            "com/mojang/renderpearl/backend/opengl/GlRenderPipeline.class"))) {
            new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public MethodVisitor visitMethod(int access,String name,String descriptor,String signature,String[] exceptions) {
                    if (!name.equals("bind")) return null;
                    assertEquals("()V",descriptor);
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override public void visitMethodInsn(int opcode,String owner,String name,String descriptor,boolean isInterface) {
                            if(owner.equals("com/mojang/renderpearl/backend/opengl/GlStateManager")
                                && Set.of("_enableBlend","_disableBlend","_blendFuncSeparate","_blendEquationSeparate").contains(name))
                                calls.merge(name+descriptor,1,Integer::sum);
                        }
                        @Override public void visitLocalVariable(String name,String descriptor,String signature,Label start,Label end,int index) {
                            if (descriptor.equals("I")) intLocalSlots.add(index);
                        }
                    };
                }
            },ClassReader.SKIP_FRAMES);
        }
        assertEquals(Map.of("_enableBlend(I)V",1,"_disableBlend(I)V",1,"_blendFuncSeparate(IIII)V",1,"_blendEquationSeparate(II)V",1),calls);

    }

    @Test void upstreamBlendCacheUsesIndexedGlCallsAndNeverGlobalEnableDisable() throws Exception {
        for (var method : java.util.List.of("_enableBlend","_disableBlend")) {
            var calls=new java.util.ArrayList<String>();
            try(var input=java.util.Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream(
                "com/mojang/renderpearl/backend/opengl/GlStateManager.class"))) {
                new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override public MethodVisitor visitMethod(int access,String name,String descriptor,String signature,String[] exceptions) {
                        if(!name.equals(method))return null;
                        assertEquals("(I)V",descriptor);
                        return new MethodVisitor(Opcodes.ASM9) {
                            @Override public void visitMethodInsn(int opcode,String owner,String name,String descriptor,boolean itf) {
                                if(owner.startsWith("org/lwjgl/opengl/"))calls.add(name+descriptor);
                            }
                        };
                    }
                },ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
            }
            assertEquals(java.util.List.of((method.equals("_enableBlend")?"glEnablei":"glDisablei")+"(II)V"),calls);
        }
    }
}
