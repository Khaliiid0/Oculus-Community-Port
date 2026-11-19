package dev.oculusforge.render.program;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

final class PostTemporalHistoryTest {
    @Test void temporalProbePreparesAsRootPostOnlyPack() throws Exception {
        var root = java.nio.file.Path.of(getClass().getClassLoader().getResource("temporal-reset").toURI());
        var prepared = new PackProgramCompiler(dev.oculusforge.shaderpack.ShaderPackLimits.DEFAULT).prepare(
            new dev.oculusforge.shaderpack.ShaderPackDescriptor("temporal-reset", "Temporal reset", root,
                dev.oculusforge.shaderpack.ShaderPackDescriptor.Kind.DIRECTORY));
        assertEquals(java.util.Set.of("shaders"), prepared.variants().keySet());
        assertEquals(1, prepared.programs().size());
        var semantics = prepared.programs().getFirst().fragment().uniforms().stream()
            .map(LegacyFinalShaderAdapter.UniformBinding::semantic).toList();
        assertTrue(semantics.containsAll(List.of(LegacyFinalShaderAdapter.UniformSemantic.WETNESS,
            LegacyFinalShaderAdapter.UniformSemantic.RAIN_STRENGTH)));
        for (var variant : prepared.world().variants().values()) {
            assertNull(variant.nativeTerrain());
            assertNull(variant.nativeShadow());
        }
    }

    private PackFinalProgram program() {
        return new PackFinalProgram("test", "final", null, List.of(), List.of(), null);
    }

    private void set(PackFinalProgram program, String name, Object value) throws Exception {
        var field = PackFinalProgram.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(program, value);
    }

    private Object get(PackFinalProgram program, String name) throws Exception {
        var field = PackFinalProgram.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(program);
    }

    private void seed(PackFinalProgram program) throws Exception {
        for (String name : List.of("lastFrameTime", "smoothedFrameTime", "wetness"))
            set(program, name, .75f);
        for (String name : List.of("previousCameraX", "previousCameraY", "previousCameraZ"))
            set(program, name, 123d);
        set(program, "hasPreviousCamera", true);
        set(program, "previousProjection", new Matrix4f().scale(2));
        set(program, "previousModelView", new Matrix4f().translate(1, 2, 3));
    }

    private void assertReset(PackFinalProgram program) throws Exception {
        for (String name : List.of("lastFrameTime", "smoothedFrameTime", "wetness"))
            assertEquals(0f, get(program, name), name);
        for (String name : List.of("previousCameraX", "previousCameraY", "previousCameraZ"))
            assertEquals(0d, get(program, name), name);
        assertEquals(false, get(program, "hasPreviousCamera"));
        assertNull(get(program, "previousProjection"));
        assertNull(get(program, "previousModelView"));
    }

    @Test void sameWorldKeepsHistory() throws Exception {
        try (var program = program()) {
            var world = new Object();
            program.prepareWorld(world);
            seed(program);
            var projection = get(program, "previousProjection");
            program.prepareWorld(world);
            assertEquals(.75f, get(program, "wetness"));
            assertEquals(123d, get(program, "previousCameraX"));
            assertSame(projection, get(program, "previousProjection"));
        }
    }

    @Test void newWorldIdentityResetsEvenWithEqualKeys() throws Exception {
        try (var program = program()) {
            var first = new String("same dimension key");
            var second = new String("same dimension key");
            program.prepareWorld(first);
            seed(program);
            program.prepareWorld(second);
            assertReset(program);
            assertSame(second, get(program, "temporalWorld"));
        }
    }

    @Test void logoutResetsEveryVariantIncludingPostOnly() throws Exception {
        var first = program();
        var second = program();
        try (var pipeline = new PackShaderPipeline("test", Map.of(
            "shaders", new PackShaderPipeline.Variant(List.of(first), null),
            "shaders/world-1", new PackShaderPipeline.Variant(List.of(second), null)))) {
            for (var program : List.of(first, second)) {
                program.prepareWorld(new Object());
                seed(program);
            }
            pipeline.resetTemporalHistory();
            pipeline.resetTemporalHistory();
            for (var program : List.of(first, second)) {
                assertReset(program);
                assertNull(get(program, "temporalWorld"));
            }
        }
    }

    @Test void missingWorldDoesNotMutateHistory() throws Exception {
        try (var program = program()) {
            var world = new Object();
            program.prepareWorld(world);
            seed(program);
            assertThrows(NullPointerException.class, () -> program.prepareWorld(null));
            assertSame(world, get(program, "temporalWorld"));
            assertEquals(.75f, get(program, "wetness"));
        }
    }

    @Test void closeReleasesWorldAndHistory() throws Exception {
        var program = program();
        program.prepareWorld(new Object());
        seed(program);
        program.close();
        program.close();
        assertReset(program);
        assertNull(get(program, "temporalWorld"));
    }
}
