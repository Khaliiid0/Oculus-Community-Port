package dev.oculusforge.render.program;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

final class NativeWorldBindingsTest {
    @Test void sceneAliasesBindIdenticallyInBothStagesAndMeshModes() throws Exception {
        String declarations="uniform float screenBrightness, sunAngle, blindness, darknessFactor, maxBlindnessDarkness;\n";
        String value="vec4(screenBrightness,sunAngle,blindness,maxBlindnessDarkness+darknessFactor)";
        for(boolean shadow:new boolean[]{false,true})for(boolean extended:new boolean[]{false,true}) {
            var program=NativeTerrainProgram.adaptWithMesh(declarations+"void main(){gl_Position=ftransform()+"+value+";}",
                declarations+"void main(){gl_FragColor="+value+";}",List.of(0),shadow,extended);
            for(String source:List.of(program.vertexSource(),program.fragmentSource())) {
                assertTrue(source.contains("oculusNative_screenBrightness"));
                assertTrue(source.contains("oculusNative_fogColor.w"));
                assertTrue(source.contains("max(oculusNative_blindness, oculusNative_darknessFactor)"));
                assertTrue(source.contains(NativeWorldBindings.block()));
            }
        }
        assertEquals(976, NativeWorldBindings.BYTE_SIZE);
    }
    private static final String V = "void main() { gl_Position = ftransform(); }";
    private static final String F = "void main() { gl_FragColor = vec4(1); }";

    @Test void viewportUniformsBindInBothStagesWithoutExtendedMeshOrShadowMatrices() throws Exception {
        String declarations = "uniform float viewWidth, viewHeight, aspectRatio;\n";
        for (boolean shadow : new boolean[] {false, true}) {
            var program = NativeTerrainProgram.adaptWithMesh(declarations +
                V.replace("ftransform()", "ftransform() + vec4(viewWidth, viewHeight, aspectRatio, 0)"),
                declarations + F.replace("vec4(1)", "vec4(viewWidth, viewHeight, aspectRatio, 1)"),
                List.of(0), shadow, false);
            assertTrue(program.usesWorldMatrices());
            assertFalse(program.needsShadowMatrices());
            for (String source : List.of(program.vertexSource(), program.fragmentSource())) {
                assertTrue(source.contains(NativeWorldBindings.block()));
                assertTrue(source.contains("vec4(oculusNative_viewWidth, oculusNative_viewHeight, oculusNative_aspectRatio,"));
            }
        }
    }

    @Test void fragmentOnlyViewportStillRequestsSharedBinding() throws Exception {
        var program = NativeTerrainProgram.adaptWithMesh(V,
            "uniform float aspectRatio;\n" + F.replace("vec4(1)", "vec4(aspectRatio)"), List.of(0), false, false);
        assertTrue(program.usesWorldMatrices());
        assertTrue(program.vertexSource().contains(NativeWorldBindings.block()));
        assertFalse(program.needsShadowMatrices());
    }

    @Test void viewportTypesDuplicatesArraysAndSimilarNamesAreValidated() throws Exception {
        for (String declaration : List.of("uniform int viewWidth;", "uniform vec2 viewHeight;",
            "uniform float aspectRatio; uniform float aspectRatio;", "uniform float viewWidth[2];"))
            assertThrows(ShaderCompatibilityException.class,
                () -> NativeTerrainProgram.adapt(V, declaration + "\n" + F, List.of(0)));
        var adapted = NativeWorldBindings.adapt("uniform float viewWidth;\nfloat viewWidthScale = viewWidth;");
        assertTrue(adapted.source().contains("float viewWidthScale = oculusNative_viewWidth;"));
        assertEquals(976, NativeWorldBindings.BYTE_SIZE);
        assertTrue(NativeWorldBindings.block().contains("float oculusNative_viewWidth;\n"
            + "float oculusNative_viewHeight;\nfloat oculusNative_aspectRatio;\nint oculusNative_worldTime;\nint oculusNative_worldDay;\n"
            + "int oculusNative_frameCounter;\nfloat oculusNative_frameTime;\nfloat oculusNative_frameTimeCounter;\nvec4 oculusNative_relativeEyePosition;\n"
            + "int oculusNative_heldBlockLightValue;\nint oculusNative_heldBlockLightValue2;\nint oculusNative_moonPhase;\nfloat oculusNative_rainStrength;\nfloat oculusNative_nightVision;\nfloat oculusNative_endFlashIntensity;\nivec2 oculusNative_eyeBrightness;\nivec2 oculusNative_eyeBrightnessSmooth;\nint oculusNative_oculusBiomeCode;\nfloat oculusNative_far;\n"));
        assertTrue(NativeWorldBindings.block().endsWith("float oculusNative_oculusCustomValue63;\nvec4 oculusNative_fogColor;\nint oculusNative_isEyeInWater;\nfloat oculusNative_darknessFactor;\nfloat oculusNative_blindness;\nfloat oculusNative_screenBrightness;\nvec4 oculusNative_previousCameraPosition;\nivec2 oculusNative_atlasSize;\nint oculusNative_heldItemId;\nint oculusNative_heldItemId2;\nfloat oculusNative_darknessLightFactor;\nfloat oculusNative_fogStart;\nfloat oculusNative_fogEnd;\nfloat oculusNative_fogAlpha;\nvec4 oculusNative_skyColor;\n};\n"));
    }

