package dev.oculusforge.render.program;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

final class NativeCameraExpressionsTest {
    private static final String V = "void main(){gl_Position=ftransform();}";
    private static final String F = "void main(){gl_FragColor=vec4(1);}";

    @Test void diagnosticMarkerSeparatesOpaqueColorsAndRetainsMismatchMarkers() throws Exception {
        var root = java.nio.file.Path.of(getClass().getClassLoader().getResource("custom-camera-inputs/shaders").toURI());
        for (String stage : List.of("gbuffers_terrain.fsh", "shadow.fsh"))
            assertTrue(java.nio.file.Files.readString(root.resolve(stage)).contains("vec4(values,bad?.75:.25,.375)"));
        String finalSource = java.nio.file.Files.readString(root.resolve("final.fsh"));
        assertTrue(finalSource.contains("abs(marker.a-.375)<.01"));
        assertTrue(finalSource.contains("abs(marker.b-.25)<.01||abs(marker.b-.75)<.01"));
        assertTrue(finalSource.contains("marker.b>.5||"));
        assertTrue(finalSource.contains("const float expectedOffset=0.0;"));
        // Quantized good AND bad stage markers stay recognizable. An opaque entity
        // cannot match solely because its blue happens to be .25 or .75.
        float alpha = Math.round(.375f * 255) / 255f;
        assertTrue(Math.abs(alpha - .375f) < .01f);
        assertFalse(Math.abs(1f - .375f) < .01f);
        for (float blue : new float[] {.25f, .75f}) {
            float quantized = Math.round(blue * 255) / 255f;
            assertTrue(Math.abs(quantized - blue) < .01f);
            assertEquals(blue == .75f, quantized > .5f);
        }
    }

    @Test void diagnosticPackPreparesBothNativePrograms() throws Exception {
        var root = java.nio.file.Path.of(getClass().getClassLoader().getResource("custom-camera-inputs").toURI());
        var prepared = new PackProgramCompiler(dev.oculusforge.shaderpack.ShaderPackLimits.DEFAULT).prepare(
            new dev.oculusforge.shaderpack.ShaderPackDescriptor("camera", "Camera", root,
                dev.oculusforge.shaderpack.ShaderPackDescriptor.Kind.DIRECTORY));
        var world = prepared.world().variant("shaders");
        assertNotNull(world.nativeTerrain(), world.nativeTerrainIssue());
        assertNotNull(world.nativeShadow(), world.nativeShadowIssue());
        for (var program : List.of(world.nativeTerrain(), world.nativeShadow())) {
            assertTrue(program.usesWorldMatrices());
            assertTrue(program.fragmentSource().contains("oculusNative_cameraPosition.y"));
        }
    }

    @Test void altitudeUsesExistingCameraSnapshotInBothStagesAndPasses() throws Exception {
        for (boolean shadow : new boolean[] {false, true}) {
            var program = NativeTerrainProgram.adaptWithMesh(
                "uniform float eyeAltitude;" + V.replace("ftransform()", "ftransform()+vec4(eyeAltitude)"),
                "uniform float eyeAltitude;" + F.replace("vec4(1)", "vec4(eyeAltitude)"),
                List.of(0), shadow, false);
            assertTrue(program.usesWorldMatrices());
            for (String source : List.of(program.vertexSource(), program.fragmentSource())) {
                assertTrue(source.contains("vec4(oculusNative_cameraPosition.y)"));
                assertFalse(source.contains("uniform float eyeAltitude"));
                assertFalse(source.contains("oculusNative_eyeAltitude"));
            }
        }
        assertEquals(976, NativeWorldBindings.BYTE_SIZE);
    }

    @Test void altitudeValidationAndSimilarNamesRemainStrict() throws Exception {
        for (String declarations : List.of("uniform int eyeAltitude;", "uniform vec3 eyeAltitude;",
            "uniform float eyeAltitude; uniform float eyeAltitude;", "uniform float eyeAltitude[2];")) {
            assertThrows(ShaderCompatibilityException.class,
                () -> NativeTerrainProgram.adapt(declarations + V, F, List.of(0)));
            assertThrows(ShaderCompatibilityException.class,
                () -> NativeTerrainProgram.adapt(V, declarations + F, List.of(0)));
        }
        var adapted = NativeWorldBindings.adapt("uniform float eyeAltitude; float eyeAltitudeScale=eyeAltitude;");
        assertTrue(adapted.source().contains("float eyeAltitudeScale=oculusNative_cameraPosition.y;"));
    }

    @Test void cameraComponentsAndAltitudeInjectCorrectTypesAndResolveDependencies() throws Exception {
        var custom = NativeCustomUniforms.parse("""
            variable.float.height=cameraPosition.y / 256
            uniform.float.probe=height + cameraPosition.x + cameraPosition.z + eyeAltitude
            """);
        String adapted = custom.adapt("uniform float probe;" + F.replace("vec4(1)", "vec4(probe)"));
        assertTrue(adapted.contains("uniform vec3 cameraPosition;"));
        assertTrue(adapted.contains("uniform float eyeAltitude;"));
        assertFalse(adapted.contains("uniform int cameraPosition"));
        for (boolean shadow : new boolean[] {false, true}) {
            var program = NativeTerrainProgram.adaptWithCustomUniforms(
                "uniform float probe;" + V.replace("ftransform()", "ftransform()+vec4(probe)"),
                "uniform float probe;" + F.replace("vec4(1)", "vec4(probe)"), List.of(0), shadow, false, custom);
            for (String source : List.of(program.vertexSource(), program.fragmentSource())) {
                assertTrue(source.contains("oculusNative_cameraPosition.xyz.y"));
                assertTrue(source.contains("oculusNative_cameraPosition.xyz.x"));
                assertTrue(source.contains("oculusNative_cameraPosition.xyz.z"));
                assertTrue(source.contains("oculusNative_cameraPosition.y"));
            }
        }
    }

    @Test void unsupportedMemberAccessAndBuiltinOverridesStayUnadapted() throws Exception {
        for (String expression : List.of("cameraPosition", "cameraPosition.w", "cameraPosition.xy",
            "cameraPosition.y.z", "cameraPosition[1]", "other.y", "worldTime.x", "cameraPosition.y()",
            "cameraPosition.y;discard", "cameraPosition.y2")) {
            var custom = NativeCustomUniforms.parse("uniform.float.probe=" + expression);
            assertThrows(ShaderCompatibilityException.class, () -> NativeTerrainProgram.adaptWithCustomUniforms(
                V, "uniform float probe;" + F.replace("vec4(1)","vec4(probe)"), List.of(0), false, false, custom), expression);
        }
        for (String override : List.of("uniform.float.cameraPosition=1", "variable.float.cameraPosition=1",
            "uniform.float.eyeAltitude=1")) {
            String input = override.contains("eyeAltitude") ? "eyeAltitude" : "cameraPosition.y";
            var custom = NativeCustomUniforms.parse(override + "\nuniform.float.probe=" + input);
            assertTrue(custom.adapt("uniform float probe;" + F).contains("uniform float probe;"));
        }
    }

    @Test void wrongExistingDependencyTypeIsNotSilentlyReplaced() {
        var custom = NativeCustomUniforms.parse("uniform.float.probe=cameraPosition.y+eyeAltitude");
        for (String declaration : List.of("uniform float cameraPosition;", "uniform int eyeAltitude;"))
            assertThrows(ShaderCompatibilityException.class, () -> NativeTerrainProgram.adaptWithCustomUniforms(
                V, declaration + "uniform float probe;" + F, List.of(0), false, false, custom));
    }
}
