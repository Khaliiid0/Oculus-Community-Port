package dev.oculusforge.render.program;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

final class ColorBufferLifetimeTest {
    private PreparedFinalProgram program(int output, int... inputs) {
        var samplers = Arrays.stream(inputs).distinct().mapToObj(index ->
            new LegacyFinalShaderAdapter.SamplerBinding("colortex" + index,
                LegacyFinalShaderAdapter.TextureSemantic.COLOR, index)).toList();
        return new PreparedFinalProgram(null, "test", "",
            new LegacyFinalShaderAdapter.AdaptedShader("", samplers, List.of()), output);
    }

    @Test void auxiliaryHandoffChainNeedsTwoTexturesNotSixteen() throws Exception {
        List<PreparedFinalProgram> programs = new ArrayList<>();
        for (int buffer = 1; buffer <= 15; buffer++) programs.add(program(buffer, buffer - 1));
        programs.add(program(-1, 15));
        var plan = ColorBufferPlan.create(programs);
        assertEquals(2, plan.scratchCount());
        assertDoesNotThrow(() -> plan.checkMemoryBudget(3840, 2160, 4));
        verifyAgainstLogicalExecution(programs);
    }

    @Test void presentationPinsEarlierColorZeroAcrossUnrelatedWrites() throws Exception {
        var programs = List.of(program(0, 0), program(1), program(2), program(3, 1));
        var plan = ColorBufferPlan.create(programs);
        assertEquals(plan.steps().getFirst().outputSlot(), plan.presentedSlot());
        for (int i = 1; i < programs.size(); i++) assertNotEquals(plan.presentedSlot(), plan.steps().get(i).outputSlot());
        verifyAgainstLogicalExecution(programs);
    }

    @Test void overwrittenVersionsAndMultipleReadersRetainTheCorrectContents() throws Exception {
        verifyAgainstLogicalExecution(List.of(
            program(1, 0), program(2, 1), program(1, 1, 2),
            program(3, 1, 2), program(2, 1, 3), program(-1, 1, 2, 3)));
    }

    @Test void auxiliaryOnlyChainLeavesMainAsThePresentedImage() throws Exception {
        var programs = List.of(program(1, 0), program(2, 1));
        assertEquals(ColorBufferPlan.MAIN, ColorBufferPlan.create(programs).presentedSlot());
        verifyAgainstLogicalExecution(programs);
    }

    @Test void randomizedPlansMatchAnIndependentLogicalBufferSimulation() throws Exception {
        Random random = new Random(20260826);
        for (int trial = 0; trial < 1000; trial++) {
            List<PreparedFinalProgram> programs = new ArrayList<>();
            List<Integer> available = new ArrayList<>(List.of(0));
            int length = 1 + random.nextInt(33);
            for (int stage = 0; stage < length; stage++) {
                int[] inputs = new int[random.nextInt(6)];
                for (int i = 0; i < inputs.length; i++) inputs[i] = available.get(random.nextInt(available.size()));
                int output = stage == length - 1 && random.nextBoolean() ? -1 : random.nextInt(16);
                programs.add(program(output, inputs));
                if (output >= 0 && !available.contains(output)) available.add(output);
            }
            verifyAgainstLogicalExecution(programs);
        }
    }

    // "Rendering" assigns a unique value to each write. Logical attachments form
    // the reference; the actual side has only the allocator's physical slots.
    private void verifyAgainstLogicalExecution(List<PreparedFinalProgram> programs) throws Exception {
        verifyAgainstLogicalExecution(programs, java.util.Set.of(), ColorBufferFormats.DEFAULT);
    }

    @Test void randomizedTypedWorldImportsMatchLogicalExecution() throws Exception {
        Random random = new Random(20260904);
        var initial = java.util.Set.of(1, 7, 15);
        var formats = new ColorBufferFormats(java.util.Map.of(1, ColorBufferFormat.RGBA16F,
            7, ColorBufferFormat.RGBA16F, 15, ColorBufferFormat.RGB8_SNORM));
        for (int trial = 0; trial < 500; trial++) {
            List<PreparedFinalProgram> programs = new ArrayList<>();
            List<Integer> available = new ArrayList<>(List.of(0, 1, 7, 15));
            int length = 1 + random.nextInt(33);
            for (int stage = 0; stage < length; stage++) {
                int[] inputs = new int[random.nextInt(6)];
                for (int i = 0; i < inputs.length; i++) inputs[i] = available.get(random.nextInt(available.size()));
                int output = stage == length - 1 ? -1 : random.nextInt(16);
                programs.add(program(output, inputs).withFormats(formats));
                if (output >= 0 && !available.contains(output)) available.add(output);
            }
            verifyAgainstLogicalExecution(programs, initial, formats);
        }
    }

