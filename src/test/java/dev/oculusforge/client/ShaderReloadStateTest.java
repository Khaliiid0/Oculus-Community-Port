package dev.oculusforge.client;

import static org.junit.jupiter.api.Assertions.*;
import dev.oculusforge.config.OculusConfig;
import java.util.Random;
import org.junit.jupiter.api.Test;

final class ShaderReloadStateTest {
    @Test void newestSelectionWinsEvenWhenSelectingTheSamePackAgain() {
        var state = new ShaderReloadState(OculusConfig.DEFAULT);
        var first = state.select(new OculusConfig(true, "a"));
        var second = state.select(new OculusConfig(true, "a"));
        assertFalse(state.isCurrent(first)); assertTrue(state.isCurrent(second));
    }

    @Test void disablingInvalidatesInFlightPreparation() {
        var state = new ShaderReloadState(OculusConfig.DEFAULT);
        var enabled = state.select(new OculusConfig(true, "a"));
        state.select(OculusConfig.DEFAULT);
        assertFalse(state.isCurrent(enabled));
    }

    @Test void fullResourceBarrierHoldsUntilTheNewestReloadFinishes() {
        var state = new ShaderReloadState(OculusConfig.DEFAULT);
        var first = state.beginResources(); var second = state.beginResources();
        assertFalse(state.finishResources(first.epoch())); assertTrue(state.resourcesPending());
        assertTrue(state.finishResources(second.epoch())); assertFalse(state.resourcesPending());
        assertFalse(state.finishResources(second.epoch()));
    }

    @Test void selectionDuringResourceReloadRemainsCurrentAfterBarrier() {
        var state = new ShaderReloadState(OculusConfig.DEFAULT);
        var resource = state.beginResources();
        var newer = state.select(new OculusConfig(true, "new"));
        assertFalse(state.isCurrent(resource.selection())); assertTrue(state.isCurrent(newer));
        assertTrue(state.resourcesPending()); assertTrue(state.finishResources(resource.epoch()));
        assertTrue(state.isCurrent(newer)); assertFalse(state.resourcesPending());
    }

    @Test void closeMakesAllResultsStale() {
        var state = new ShaderReloadState(OculusConfig.DEFAULT);
        var selection = state.select(new OculusConfig(true, "a")); var resource = state.beginResources();
        state.close();
        assertFalse(state.isCurrent(selection)); assertFalse(state.finishResources(resource.epoch()));
        assertThrows(IllegalStateException.class, () -> state.select(OculusConfig.DEFAULT));
        assertThrows(IllegalStateException.class, state::beginResources);
    }

    @Test void manyOutOfOrderCompletionsCannotRestoreAnOlderSelection() {
        var state = new ShaderReloadState(OculusConfig.DEFAULT);
        var requests = new java.util.ArrayList<ShaderReloadState.Request>();
        for (int i = 0; i < 1000; i++) requests.add(state.select(new OculusConfig(i % 3 != 0, "pack" + (i % 4))));
        java.util.Collections.shuffle(requests, new Random(3));
        assertEquals(1, requests.stream().filter(state::isCurrent).count());
        assertEquals(1000, state.current().revision());
    }
}