    @Test void combinedFixtureRequiresAndBindsMatricesMaterialsAndExtendedAttributes() throws Exception {
        var root = java.nio.file.Path.of(getClass().getClassLoader().getResource("matrix-materials/shaders").toURI());
        for (String stage : List.of("gbuffers_terrain", "shadow")) {
            String vertex = java.nio.file.Files.readString(root.resolve(stage + ".vsh"));
            String fragment = java.nio.file.Files.readString(root.resolve(stage + ".fsh"));
            var program = NativeTerrainProgram.adaptWithMesh(vertex, fragment, List.of(0), stage.equals("shadow"), true);
            assertTrue(program.usesWorldMatrices());
            assertTrue(program.usesMaterialIds());
            assertTrue(program.vertexSource().contains("in int OculusState;"));
            assertThrows(ShaderCompatibilityException.class, () -> NativeTerrainProgram.adaptWithMesh(
                vertex, fragment, List.of(0), stage.equals("shadow"), false));
        }
    }

    @Test void actualFixturePreparesNativeTerrainAndShadowWithMatrices() throws Exception {
        var resource = getClass().getClassLoader().getResource("matrix-terrain");
        assertNotNull(resource);
        var prepared = new PackProgramCompiler(dev.oculusforge.shaderpack.ShaderPackLimits.DEFAULT).prepare(
            new dev.oculusforge.shaderpack.ShaderPackDescriptor("matrix", "Matrix probe", java.nio.file.Path.of(resource.toURI()),
                dev.oculusforge.shaderpack.ShaderPackDescriptor.Kind.DIRECTORY));
        var world = prepared.world().variant("shaders");
        assertNotNull(world.nativeTerrain(), world.nativeTerrainIssue());
        assertNotNull(world.nativeShadow(), world.nativeShadowIssue());
        assertTrue(world.nativeTerrain().usesWorldMatrices());
        assertTrue(world.nativeShadow().usesWorldMatrices());
    }

    @Test void vertexAndFragmentShareTheSameCompleteLayout() throws Exception {
        var program = NativeTerrainProgram.adapt("uniform mat4 gbufferProjection;\nuniform vec3 cameraPosition;\n" +
            V.replace("ftransform()", "gbufferProjection * vec4(cameraPosition, 1)"),
            "uniform mat4 gbufferProjectionInverse;\n" + F.replace("vec4(1)", "gbufferProjectionInverse[0]"), List.of(0));
        assertTrue(program.usesWorldMatrices());
        assertFalse(program.needsShadowMatrices());
        assertTrue(program.vertexSource().contains(NativeWorldBindings.block()));
        assertTrue(program.fragmentSource().contains(NativeWorldBindings.block()));
        assertTrue(program.vertexSource().contains("vec4(oculusNative_cameraPosition.xyz, 1)"));
        assertTrue(program.fragmentSource().contains("oculusNativeFragmentProjectionInverse(oculusNative_gbufferProjectionInverse)[0]"));
    }

    @Test void fragmentOnlyBindingStillHasMatchingVertexBlock() throws Exception {
        var program = NativeTerrainProgram.adapt(V, "uniform mat4 shadowModelView;\n" +
            F.replace("vec4(1)", "shadowModelView[0]"), List.of(0));
        assertTrue(program.needsShadowMatrices());
        assertTrue(program.vertexSource().contains(NativeWorldBindings.block()));
    }

    @Test void matricesDoNotRequireTheExperimentalMesh() throws Exception {
        var p = NativeTerrainProgram.adaptWithMesh("uniform mat4 gbufferModelView;\n" +
            V.replace("ftransform()", "gl_ProjectionMatrix * gbufferModelView * gl_Vertex"), F, List.of(0), false, false);
        assertTrue(p.usesWorldMatrices());
        assertFalse(p.vertexSource().contains("in int OculusState;"));
    }

    @Test void unknownMistypedDuplicateOrReservedDeclarationsAreRejected() {
        assertThrows(ShaderCompatibilityException.class, () -> NativeTerrainProgram.adapt("uniform float timeAngle;\n"
            + V.replace("ftransform()", "ftransform()*timeAngle"),F,List.of(0)));
        for (String declaration : List.of("uniform vec3 gbufferProjection;",
            "uniform mat4 gbufferProjection; uniform mat4 gbufferProjection;", "uniform mat4 gbufferProjection[2];",
            "uniform vec3 cameraPosition; uniform mat4 cameraPosition;", "float OculusWorldMatrices;")) {
            assertThrows(ShaderCompatibilityException.class, () -> NativeTerrainProgram.adapt(declaration + "\n" + V, F, List.of(0)));
        }
    }

    @Test void crossStageWrongTypesFailAndUnrelatedNamesAreNotRewritten() throws Exception {
        assertThrows(ShaderCompatibilityException.class, () -> NativeTerrainProgram.adapt(
            "uniform mat4 gbufferProjection;\n" + V, "uniform vec3 gbufferProjection;\n" + F, List.of(0)));
        var binding = NativeWorldBindings.adapt("uniform mat4 gbufferProjection;\nfloat gbufferProjectionScale;\n");
        assertTrue(binding.source().contains("float gbufferProjectionScale;"));
        assertFalse(NativeTerrainProgram.adapt(V, F, List.of(0)).usesWorldMatrices());
    }
}
