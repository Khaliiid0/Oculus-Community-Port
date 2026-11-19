package dev.oculusforge.render.pipeline;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class RenderCompatibilityPauseTest {
    @Test void startsOpenWithoutACompatibilityWarning() {
        var gate = new RenderCompatibilityPause();
        var pipeline = new Object();
        assertFalse(gate.paused());
        assertEquals("", gate.reason());
        assertSame(pipeline, gate.visible(pipeline));
    }

    @Test void extractedImprovedTransparencyAloneBlocksBeforeSubmission() {
        var gate = new RenderCompatibilityPause();
        assertTrue(gate.update(true, true, true, false));
        assertTrue(gate.paused());
        assertNull(gate.visible(new Object()));
    }

    @Test void rendererImprovedTransparencyAloneAlsoBlocks() {
        var gate = new RenderCompatibilityPause();
        assertTrue(gate.update(true, true, false, true));
        assertEquals(RenderCompatibilityPause.IMPROVED_TRANSPARENCY, gate.reason());
    }

    @Test void bothImprovedFlagsBlock() {
        var gate = new RenderCompatibilityPause();
        assertTrue(gate.update(true, true, true, true));
        assertTrue(gate.paused());
    }

    @Test void repeatedIncompatibleFramesDoNotPublishRepeatedTransitions() {
        var gate = new RenderCompatibilityPause();
        assertTrue(gate.update(true, true, true, true));
        for (int frame = 0; frame < 1000; frame++) assertFalse(gate.update(true, true, true, true));
        assertTrue(gate.paused());
    }

    @Test void mixedSnapshotChangesDoNotResumeEarly() {
        var gate = new RenderCompatibilityPause();
        gate.update(true, true, true, true);
        assertFalse(gate.update(true, true, false, true));
        assertTrue(gate.paused());
        assertFalse(gate.update(true, true, true, false));
        assertTrue(gate.paused());
    }

    @Test void classicTransparencyResumesTheIdenticalConfiguredGeneration() {
        var gate = new RenderCompatibilityPause();
        var pipeline = new Object();
        gate.update(true, true, true, false);
        assertNull(gate.visible(pipeline));
        assertTrue(gate.update(true, true, false, false));
        assertSame(pipeline, gate.visible(pipeline));
        assertEquals("", gate.reason());
    }

    @Test void compatibleFramesDoNotRepeatedlyReinstallRouting() {
        var gate = new RenderCompatibilityPause();
        assertFalse(gate.update(true, true, false, false));
        gate.update(true, true, true, true);
        assertTrue(gate.update(true, true, false, false));
        for (int frame = 0; frame < 1000; frame++) assertFalse(gate.update(true, true, false, false));
    }

    @Test void postOnlyVariantWithoutClassicRequirementsStaysAvailable() {
        var gate = new RenderCompatibilityPause();
        var pipeline = new Object();
        assertFalse(gate.update(true, false, true, true));
        assertSame(pipeline, gate.visible(pipeline));
    }

    @Test void dimensionWithoutClassicRequirementsClearsPreviousPause() {
        var gate = new RenderCompatibilityPause();
        gate.update(true, true, true, true);
        assertTrue(gate.update(true, false, true, true));
        assertFalse(gate.paused());
    }

    @Test void absentPipelineCannotBeResurrectedByCompatibilityChanges() {
        var gate = new RenderCompatibilityPause();
        gate.update(true, true, true, true);
        assertTrue(gate.update(false, true, false, false));
        assertNull(gate.visible(null));
        assertFalse(gate.update(false, true, true, true));
        assertFalse(gate.paused());
    }

    @Test void explicitFailureDisableOrDisposalCanClearPauseIndependently() {
        var gate = new RenderCompatibilityPause();
        gate.update(true, true, true, true);
        gate.clear();
        gate.clear();
        assertFalse(gate.paused());
        assertEquals("", gate.reason());
        assertNull(gate.visible(null));
    }

    @Test void replacementGenerationRemainsHiddenUntilCompatible() {
        var gate = new RenderCompatibilityPause();
        var first = new Object();
        var replacement = new Object();
        gate.update(true, true, true, true);
        assertNull(gate.visible(first));
        assertNull(gate.visible(replacement));
        gate.update(true, true, false, false);
        assertSame(replacement, gate.visible(replacement));
    }

    @Test void repeatedTransitionsAreReversibleAndDoNotOwnOrCloseResources() {
        var gate = new RenderCompatibilityPause();
        var resource = new Object();
        for (int cycle = 0; cycle < 100; cycle++) {
            assertTrue(gate.update(true, true, false, true));
            assertNull(gate.visible(resource));
            assertTrue(gate.update(true, true, false, false));
            assertSame(resource, gate.visible(resource));
        }
    }
}
