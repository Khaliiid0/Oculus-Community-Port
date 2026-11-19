package dev.oculusforge.render.program;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;

class WetnessCallSitesTest {
    @Test void productionCallSitesPassTheVariantStateInsteadOfPerProgramFallbacks() throws Exception {
        var calls = new ArrayList<String>();
        for (String type : List.of("render/pipeline/FullscreenProgramRunner", "render/world/WorldRenderHooks",
            "render/world/WorldRenderPipeline", "render/program/PackProgramCompiler")) {
            try (var input = getClass().getClassLoader().getResourceAsStream("dev/oculusforge/"+type+".class")) {
                assertNotNull(input);
                new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] exceptions) {
                        return new MethodVisitor(Opcodes.ASM9) {
                            @Override public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
                                calls.add(type+" "+owner+"."+name+desc);
                            }
                        };
                    }
                }, ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
            }
        }
        for (String caller : List.of("render/pipeline/FullscreenProgramRunner", "render/world/WorldRenderHooks"))
            assertTrue(calls.stream().anyMatch(c -> c.startsWith(caller+" ")
                && c.contains("PackShaderPipeline$Variant.wetness()")), caller);
        assertTrue(calls.stream().anyMatch(c -> c.startsWith("render/pipeline/FullscreenProgramRunner ")
            && c.contains("PackFinalProgram.updateUniforms(") && c.contains("WetnessSmoother;")));
        assertTrue(calls.stream().anyMatch(c -> c.startsWith("render/world/WorldRenderPipeline ")
            && c.contains("WetnessSmoother.update(")));
        assertTrue(calls.stream().anyMatch(c -> c.startsWith("render/program/PackProgramCompiler ")
            && c.contains("PreparedPackPipeline$Variant.wetnessSettings()")));
    }
}