    @Test void worldInputsAreReadOnlyAndWritesAlwaysUseSeparateStorage() throws Exception {
        var plan = ColorBufferPlan.create(List.of(program(0, 7), program(-1, 0)), java.util.Set.of(7));
        assertEquals(2, plan.scratchCount());
        assertEquals(java.util.Map.of("colortex7", 7), plan.steps().getFirst().worldColorInputs());
        assertTrue(plan.steps().getLast().worldColorInputs().isEmpty());
        verifyAgainstLogicalExecution(List.of(program(0, 7), program(-1, 0)), java.util.Set.of(7), ColorBufferFormats.DEFAULT);
    }

    private void verifyAgainstLogicalExecution(List<PreparedFinalProgram> programs, java.util.Set<Integer> initial,
                                               ColorBufferFormats formats) throws Exception {
        var plan = ColorBufferPlan.create(programs, initial, formats);
        int[] logical = new int[16];
        Arrays.fill(logical, -999);
        logical[0] = 0;
        int[] physical = new int[plan.scratchCount()];
        Arrays.fill(physical, -888);
        initial.forEach(buffer -> logical[buffer] = -1000 - buffer);
        int expectedPresentation = 0;
        for (int i = 0; i < programs.size(); i++) {
            var program = programs.get(i);
            var step = plan.steps().get(i);
            assertEquals(program.formats().get(program.outputBuffer()), plan.scratchFormats().get(step.outputSlot()));
            for (var sampler : program.fragment().samplers()) {
                Integer worldIndex = step.worldColorInputs().get(sampler.name());
                if (worldIndex != null) {
                    assertTrue(initial.contains(worldIndex));
                    assertEquals(logical[sampler.colorIndex()], -1000 - worldIndex,
                        "World read must not be used after a post stage replaced its logical buffer");
                    continue;
                }
                int slot = step.colorInputs().get(sampler.name());
                assertNotEquals(step.outputSlot(), slot, "Read/write texture feedback at pass " + i);
                assertEquals(logical[sampler.colorIndex()], slot == ColorBufferPlan.MAIN ? 0 : physical[slot],
                    "Wrong producer version at pass " + i + ", sampler " + sampler.name());
            }
            physical[step.outputSlot()] = i + 1;
            if (program.outputBuffer() == -1) expectedPresentation = i + 1;
            else {
                logical[program.outputBuffer()] = i + 1;
                expectedPresentation = logical[0];
            }
        }
        assertEquals(expectedPresentation,
            plan.presentedSlot() == ColorBufferPlan.MAIN ? 0 : physical[plan.presentedSlot()]);
    }

    @Test void multipleWorldReadersAndMrtOverwriteSelectTheRightProducer() throws Exception {
        var formats = new ColorBufferFormats(java.util.Map.of(7, ColorBufferFormat.RGBA16F,
            8, ColorBufferFormat.RGB8_SNORM));
        var inputs = program(7, 7, 8).fragment();
        var mrt = new PreparedFinalProgram(null, "composite", "", inputs, List.of(8, 7), formats);
        var plan = ColorBufferPlan.create(List.of(program(1, 7, 8).withFormats(formats), mrt,
            program(-1, 7, 8).withFormats(formats)), java.util.Set.of(7, 8), formats);
        assertEquals(java.util.Map.of("colortex7", 7, "colortex8", 8), plan.steps().get(0).worldColorInputs());
        assertEquals(plan.steps().get(0).worldColorInputs(), plan.steps().get(1).worldColorInputs());
        assertTrue(plan.steps().get(2).worldColorInputs().isEmpty());
        assertEquals(plan.steps().get(1).outputSlots().get(0), plan.steps().get(2).colorInputs().get("colortex8"));
        assertEquals(plan.steps().get(1).outputSlots().get(1), plan.steps().get(2).colorInputs().get("colortex7"));
        assertEquals(3, plan.scratchCount());
    }

    @Test void borrowedHdrInputsDoNotCountTwiceAgainstPostBudget() throws Exception {
        var formats = new ColorBufferFormats(java.util.Map.of(7, ColorBufferFormat.RGBA32F,
            8, ColorBufferFormat.RGBA32F));
        var plan = ColorBufferPlan.create(List.of(program(-1, 7, 8).withFormats(formats)),
            java.util.Set.of(7, 8), formats);
        assertEquals(1, plan.scratchCount());
        assertEquals(1920L * 1080 * 4, plan.scratchBytes(1920, 1080));
        assertDoesNotThrow(() -> plan.checkMemoryBudget(1920, 1080));
    }
}
