package dev.oculusforge.render.program;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class WorldPostPhasePlanTest {
    private PreparedFinalProgram program(String stage, List<Integer> outputs, int... inputs) {
        var samplers = Arrays.stream(inputs).distinct().mapToObj(index ->
            new LegacyFinalShaderAdapter.SamplerBinding("colortex" + index,
                LegacyFinalShaderAdapter.TextureSemantic.COLOR, index)).toList();
        return new PreparedFinalProgram(null, stage, "",
            new LegacyFinalShaderAdapter.AdaptedShader("", samplers, List.of()), outputs);
    }

    @Test void deferredExportsArePinnedEvenWhenTheyClearEveryFrame() throws Exception {
        var programs = List.of(program("deferred",List.of(0,4),0), program("deferred1",List.of(5)),
            program("deferred2",List.of(6)), program("final",List.of(-1),0,4));
        var plan = WorldPostPhasePlan.create(programs,Set.of(),ColorBufferFormats.DEFAULT);
        assertEquals(3,plan.deferredCount());
        assertEquals(Set.of(4,5,6),plan.worldBuffers());
        var deferred = plan.deferred();
        int export4 = deferred.steps().getFirst().outputSlots().get(1);
        assertEquals(export4,deferred.retainedColorSlots().get(4));
        assertFalse(deferred.steps().get(1).outputSlots().contains(export4));
        assertFalse(deferred.steps().get(2).outputSlots().contains(export4));
        assertTrue(ColorBufferFormats.DEFAULT.shouldClear(4));
        assertEquals(Map.of("colortex4",4),plan.composite().steps().getFirst().worldColorInputs());
        assertEquals(ColorBufferPlan.MAIN,plan.composite().steps().getFirst().colorInputs().get("colortex0"));
    }

    @Test void compositeImportsWaterUpdatedAttachmentsRatherThanEarlierDeferredScratch() throws Exception {
        var programs = List.of(program("deferred",List.of(0,4),0,4),program("composite",List.of(0),0,4),
            program("final",List.of(-1),0));
        var plan = WorldPostPhasePlan.create(programs,Set.of(4),ColorBufferFormats.DEFAULT);
        // Simulate commit followed by a water write, then resolve the actual
        // allocator-produced input route (not merely the logical buffer name).
        var world = new HashMap<Integer,Integer>(Map.of(4,100));
        var deferred = plan.deferred();
        var slots = new HashMap<Integer,Integer>();
        slots.put(deferred.retainedColorSlots().get(4),200);
        world.put(4,slots.get(deferred.retainedColorSlots().get(4)));
        world.put(4,world.get(4)+7); // translucent gbuffer write, after deferred
        var postStep = plan.composite().steps().getFirst();
        Integer imported = postStep.worldColorInputs().get("colortex4");
        int actual = imported == null ? slots.get(postStep.colorInputs().get("colortex4")) : world.get(imported);
        assertEquals(207,actual);
        assertEquals(200,slots.get(deferred.retainedColorSlots().get(4)));
        // Negative control: the old unsplit plan still routes to stale scratch.
        var unsplit = ColorBufferPlan.create(programs,Set.of(4));
        assertFalse(unsplit.steps().get(1).worldColorInputs().containsKey("colortex4"));
        assertEquals(unsplit.steps().getFirst().outputSlots().get(1),
            unsplit.steps().get(1).colorInputs().get("colortex4"));
    }

    @Test void sameLogicalBufferCanBeReadAndWrittenWithoutFeedback() throws Exception {
        var plan = WorldPostPhasePlan.create(List.of(program("deferred",List.of(4),4),
            program("deferred1",List.of(4),4),program("final",List.of(-1),4)),Set.of(4),ColorBufferFormats.DEFAULT);
        assertEquals(Map.of("colortex4",4),plan.deferred().steps().getFirst().worldColorInputs());
        assertNotEquals(plan.deferred().steps().getFirst().outputSlot(),plan.deferred().steps().get(1).outputSlot());
        assertEquals(plan.deferred().steps().get(1).outputSlot(),plan.deferred().retainedColorSlots().get(4));
    }

    @Test void historyPolicyAndOutputFormatsSurviveTheSplit() throws Exception {
        var formats = new ColorBufferFormats(Map.of(0,ColorBufferFormat.R11F_G11F_B10F,4,ColorBufferFormat.RGBA16F),
            Map.of(4,false),Map.of());
        var programs = List.of(program("deferred",List.of(4),4).withFormats(formats),
            program("composite",List.of(4),4).withFormats(formats),program("final",List.of(-1),0,4).withFormats(formats));
        var plan = WorldPostPhasePlan.create(programs,Set.of(4),formats);
        assertEquals(ColorBufferFormat.RGBA16F,plan.deferred().scratchFormats().getFirst());
        assertTrue(plan.composite().retainedColorSlots().containsKey(4));
        assertEquals(ColorBufferFormat.RGBA16F,plan.composite().borrowedColorFormats().get(4));
        assertFalse(formats.shouldClear(4));
    }

    @Test void noDeferredAndDeferredOnlyPlansAreWellDefined() throws Exception {
        var post = WorldPostPhasePlan.create(List.of(program("final",List.of(-1),0)),Set.of(),ColorBufferFormats.DEFAULT);
        assertEquals(0,post.deferredCount());
        assertEquals(0,post.deferred().scratchCount());
        var only = WorldPostPhasePlan.create(List.of(program("deferred99",List.of(4))),Set.of(),ColorBufferFormats.DEFAULT);
        assertEquals(1,only.deferredCount());
        assertEquals(0,only.composite().steps().size());
        assertEquals(ColorBufferPlan.MAIN,only.composite().presentedSlot());
    }

    @Test void misplacedDeferredOrScreenWritesAreRejected() {
        assertThrows(ShaderCompatibilityException.class,()->WorldPostPhasePlan.create(
            List.of(program("composite",List.of(0)),program("deferred",List.of(4))),Set.of(),ColorBufferFormats.DEFAULT));
        assertThrows(ShaderCompatibilityException.class,()->WorldPostPhasePlan.create(
            List.of(program("deferred",List.of(-1))),Set.of(),ColorBufferFormats.DEFAULT));
        assertFalse(WorldPostPhasePlan.isDeferred("deferred100"));
    }

    @Test void combinedPersistentAndFramegraphScratchRespectOneBudget() throws Exception {
        var plan = WorldPostPhasePlan.create(List.of(program("deferred",List.of(0),0),
            program("final",List.of(-1),0)),Set.of(),ColorBufferFormats.DEFAULT);
        assertDoesNotThrow(()->plan.checkMemoryBudget(960,540));
        assertThrows(IllegalStateException.class,()->plan.checkMemoryBudget(5000,4000));
    }
}
