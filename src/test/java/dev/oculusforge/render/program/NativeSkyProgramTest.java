package dev.oculusforge.render.program;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeSkyProgramTest {
    private static final String V = """
        varying vec2 uv;
        void main() { uv = gl_MultiTexCoord0.xy; gl_Position = ftransform(); }
        """;
    private static final String F = """
        varying vec2 uv;
        uniform sampler2D texture;
        void main() { gl_FragData[0] = texture2D(texture, uv); }
        """;
    @Test void preparesTexturedPair() throws Exception {
        var p = NativeSkyProgram.adapt(V, F, List.of(0));
        assertEquals(1, p.samplers().size());
        assertEquals("Sampler0", p.samplers().getFirst().name());
        assertTrue(p.fragmentSource().contains("texture(Sampler0, uv)"));
        assertFalse(p.usesWorldMatrices());
    }
    @Test void bindsWorldAndStageInFragment() throws Exception {
        var p = NativeSkyProgram.adapt(V, "uniform int renderStage;\nuniform vec3 fogColor;\n" +
            F.replace("texture2D(texture, uv)", "vec4(fogColor * float(renderStage), 1)"), List.of(0));
        assertTrue(p.usesWorldMatrices());
        assertTrue(p.fragmentSource().contains("oculusNative_fogColor.xyz * float(oculusSkyRenderStage)"));
        assertTrue(p.fragmentSource().contains(NativeWorldBindings.block()));
    }
    @Test void rejectsFeedbackAndDepthReads() {
        for (String name : List.of("colortex0", "gcolor", "depthtex0", "shadowtex0"))
            assertThrows(ShaderCompatibilityException.class, () -> NativeSkyProgram.adapt(V, F.replace("texture;", name + ";").replace("(texture,", "(" + name + ","), List.of(0)));
    }
    @Test void auxiliaryReadIsExplicitlyListed() throws Exception {
        var p = NativeSkyProgram.adapt(V, F.replace("texture;", "gaux1;").replace("(texture,", "(gaux1,"), List.of(0));
        assertEquals(NativeColorSamplers.canonical(4), p.samplers().getFirst().name());
    }
    @Test void rejectsInvalidOutputsAndDepthWrites() {
        assertThrows(ShaderCompatibilityException.class, () -> NativeSkyProgram.adapt(V, F, List.of(0, 1)));
        assertThrows(ShaderCompatibilityException.class, () -> NativeSkyProgram.adapt(V, F.replace("[0]", "[1]"), List.of(0)));
        assertThrows(ShaderCompatibilityException.class, () -> NativeSkyProgram.adapt(V, F.replace("gl_FragData[0]", "gl_FragDepth"), List.of(0)));
    }
    @Test void rejectsMismatchedVaryingsAndSamplerTypes() {
        assertThrows(ShaderCompatibilityException.class, () -> NativeSkyProgram.adapt(V, F.replace("varying vec2", "varying vec3"), List.of(0)));
        assertThrows(ShaderCompatibilityException.class, () -> NativeSkyProgram.adapt(V, F.replace("sampler2D", "samplerCube"), List.of(0)));
    }
    @Test void rejectsAliasesAndReservedIdentifiers() {
        assertThrows(ShaderCompatibilityException.class, () -> NativeSkyProgram.adapt(V, "uniform sampler2D tex;\n" + F, List.of(0)));
        assertThrows(ShaderCompatibilityException.class, () -> NativeSkyProgram.adapt(V, F + "\nfloat OculusNoise;", List.of(0)));
    }
    @Test void rejectsMissingAndDuplicateFragmentMain() {
        assertThrows(ShaderCompatibilityException.class, () -> NativeSkyProgram.adapt(V, F.replace("void main()", "void other()"), List.of(0)));
        assertThrows(ShaderCompatibilityException.class, () -> NativeSkyProgram.adapt(V, F + "\nvoid main() {}", List.of(0)));
    }
}
