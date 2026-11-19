package dev.oculusforge.render.program;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

final class NativeCustomUniformsTest {
    @Test void gpuProbePreparesCustomExpressionsInAllFourNativeStages() throws Exception {
        var resource = getClass().getClassLoader().getResource("custom-clock");
        assertNotNull(resource);
        var prepared = new PackProgramCompiler(dev.oculusforge.shaderpack.ShaderPackLimits.DEFAULT).prepare(
            new dev.oculusforge.shaderpack.ShaderPackDescriptor("custom", "Custom clock probe",
                java.nio.file.Path.of(resource.toURI()), dev.oculusforge.shaderpack.ShaderPackDescriptor.Kind.DIRECTORY));
        var world = prepared.world().variant("shaders");
        assertNotNull(world.nativeTerrain(), world.nativeTerrainIssue());
        assertNotNull(world.nativeShadow(), world.nativeShadowIssue());
        for (var program : List.of(world.nativeTerrain(), world.nativeShadow()))
            for (String source : List.of(program.vertexSource(), program.fragmentSource())) {
                assertTrue(source.contains("oculusNative_oculusCustomValue0"));
                assertTrue(source.contains("oculusNative_oculusCustomValue1"));
                assertTrue(source.contains("oculusNative_oculusCustomValue2"));
            }
    }
    private static final String V = "uniform float phase;\nvoid main() { gl_Position = ftransform() + vec4(phase); }";
    private static final String F = "uniform float phase;\nvoid main() { gl_FragColor = vec4(phase); }";

    @Test void fullPackPreparationReloadsCpuExpressionsWithoutChangingSlotShaders(
        @org.junit.jupiter.api.io.TempDir java.nio.file.Path root) throws Exception {
        var shaders = java.nio.file.Files.createDirectories(root.resolve("shaders"));
        java.nio.file.Files.writeString(shaders.resolve("gbuffers_terrain.vsh"), V);
        java.nio.file.Files.writeString(shaders.resolve("gbuffers_terrain.fsh"), F);
        java.nio.file.Files.writeString(shaders.resolve("shadow.vsh"), V);
        java.nio.file.Files.writeString(shaders.resolve("shadow.fsh"), F);
        java.nio.file.Files.writeString(shaders.resolve("final.fsh"),
            "#version 120\nvoid main() { gl_FragColor = vec4(1); }");
        var compiler = new PackProgramCompiler(dev.oculusforge.shaderpack.ShaderPackLimits.DEFAULT);
        var descriptor = new dev.oculusforge.shaderpack.ShaderPackDescriptor("custom", "Custom probe", root,
            dev.oculusforge.shaderpack.ShaderPackDescriptor.Kind.DIRECTORY);
        String prior = null;
        for (int divisor : new int[] {7, 8}) {
            java.nio.file.Files.writeString(shaders.resolve("shaders.properties"),
                "uniform.float.phase=frameCounter % " + divisor);
            var prepared=compiler.prepare(descriptor);
            var world = prepared.world().variant("shaders");
            assertNotNull(world.nativeTerrain(), world.nativeTerrainIssue());
            assertNotNull(world.nativeShadow(), world.nativeShadowIssue());
            assertTrue(world.nativeTerrain().vertexSource().contains("oculusNative_oculusCustomValue0"));
            assertEquals(15%divisor,prepared.variants().get("shaders").customUniforms().newState()
                .update(new Object(),new AnimationClock.Snapshot(15,0,0),0,0,0,0,0).boundSlot(0));
            if(prior!=null) assertEquals(prior, world.nativeTerrain().fingerprint());
            prior = world.nativeTerrain().fingerprint();
        }
        java.nio.file.Files.writeString(shaders.resolve("shaders.properties"), "uniform.float.phase=cos(worldTime)");
        var error=assertThrows(ShaderCompatibilityException.class,()->compiler.prepare(descriptor));
        assertTrue(error.getMessage().contains("phase"));
    }

