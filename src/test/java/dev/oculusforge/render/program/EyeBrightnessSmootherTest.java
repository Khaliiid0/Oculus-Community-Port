package dev.oculusforge.render.program;

import static org.junit.jupiter.api.Assertions.*;
import dev.oculusforge.render.world.EyeBrightness;
import org.junit.jupiter.api.Test;

final class EyeBrightnessSmootherTest {
    @Test void firstFrameStartsAtRealInputAndHalfLifeMovesBothLayersHalfway() {
        var filter = new EyeBrightnessSmoother(10);
        var world = new Object();
        assertEquals(new EyeBrightnessSmoother.Value(0, 240), filter.update(world, new Object(), new EyeBrightness(0, 15), 0));
        assertEquals(new EyeBrightnessSmoother.Value(120, 120), filter.update(world, new Object(), new EyeBrightness(15, 0), .5));
        assertEquals(new EyeBrightnessSmoother.Value(180, 60), filter.update(world, new Object(), new EyeBrightness(15, 0), .5));
    }

    @Test void multipleStagesCannotAdvanceSameFrameMoreThanOnce() {
        var filter = new EyeBrightnessSmoother(10);
        var world = new Object();
        filter.update(world, new Object(), EyeBrightness.DARK, 0);
        var frame = new Object();
        var expected = filter.update(world, frame, new EyeBrightness(15, 15), .5);
        for (int i = 0; i < 40; i++) assertSame(expected, filter.update(world, frame, EyeBrightness.DARK, .5));
        assertEquals(new EyeBrightnessSmoother.Value(120, 120), expected);
        assertEquals(new EyeBrightnessSmoother.Value(180, 180), filter.update(world, new Object(), new EyeBrightness(15, 15), .5));
    }

    @Test void fractionalStateIsPreservedAcrossDifferentFrameRates() {
        for (int steps : new int[] {1, 10, 30, 60, 144, 1000}) {
            var filter = new EyeBrightnessSmoother(10);
            var world = new Object();
            filter.update(world, new Object(), EyeBrightness.DARK, 0);
            EyeBrightnessSmoother.Value result = null;
            for (int i = 0; i < steps; i++) result = filter.update(world, new Object(), new EyeBrightness(15, 15), .5 / steps);
            assertEquals(new EyeBrightnessSmoother.Value(120, 120), result, "steps=" + steps);
        }
    }

    @Test void identityChangeAndExplicitResetStartFreshEvenWithSameFrameToken() {
        var filter = new EyeBrightnessSmoother(10);
        Object oldWorld = new String("same dimension"), newWorld = new String("same dimension"), frame = new Object();
        filter.update(oldWorld, frame, new EyeBrightness(15, 15), 0);
        assertEquals(new EyeBrightnessSmoother.Value(0, 0), filter.update(newWorld, frame, EyeBrightness.DARK, 0));
        filter.reset();
        assertEquals(new EyeBrightnessSmoother.Value(48, 224), filter.update(newWorld, frame, new EyeBrightness(3, 14), 0));
    }

    @Test void zeroHalfLifeIsImmediateButZeroElapsedTimeOtherwiseHoldsHistory() {
        var world = new Object();
        var smooth = new EyeBrightnessSmoother(10);
        var instant = new EyeBrightnessSmoother(0);
        for (var filter : new EyeBrightnessSmoother[] {smooth, instant}) filter.update(world, new Object(), EyeBrightness.DARK, 0);
        assertEquals(new EyeBrightnessSmoother.Value(0, 0), smooth.update(world, new Object(), new EyeBrightness(15, 15), 0));
        assertEquals(new EyeBrightnessSmoother.Value(240, 240), instant.update(world, new Object(), new EyeBrightness(15, 15), 0));
        assertEquals(new EyeBrightnessSmoother.Value(240, 240), smooth.update(world, new Object(), new EyeBrightness(15, 15), Double.MAX_VALUE));
    }

    @Test void invalidCallsDoNotChangeHistoryOrClaimTheFrame() {
        for (double halfLife : new double[] {-1, Double.NaN, Double.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class, () -> new EyeBrightnessSmoother(halfLife));
        var filter = new EyeBrightnessSmoother(10);
        var world = new Object();
        filter.update(world, new Object(), EyeBrightness.DARK, 0);
        var next = new Object();
        for (double delta : new double[] {-1, Double.NaN, Double.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class, () -> filter.update(world, next, new EyeBrightness(15, 15), delta));
        assertThrows(NullPointerException.class, () -> filter.update(null, next, EyeBrightness.DARK, 1));
        assertThrows(NullPointerException.class, () -> filter.update(world, null, EyeBrightness.DARK, 1));
        assertThrows(NullPointerException.class, () -> filter.update(world, next, null, 1));
        assertEquals(new EyeBrightnessSmoother.Value(120, 120), filter.update(world, next, new EyeBrightness(15, 15), .5));
    }

    @Test void customHalfLifeChangesTransitionSpeedWithoutOvershooting() {
        var fast = new EyeBrightnessSmoother(5);
        var slow = new EyeBrightnessSmoother(20);
        var world = new Object();
        for (var filter : new EyeBrightnessSmoother[] {fast, slow}) filter.update(world, new Object(), EyeBrightness.DARK, 0);
        assertEquals(new EyeBrightnessSmoother.Value(180, 180), fast.update(world, new Object(), new EyeBrightness(15, 15), .5));
        assertEquals(new EyeBrightnessSmoother.Value(70, 70), slow.update(world, new Object(), new EyeBrightness(15, 15), .5));
        for (int i = 0; i < 1000; i++) {
            var v = slow.update(world, new Object(), i % 2 == 0 ? EyeBrightness.DARK : new EyeBrightness(15, 15), .016);
            assertTrue(v.block() >= 0 && v.block() <= 240);
        }
    }
}
