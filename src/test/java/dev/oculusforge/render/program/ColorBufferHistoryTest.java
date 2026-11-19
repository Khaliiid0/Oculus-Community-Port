package dev.oculusforge.render.program;

import static org.junit.jupiter.api.Assertions.*;
import dev.oculusforge.shaderpack.GlslPreprocessor;
import dev.oculusforge.shaderpack.ShaderPackDescriptor;
import dev.oculusforge.shaderpack.ShaderPackLimits;
import dev.oculusforge.render.world.WorldTargetLayout;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ColorBufferHistoryTest {
    @TempDir Path directory;

    private ColorBufferFormats metadata(String source) throws Exception {
        var builder = new ColorBufferFormats.Builder(ColorBufferFormats.DEFAULT);
        builder.read(GlslPreprocessor.preprocess(source, Map.of()), "history test");
        return builder.build();
    }

    @Test void defaultsMatchAuxiliaryClearSemantics() {
        var formats = ColorBufferFormats.DEFAULT;
        for (int i = 1; i < 16; i++) {
            assertTrue(formats.shouldClear(i));
            assertEquals(i == 1 ? ColorClearValue.WHITE : ColorClearValue.BLACK, formats.clearValue(i));
        }
    }

    @Test void aliasesCommentsAndActiveBranchesControlHistory() throws Exception {
        var formats = metadata("""
            /*
            const bool gaux4Clear = false;
            const vec4 gaux4ClearColor = vec4(0.25, -0.5, 2.0, 0.0);
            */
            // const bool colortex7Clear = true;
            #if 0
            const bool colortex7Clear = true;
            const vec4 colortex7ClearColor = vec4(99.0);
            #endif
            const bool gdepthClear = false;
            const bool colortex1Clear = true;
            """);
        assertFalse(formats.shouldClear(7));
        assertTrue(formats.shouldClear(1));
        assertEquals(new ColorClearValue(.25f, -.5f, 2, 0), formats.clearValue(7));
        var inherited = new ColorBufferFormats.Builder(formats);
        inherited.read("const bool colortex7Clear = true;", "override");
        assertTrue(inherited.build().shouldClear(7));
        assertEquals(formats.clearValue(7), inherited.build().clearValue(7));
    }

    @Test void clearOnlySourcesAreRecognizedAndUnusedExtensionsIgnored() throws Exception {
        assertTrue(ColorBufferFormats.hasDeclarations("const bool colortex7Clear = false;"));
        assertTrue(ColorBufferFormats.hasDeclarations("const vec4 colortex7ClearColor = vec4(1.0);"));
        assertTrue(metadata("const bool colortex16Clear = false;").clearFlags().isEmpty());
    }

    @Test void literalVectorsSupportSignedHdrAndSplatWithoutChangingRgbAlpha() throws Exception {
        var clear = ColorClearValue.parse(" vec4(+.25f, -5e-1, 2., 0) ");
        assertEquals(new ColorClearValue(.25f, -.5f, 2, 0), clear);
        assertEquals(0, clear.vector(ColorBufferFormat.RGBA16F).w());
        assertEquals(1, clear.vector(ColorBufferFormat.RGB16F).w());
        assertEquals(new ColorClearValue(2, 2, 2, 2), ColorClearValue.parse("vec4(2.0)"));
    }

    @Test void malformedAndNonFiniteDirectivesFailExplicitly() {
        for (String value : List.of("vec4()", "vec4(1,2)", "vec4(1,2,3,4,)", "vec4(NaN)",
            "vec4(1e999)", "vec4(1 + 2)", "vec4(vec3(0), 1)", "vec4(0); bad")) {
            assertThrows(ShaderCompatibilityException.class, () -> ColorClearValue.parse(value), value);
        }
        for (String source : List.of("const bool colortex7Clear = 0;", "const vec4 colortex7Clear = vec4(0);",
            "const bool colortex7ClearColor = false;")) {
            assertThrows(ShaderCompatibilityException.class, () -> metadata(source), source);
        }
        assertThrows(IllegalArgumentException.class, () -> new ColorClearValue(Float.NaN, 0, 0, 0));
    }

    @Test void layoutIdentityIncludesClearPolicyAndSeed() {
        var base = new WorldTargetLayout(16, 16, Set.of(7), 0);
        assertNotEquals(base, new WorldTargetLayout(16, 16, Set.of(7), 0,
            new ColorBufferFormats(Map.of(), Map.of(7, false), Map.of())));
        assertNotEquals(base, new WorldTargetLayout(16, 16, Set.of(7), 0,
            new ColorBufferFormats(Map.of(), Map.of(), Map.of(7, ColorClearValue.WHITE))));
    }

    private PreparedFinalProgram program(List<Integer> outputs, ColorBufferFormats formats, int... inputs) {
        var samplers = Arrays.stream(inputs).distinct().mapToObj(index ->
            new LegacyFinalShaderAdapter.SamplerBinding("colortex" + index,
                LegacyFinalShaderAdapter.TextureSemantic.COLOR, index)).toList();
        return new PreparedFinalProgram(null, "test", "",
            new LegacyFinalShaderAdapter.AdaptedShader("", samplers, List.of()), outputs, formats);
    }

    @Test void retentionPinsLastWriterEvenWithoutAnyFollowingReader() throws Exception {
        var formats = new ColorBufferFormats(Map.of(), Map.of(7, false), Map.of());
        var plan = ColorBufferPlan.create(List.of(program(List.of(7), formats, 7),
            program(List.of(7), formats, 7), program(List.of(8), formats),
            program(List.of(-1), formats, 0)), Set.of(7), formats);
        int retained = plan.steps().get(1).outputSlot();
        assertEquals(Map.of(7, retained), plan.retainedColorSlots());
        assertNotEquals(retained, plan.steps().get(2).outputSlot());
        assertNotEquals(retained, plan.steps().get(3).outputSlot());
        assertEquals(plan.steps().get(0).outputSlot(), plan.steps().get(2).outputSlot());
    }

    @Test void untouchedHistoryAndClearEveryFrameOutputsNeedNoCommit() throws Exception {
        var formats = new ColorBufferFormats(Map.of(), Map.of(7, false), Map.of());
        var plan = ColorBufferPlan.create(List.of(program(List.of(8), formats, 7),
            program(List.of(-1), formats, 8)), Set.of(7, 8), formats);
        assertTrue(plan.retainedColorSlots().isEmpty());
        assertEquals(Map.of(7, ColorBufferFormat.RGBA8), plan.borrowedColorFormats());
    }

    @Test void randomizedMrtPlansKeepCorrectValuesAcrossFrames() throws Exception {
        Random random = new Random(20260913);
        var formats = new ColorBufferFormats(Map.of(7, ColorBufferFormat.RGBA16F),
            Map.of(7, false, 8, false, 9, false), Map.of());
        Set<Integer> initial = Set.of(7, 8, 9);
        for (int trial = 0; trial < 300; trial++) {
            List<PreparedFinalProgram> programs = new ArrayList<>();
            for (int stage = 0; stage < 12; stage++) {
                int a = 7 + random.nextInt(3), b = 7 + random.nextInt(3);
                programs.add(program(a == b ? List.of(a) : List.of(a, b), formats, 7, 8, 9));
            }
            programs.add(program(List.of(-1), formats, 7, 8, 9));
            var plan = ColorBufferPlan.create(programs, initial, formats);
            int[] world = new int[16];
            int serial = 0;
            for (int frame = 0; frame < 8; frame++) {
                int[] logical = world.clone(), scratch = new int[plan.scratchCount()];
                for (int stage = 0; stage < programs.size(); stage++) {
                    var program = programs.get(stage);
                    var step = plan.steps().get(stage);
                    for (var sampler : program.fragment().samplers()) {
                        Integer borrowed = step.worldColorInputs().get(sampler.name());
                        if (borrowed != null) assertEquals(logical[sampler.colorIndex()], world[borrowed]);
                        else {
                            int slot = step.colorInputs().get(sampler.name());
                            assertFalse(step.outputSlots().contains(slot), "Read/write feedback");
                            assertEquals(logical[sampler.colorIndex()], scratch[slot]);
                        }
                    }
                    for (int output = 0; output < program.outputBuffers().size(); output++) {
                        int buffer = program.outputBuffers().get(output);
                        scratch[step.outputSlots().get(output)] = ++serial;
                        if (buffer >= 0) logical[buffer] = serial;
                    }
                }
                plan.retainedColorSlots().forEach((buffer, slot) -> world[buffer] = scratch[slot]);
                for (int buffer : initial) assertEquals(logical[buffer], world[buffer], "Wrong cross-frame history");
            }
        }
    }

    private PreparedPackPipeline prepare(String directives, boolean world) throws Exception {
        Path shaders = Files.createDirectories(directory.resolve("shaders"));
        if (world) Files.writeString(shaders.resolve("gbuffers_terrain.fsh"), "/* DRAWBUFFERS:0 */\nvoid main(){gl_FragColor=vec4(1);}");
        Files.writeString(shaders.resolve("composite.fsh"), directives + "\n/* DRAWBUFFERS:7 */\nvoid main(){gl_FragColor=vec4(1);}");
        return new PackProgramCompiler(ShaderPackLimits.DEFAULT).prepare(new ShaderPackDescriptor(
            "history", "History", directory, ShaderPackDescriptor.Kind.DIRECTORY));
    }

    @Test void writeOnlyRetainedOutputStillGetsWorldStorage() throws Exception {
        var prepared = prepare("const bool colortex7Clear = false;", true);
        var plan = prepared.variants().get("shaders").buffers();
        assertEquals(Set.of(7), plan.retainedColorSlots().keySet());
        assertTrue(plan.borrowedColorFormats().isEmpty());
        assertTrue(prepared.world().variant("shaders").initialColorBuffers().contains(7));
    }

    @Test void postOnlyHistoryFailsInsteadOfSilentlyDiscardingFrames() {
        assertThrows(ShaderCompatibilityException.class, () -> prepare("const bool colortex7Clear = false;", false));
    }

    @Test void customMainClearCannotOverwriteVanillaOwnership() {
        assertThrows(ShaderCompatibilityException.class, () -> prepare("const vec4 colortex0ClearColor = vec4(0);", true));
    }

    @Test void gpuFixtureRequiresRetainedHdrAndExplicitClearedInputs() throws Exception {
        var resource = getClass().getClassLoader().getResource("world-buffer-history");
        assertNotNull(resource);
        var prepared = new PackProgramCompiler(ShaderPackLimits.DEFAULT).prepare(new ShaderPackDescriptor(
            "history", "History", Path.of(resource.toURI()), ShaderPackDescriptor.Kind.DIRECTORY));
        var plan = prepared.variants().get("shaders").buffers();
        assertEquals(Set.of(7), plan.retainedColorSlots().keySet());
        assertEquals(Set.of(1, 7, 8), plan.borrowedColorFormats().keySet());
        assertFalse(prepared.world().variant("shaders").terrainTargets().contains(7));
    }
}