    @Test void packNamesAreNotHardCodedAndBothStagesUseExistingClockBindings() throws Exception {
        for (boolean shadow : new boolean[] {false, true}) for (boolean extended : new boolean[] {false, true}) {
            var result = NativeTerrainProgram.adaptWithCustomUniforms(V, F, List.of(0), shadow, extended,
                NativeCustomUniforms.parse("uniform.float.phase = frameCounter % 7"));
            assertTrue(result.usesWorldMatrices());
            for (String source : List.of(result.vertexSource(), result.fragmentSource()))
                assertTrue(source.contains("float(oculusNative_frameCounter % 7)"));
        }
    }

    @Test void divisionUsesFloatingPointAndKeepsNearbyNames() throws Exception {
        var custom = NativeCustomUniforms.parse("uniform.float.phase=worldTime / 24000");
        String result = custom.adapt("uniform float phase;\nfloat phaseScale = phase;");
        assertTrue(result.contains("float phaseScale = ((float(worldTime) / 24000.0));"));
        assertTrue(result.startsWith("uniform int worldTime;"));
    }

    @Test void existingDependencyDeclarationRemainsValidated() throws Exception {
        var custom = NativeCustomUniforms.parse("uniform.float.phase=frameCounter % 8");
        var result = NativeTerrainProgram.adaptWithCustomUniforms("uniform int frameCounter;\n" + V,
            F, List.of(0), false, false, custom);
        assertTrue(result.usesWorldMatrices());
        assertThrows(ShaderCompatibilityException.class, () -> NativeTerrainProgram.adaptWithCustomUniforms(
            "uniform float frameCounter;\n" + V, F, List.of(0), false, false, custom));
    }

    @Test void missingAndUnsupportedDefinitionsDoNotInventUniforms() throws Exception {
        for (String expression : List.of("", "uniform.float.phase=worldTime / 0",
            "uniform.float.phase=frameCounter % -2", "uniform.float.phase=frameCounter % 1000001",
            "uniform.float.phase=frameCounter % 8; discard", "uniform.float.phase=sin(frameTimeCounter)",
            "uniform.float.phase=other % 2", "uniform.int.phase=frameCounter % 2",
            "uniform.float.phase=frameCounter % 8 + 1")) {
            assertEquals(V, NativeCustomUniforms.parse(expression).adapt(V));
            assertThrows(ShaderCompatibilityException.class, () -> NativeTerrainProgram.adaptWithCustomUniforms(
                V, F, List.of(0), false, false, NativeCustomUniforms.parse(expression)));
        }
    }

    @Test void conditionalDuplicateEscapedAndVariableOverridesStayUnsupported() throws Exception {
        String definition = "uniform.float.phase=frameCounter % 8\n";
        for (String properties : List.of("#if ENABLE\n" + definition + "#endif",
            definition + "#if ENABLE\nuniform.float.phase=frameCounter % 2\n#endif",
            definition + definition, definition + "uniform.int.phase=frameCounter % 2",
            definition + "uniform.float.ph" + "\\u0061se=frameCounter % 2",
            definition + "uniform.float.phase: frameCounter % 2",
            definition + "variable.float.phase=1", "#endif\n" + definition,
            "#if ENABLE\n" + definition, "other=value\\\n" + definition))
            assertEquals(V, NativeCustomUniforms.parse(properties).adapt(V), properties);
    }

    @Test void unrelatedConditionalPropertiesDoNotDisableUnconditionalDefinitions() throws Exception {
        String properties = "#if ENABLE\nuniform.float.other=frameCounter % 3\n#endif\n"
            + "uniform.float.phase=frameCounter % 8";
        assertTrue(NativeCustomUniforms.parse(properties).adapt(V).contains("float(frameCounter % 8)"));
    }

    @Test void wrongDuplicateAndArrayShaderDeclarationsStillFail() {
        var custom = NativeCustomUniforms.parse("uniform.float.phase=frameCounter % 8");
        for (String vertex : List.of(V.replace("uniform float", "uniform int"),
            "uniform float phase;\n" + V, V.replace("phase;", "phase[2];")))
            assertThrows(ShaderCompatibilityException.class, () -> NativeTerrainProgram.adaptWithCustomUniforms(
                vertex, F, List.of(0), false, false, custom));
    }

    @Test void cannotOverrideBuiltInClockNames() throws Exception {
        String shader = "uniform int frameCounter;\nint value=frameCounter;";
        assertEquals(shader, NativeCustomUniforms.parse("uniform.float.frameCounter=worldTime / 2").adapt(shader));
    }
}
