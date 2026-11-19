package dev.oculusforge.render.program;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class NativeHelperReachabilityTest {
    private static final String V = "void main(){gl_Position=ftransform();}";
    private static final String F = "void main(){gl_FragColor=vec4(1.0);}";
    private static final String NOISE = "uniform sampler2D noisetex;\n"
        + "vec4 unusedNoise(){return texture2D(noisetex,vec2(.5));}\n";

    @Test void gpuFixturePreparesRealTerrainAndShadowWithoutInventedLibraryInputs() throws Exception {
        var root = java.nio.file.Path.of(getClass().getClassLoader().getResource("native-helper-pruning").toURI());
        var compiler = new PackProgramCompiler(dev.oculusforge.shaderpack.ShaderPackLimits.DEFAULT);
        var prepared = compiler.prepare(new dev.oculusforge.shaderpack.ShaderPackDescriptor("helpers", "Helpers", root,
            dev.oculusforge.shaderpack.ShaderPackDescriptor.Kind.DIRECTORY));
        var world = prepared.world().variant("shaders");
        assertNotNull(world.nativeTerrain(), world.nativeTerrainIssue());
        assertNotNull(world.nativeShadow(), world.nativeShadowIssue());
        assertEquals(List.of(0, 4), world.terrainTargets());
        for (var nativeProgram : List.of(world.nativeTerrain(), world.nativeShadow())) {
            assertFalse(nativeProgram.usesNoise());
            for (String source : List.of(nativeProgram.vertexSource(), nativeProgram.fragmentSource())) {
                assertFalse(source.contains("deadWrapper"));
                assertFalse(source.contains("deadNoise"));
                assertFalse(source.contains("missingLibraryInput"));
                assertFalse(source.contains("noisetex"));
                assertTrue(source.contains("markerLeaf"));
                assertTrue(source.contains("activeMarker"));
            }
        }
        assertEquals(0, compiler.cachedPipelineCount());
    }

    @Test void unusedVertexLibraryTexturesAreRemovedInBothWorldStagesAndMeshModes() throws Exception {
        for (boolean extended : List.of(false, true)) for (boolean shadow : List.of(false, true)) {
            var program = NativeTerrainProgram.adaptWithMesh(NOISE + V, F, List.of(0), shadow, extended);
            assertFalse(program.vertexSource().contains("noisetex"));
            assertFalse(program.vertexSource().contains("unusedNoise"));
            assertFalse(program.usesNoise());
            assertFalse(program.usesWorldMatrices());
        }
    }

    @Test void activeVertexSamplingIsNotSilentlyRemovedOrBoundToAFakeTexture() {
        for (boolean extended : List.of(false, true)) for (boolean shadow : List.of(false, true)) {
            String vertex = NOISE + "void main(){gl_Position=ftransform()+unusedNoise();}";
            var error = assertThrows(ShaderCompatibilityException.class,
                () -> NativeTerrainProgram.adaptWithMesh(vertex, F, List.of(0), shadow, extended));
            assertTrue(error.getMessage().contains("noisetex"));
        }
    }

    @Test void fragmentHelpersDoNotRequestUnusedNoiseDepthOrShadowResources() throws Exception {
        String fragment = NOISE + "uniform sampler2D depthtex1;\nuniform sampler2D shadowcolor1;\n"
            + "vec4 unusedDepth(){return texture2D(depthtex1,vec2(.5));}\n"
            + "vec4 unusedShadow(){return texture2D(shadowcolor1,vec2(.5));}\n" + F;
        for (boolean shadow : List.of(false, true)) {
            var program = NativeTerrainProgram.adaptWithMesh(V, fragment, List.of(0), shadow, false);
            assertFalse(program.usesNoise());
            assertFalse(program.usesOpaqueWorldDepth());
            assertFalse(program.usesShadowTextures());
            assertFalse(program.fragmentSource().contains("unusedDepth"));
        }
        var active = NativeTerrainProgram.adapt(V, NOISE + "void main(){gl_FragColor=unusedNoise();}", List.of(0));
        assertTrue(active.usesNoise());
        assertTrue(active.fragmentSource().contains("vec4 unusedNoise()"));
    }

    @Test void reachableTextureFeedbackStillRejects() {
        for (String sampler : List.of("colortex0", "shadowcolor1", "depthtex1")) {
            String fragment = "uniform sampler2D " + sampler + ";\n"
                + "vec4 helper(){return texture2D(" + sampler + ",vec2(.5));}\n"
                + "void main(){gl_FragColor=helper();}";
            assertThrows(ShaderCompatibilityException.class,
                () -> NativeTerrainProgram.adaptWithMesh(V, fragment, List.of(0), true, false), sampler);
        }
    }

    @Test void transitiveHelpersRemainAndDeadCyclesAreRemovedWithoutChangingPositions() throws Exception {
        String source = "uniform float missing;\nfloat deadA(){return deadB()+missing;}\n"
            + "float deadB(){return deadA();}\nfloat leaf(){return 1.;}\n"
            + "float used(){return leaf();}\nvoid main(){gl_Position=ftransform()*used();}";
        String result = NativeUnusedUniforms.adaptReachable(source);
        assertEquals(source.length(), result.length());
        assertEquals(source.lines().count(), result.lines().count());
        assertFalse(result.contains("deadA"));
        assertFalse(result.contains("deadB"));
        assertFalse(result.contains("missing"));
        assertTrue(result.contains("float leaf()"));
        assertTrue(result.contains("float used()"));
        assertNotNull(NativeTerrainProgram.adapt(source, F, List.of(0)));
    }

    @Test void overloadsPrototypesAndGlobalInitializersRetainRequiredInputs() throws Exception {
        for (String source : List.of(
            "uniform float missing;\nfloat helper(float x){return x;}\n"
                + "vec3 helper(vec3 x){return x*missing;}\nvoid main(){gl_Position=ftransform()*helper(1.);}",
            "uniform float missing;\nfloat helper();\nfloat helper(){return missing;}\n" + V,
            "uniform float missing;\nfloat helper(){return missing;}\nfloat value=helper();\n" + V)) {
            assertEquals(source, NativeUnusedUniforms.adaptReachable(source));
            assertThrows(ShaderCompatibilityException.class, () -> NativeTerrainProgram.adapt(source, F, List.of(0)));
        }
    }

    @Test void malformedOrDuplicateUniformsAreValidatedEvenWhenOnlyDeadHelpersMentionThem() {
        for (String declaration : List.of("uniform float noisetex;", "uniform sampler2D noisetex[2];",
            "uniform sampler2D noisetex;\nuniform sampler2D noisetex;", "uniform int screenBrightness;",
            "uniform float blindness=0.;", "uniform float sunAngle[2];", "uniform float missing;\nuniform float missing;")) {
            String source = declaration + "\nfloat dead(){return 1.;}\n" + V;
            assertThrows(ShaderCompatibilityException.class, () -> NativeUnusedUniforms.adaptReachable(source), declaration);
            assertThrows(ShaderCompatibilityException.class, () -> NativeTerrainProgram.adapt(source, F, List.of(0)), declaration);
        }
    }

    @Test void uniformBudgetCannotBeEvadedByDeadHelperPruning() {
        var source = new StringBuilder();
        for (int i = 0; i < 513; i++) source.append("uniform float unused").append(i).append(";\n");
        source.append("float dead(){return unused0;}\n").append(V);
        var error = assertThrows(ShaderCompatibilityException.class,
            () -> NativeUnusedUniforms.adaptReachable(source.toString()));
        assertTrue(error.getMessage().contains("512 uniform declarations"));
    }

    @Test void macrosSubroutinesAndMalformedBodiesKeepTheConservativePath() throws Exception {
        for (String source : List.of("#define CALL unusedNoise\n" + NOISE + V,
            "subroutine float Type();\n" + NOISE + V, NOISE + V + "float broken(){"))
            assertEquals(source, NativeUnusedUniforms.adaptReachable(source));
    }

    @Test void reservedNamesExtensionsAndInvalidMeshDeclarationsStillRejectInDeadCode() {
        for (String prefix : List.of("float dead(){return oculusNativeIntrusion;}",
            "float dead(){return OculusNoise;}", "#extension GL_UNKNOWN_extension : require\n",
            "attribute vec3 at_tangent;\n", "attribute vec4 at_tangent;\nattribute vec4 at_tangent;\n"))
            assertThrows(ShaderCompatibilityException.class,
                () -> NativeTerrainProgram.adaptWithMesh(prefix + "\n" + V, F, List.of(0), false, true));
    }
}
