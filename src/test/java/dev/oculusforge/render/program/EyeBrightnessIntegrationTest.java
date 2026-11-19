package dev.oculusforge.render.program;

import static org.junit.jupiter.api.Assertions.*;
import dev.oculusforge.render.world.EyeBrightness;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class EyeBrightnessIntegrationTest {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory;

    @Test void postOnlyGpuProbeHasThreeStagesAndNoNativeWorldPlan() throws Exception {
        var root = java.nio.file.Path.of(getClass().getClassLoader().getResource("eye-brightness-post-only").toURI());
        var prepared = new PackProgramCompiler(dev.oculusforge.shaderpack.ShaderPackLimits.DEFAULT).prepare(
            new dev.oculusforge.shaderpack.ShaderPackDescriptor("post-eye", "Post eye", root,
                dev.oculusforge.shaderpack.ShaderPackDescriptor.Kind.DIRECTORY));
        assertTrue(prepared.world().variants().isEmpty());
        assertEquals(List.of("composite", "composite1", "final"),
            prepared.programs().stream().map(PreparedFinalProgram::stageName).toList());
        assertEquals(400, prepared.variants().get("shaders").eyeBrightnessHalfLife());
        for (var program : prepared.programs())
            assertTrue(program.fragment().uniforms().stream()
                .anyMatch(u -> u.semantic() == LegacyFinalShaderAdapter.UniformSemantic.EYE_BRIGHTNESS_SMOOTH));
    }

    @Test void gpuProbePreparesNativeAndPostSmoothBindings() throws Exception {
        var root = java.nio.file.Path.of(getClass().getClassLoader().getResource("eye-brightness-smooth").toURI());
        var prepared = new PackProgramCompiler(dev.oculusforge.shaderpack.ShaderPackLimits.DEFAULT).prepare(
            new dev.oculusforge.shaderpack.ShaderPackDescriptor("smooth-probe", "Smooth probe", root,
                dev.oculusforge.shaderpack.ShaderPackDescriptor.Kind.DIRECTORY));
        var world = prepared.world().variant("shaders");
        assertNotNull(world.nativeTerrain(), world.nativeTerrainIssue());
        assertNotNull(world.nativeShadow(), world.nativeShadowIssue());
        assertEquals(400, prepared.variants().get("shaders").eyeBrightnessHalfLife());
        assertTrue(prepared.programs().getFirst().fragment().uniforms().stream()
            .anyMatch(u -> u.semantic() == LegacyFinalShaderAdapter.UniformSemantic.EYE_BRIGHTNESS_SMOOTH));
    }

    private PreparedPackPipeline prepare() throws Exception {
        return new PackProgramCompiler(dev.oculusforge.shaderpack.ShaderPackLimits.DEFAULT).prepare(
            new dev.oculusforge.shaderpack.ShaderPackDescriptor("smooth-test", "Smooth test", directory,
                dev.oculusforge.shaderpack.ShaderPackDescriptor.Kind.DIRECTORY));
    }

    private void shader(String name, String source) throws Exception {
        var file = directory.resolve("shaders/" + name);
        java.nio.file.Files.createDirectories(file.getParent());
        java.nio.file.Files.writeString(file, source);
    }

    @Test void preparationReadsNativeSettingsAndRejectsCrossStageConflict() throws Exception {
        shader("gbuffers_terrain.vsh", "const float eyeBrightnessHalflife=25.0;\nvoid main(){gl_Position=ftransform();}");
        shader("gbuffers_terrain.fsh", "void main(){gl_FragColor=vec4(1);}");
        shader("shadow.vsh", "const float eyeBrightnessHalflife=25.0;\nvoid main(){gl_Position=ftransform();}");
        shader("shadow.fsh", "void main(){gl_FragColor=vec4(1);}");
        shader("final.fsh", "uniform ivec2 eyeBrightnessSmooth;\nvoid main(){gl_FragColor=vec4(vec2(eyeBrightnessSmooth)/240.0,0,1);}");
        var prepared = prepare();
        assertNotNull(prepared.world().variant("shaders").nativeTerrain());
        assertNotNull(prepared.world().variant("shaders").nativeShadow());
        assertEquals(25, prepared.variants().get("shaders").eyeBrightnessHalfLife());
        shader("final.fsh", "const float eyeBrightnessHalflife=10.0;\nvoid main(){gl_FragColor=vec4(1);}");
        assertThrows(ShaderCompatibilityException.class, this::prepare);
    }

    @Test void postOnlySettingsRespectPreprocessingIncludesAndDimensionIsolation() throws Exception {
        shader("settings.glsl", "#define HALF_LIFE 5.0\nconst float eyeBrightnessHalflife=HALF_LIFE;\n");
        shader("final.fsh", "#include \"/settings.glsl\"\n#if 0\nconst float eyeBrightnessHalflife=99.0;\n#endif\nvoid main(){gl_FragColor=vec4(1);}");
        shader("world-1/final.fsh", "const float eyeBrightnessHalflife=0.0;\nvoid main(){gl_FragColor=vec4(1);}");
        var prepared = prepare();
        assertEquals(5, prepared.variants().get("shaders").eyeBrightnessHalfLife());
        assertEquals(0, prepared.variants().get("shaders/world-1").eyeBrightnessHalfLife());
    }

    @Test void settingsDefaultLiteralsDuplicatesAndComments() throws Exception {
        var settings = new EyeBrightnessSettings();
        assertEquals(10, settings.ticks());
        settings.read("// const float eyeBrightnessHalflife = 99;\n/* const float eyeBrightnessHalflife = 30; */");
        assertEquals(10, settings.ticks());
        settings.read("const float eyeBrightnessHalflife = 2.5e1;\nconst float eyeBrightnessHalflife = 25.0f;");
        assertEquals(25, settings.ticks());
        assertThrows(ShaderCompatibilityException.class,
            () -> settings.read("const float eyeBrightnessHalflife = 10.0;"));
    }

    @Test void invalidSettingsAreNotSilentlyApproximated() {
        for (String value : List.of("-1.0", "1e999", "NaN", "10.0 * 2.0"))
            assertThrows(ShaderCompatibilityException.class,
                () -> new EyeBrightnessSettings().read("const float eyeBrightnessHalflife = " + value + ";"));
    }

    @Test void nativeSmoothSupportsVertexAndFragmentInTerrainAndShadow() throws Exception {
        for (boolean shadow : new boolean[] {false, true}) {
            var program = NativeTerrainProgram.adaptWithMesh(
                "const float eyeBrightnessHalflife = 25.0;\nuniform ivec2 eyeBrightnessSmooth;\nvoid main(){gl_Position=ftransform()+vec4(vec2(eyeBrightnessSmooth),0,0);}",
                "uniform ivec2 eyeBrightnessSmooth;\nvoid main(){gl_FragColor=vec4(vec2(eyeBrightnessSmooth),0,1);}",
                List.of(0), shadow, false);
            assertTrue(program.usesWorldMatrices());
            for (String source : List.of(program.vertexSource(), program.fragmentSource()))
                assertTrue(source.contains("vec2(oculusNative_eyeBrightnessSmooth)"));
            var settings = new EyeBrightnessSettings();
            settings.read(program.vertexSource());
            assertEquals(25, settings.ticks());
        }
    }

    @Test void packOwnsSharedStateAndResetReleasesItsHistory() {
        Object world = new Object();
        var variant = new PackShaderPipeline.Variant(List.of(), null);
        try (var pack = new PackShaderPipeline("test", Map.of("shaders", variant))) {
            var terrain = pack.variant("shaders/world-1").eyeBrightness();
            var post = pack.variant("shaders").eyeBrightness();
            assertSame(terrain, post);
            terrain.update(world, new Object(), EyeBrightness.DARK, 0);
            Object frame = new Object();
            var value = terrain.update(world, frame, new EyeBrightness(15, 15), .5);
            assertEquals(new EyeBrightnessSmoother.Value(120, 120), value);
            assertSame(value, post.update(world, frame, new EyeBrightness(15, 15), .5));
            pack.resetTemporalHistory();
            assertEquals(new EyeBrightnessSmoother.Value(240, 240), post.update(world, frame, new EyeBrightness(15, 15), .5));
        }
        assertEquals(new EyeBrightnessSmoother.Value(0, 0), variant.eyeBrightness().update(world, new Object(), EyeBrightness.DARK, 0));
    }

    @Test void multiplePostProgramsSerializeTheNativeFrameWithoutAdvancingOrResettingIt() throws Exception {
        var smoother = new EyeBrightnessSmoother(10);
        Object world = new Object();
        smoother.update(world, AnimationClock.ZERO, EyeBrightness.DARK, 0);
        var frame = new AnimationClock.Snapshot(1, .5f, .5f);
        var environment = new dev.oculusforge.render.world.WorldEnvironment(0, 0, 0, new EyeBrightness(15, 15));
        var nativeValue = smoother.update(world, frame, environment.eyeBrightness(), frame.frameTime());
        var writer = PackFinalProgram.class.getDeclaredMethod("writeUniform",
            com.mojang.blaze3d.buffers.Std140Builder.class, LegacyFinalShaderAdapter.UniformSemantic.class,
            net.minecraft.client.renderer.state.level.LevelRenderState.class, int.class, int.class,
            dev.oculusforge.render.world.WorldEnvironment.class, WorldClockUniforms.class, AnimationClock.Snapshot.class);
        writer.setAccessible(true);
        for (int stage = 0; stage < 3; stage++) {
            try (var program = new PackFinalProgram("test", "composite" + stage, null, List.of(), List.of(), null)) {
                program.prepareEyeBrightness(world, frame, environment, smoother);
                var bytes = java.nio.ByteBuffer.allocateDirect(16).order(java.nio.ByteOrder.nativeOrder());
                writer.invoke(program, com.mojang.blaze3d.buffers.Std140Builder.intoBuffer(bytes),
                    LegacyFinalShaderAdapter.UniformSemantic.EYE_BRIGHTNESS_SMOOTH, null, 854, 480, environment, null, frame);
                assertEquals(120, bytes.getInt(0));
                assertEquals(120, bytes.getInt(4));
                program.resetTemporalHistory();
                assertSame(nativeValue, smoother.update(world, frame, environment.eyeBrightness(), frame.frameTime()));
                assertThrows(java.lang.reflect.InvocationTargetException.class,
                    () -> writer.invoke(program, com.mojang.blaze3d.buffers.Std140Builder.intoBuffer(bytes.clear()),
                        LegacyFinalShaderAdapter.UniformSemantic.EYE_BRIGHTNESS_SMOOTH, null, 854, 480, environment, null, frame));
            }
        }
    }
}
