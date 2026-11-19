package dev.oculusforge.render.program;

import static org.junit.jupiter.api.Assertions.*;

import dev.oculusforge.config.OculusConfig;
import dev.oculusforge.shaderpack.ShaderPackDescriptor;
import dev.oculusforge.shaderpack.ShaderPackLimits;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class VisualFixtureTest {
    @TempDir Path temporaryDirectory;

    private Path fixture(String name) throws Exception {
        var resource = getClass().getClassLoader().getResource(name);
        assertNotNull(resource, "The real test-pack resources must be on the test classpath");
        return Path.of(resource.toURI());
    }

    private PreparedPackPipeline prepare(Path path) throws Exception {
        return new PackProgramCompiler(ShaderPackLimits.DEFAULT).prepare(
            new ShaderPackDescriptor("fixture", "Visual fixture", path, ShaderPackDescriptor.Kind.DIRECTORY));
    }

    @Test void intrusiveDiagnosticIsDisabledByDefault() throws Exception {
        var prepared = prepare(fixture("buffer-routing"));
        var finalStage = prepared.variants().get("shaders").programs().getLast();
        assertFalse(finalStage.fragment().source().contains("texcoord.x < 0.15"));
        assertTrue(finalStage.fragment().source().contains("texture(colortex0, texcoord)"));
    }

    @Test void diagnosticStripeRequiresAnExplicitOptIn() throws Exception {
        Path original = fixture("buffer-routing").resolve("shaders");
        Path shaders = Files.createDirectories(temporaryDirectory.resolve("shaders"));
        for (String file : List.of("deferred.fsh", "composite.fsh", "final.fsh", "debug.glsl")) {
            Files.copy(original.resolve(file), shaders.resolve(file));
        }
        Path debug = shaders.resolve("debug.glsl");
        Files.writeString(debug, Files.readString(debug).replace("OCULUS_ROUTING_OVERLAY 0", "OCULUS_ROUTING_OVERLAY 1"));
        var finalStage = prepare(temporaryDirectory).variants().get("shaders").programs().getLast();
        assertTrue(finalStage.fragment().source().contains("texcoord.x < 0.15"));
    }

    @Test void normalViewStillExercisesThreeStagesAndIndependentColorBuffers() throws Exception {
        var variant = prepare(fixture("buffer-roundtrip")).variants().get("shaders");
        assertEquals(List.of("deferred", "composite", "final"),
            variant.programs().stream().map(PreparedFinalProgram::stageName).toList());
        var plan = variant.buffers();
        assertEquals(2, plan.scratchCount(), "The auxiliary buffer can be reused after its final read");
        var combine = plan.steps().get(1);
        assertEquals(ColorBufferPlan.MAIN, combine.colorInputs().get("colortex0"));
        assertEquals(plan.steps().getFirst().outputSlot(), combine.colorInputs().get("composite"));
        assertNotEquals(combine.colorInputs().get("colortex0"), combine.colorInputs().get("composite"));
        assertTrue(variant.programs().getFirst().fragment().source().contains("scene.brg"));
        assertTrue(variant.programs().get(1).fragment().source().contains("encoded.gbr"));
        assertFalse(variant.programs().stream().anyMatch(program -> program.fragment().source().contains("indicator")));
    }

    @Test void noiseFixtureExercisesTheActualSamplerWithoutAnOverlay() throws Exception {
        var prepared = prepare(fixture("noise-preview"));
        assertEquals(128, prepared.noise().resolution());
        var variant = prepared.variants().get("shaders");
        assertEquals(3, variant.programs().size());
        assertEquals(2, variant.buffers().scratchCount());
        assertTrue(variant.programs().getLast().fragment().samplers().stream().anyMatch(
            binding -> binding.texture() == LegacyFinalShaderAdapter.TextureSemantic.NOISE));
        assertFalse(variant.programs().getLast().fragment().source().contains("texcoord.x <"));
    }

    @Test void customPngFixturePreservesTheSceneAndSharesOneWhiteImage() throws Exception {
        var prepared = prepare(fixture("custom-texture-roundtrip"));
        assertNull(prepared.noise());
        assertEquals(1, prepared.images().size());
        var image = prepared.images().values().iterator().next();
        assertEquals(4, image.byteSize());
        assertEquals(0xFFFFFFFF, image.pixelAbgr(0));
        assertEquals(2, prepared.variants().get("shaders").buffers().scratchCount());
    }

    @Test void negativeGpuFixtureReachesCompilationAfterAllocatingAUniformProgram() throws Exception {
        var programs = prepare(fixture("compile-error-probe")).variants().get("shaders").programs();
        assertEquals(3, programs.size());
        assertFalse(programs.getFirst().fragment().uniforms().isEmpty());
        assertTrue(programs.getLast().fragment().source().contains("oculusIntentionalUndefinedSymbol"));
    }

    @Test void freshModConfigurationNeverSelectsADiagnosticPack() {
        assertFalse(OculusConfig.DEFAULT.shadersEnabled());
        assertEquals("", OculusConfig.DEFAULT.selectedPack());
    }
}
