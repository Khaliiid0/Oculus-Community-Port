package dev.oculusforge.render.program;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class WetnessSmootherTest {
    private final Object world = new Object();
    private final WetnessSmoother smoother = new WetnessSmoother(WetnessSettings.DEFAULT);
    private float sample(float rain, double seconds) {
        return smoother.update(world, new Object(), rain, seconds).value();
    }

    @Test void defaultRuntimeHalfLivesAreSixtyAndTwentySeconds() {
        assertEquals(60, WetnessSettings.DEFAULT.wettingSeconds());
        assertEquals(20, WetnessSettings.DEFAULT.dryingSeconds());
        assertEquals(0, sample(0, 0));
        assertEquals(.5f, sample(1, 60));
        assertEquals(.25f, sample(0, 20));
    }

    @Test void firstSampleSeedsRealRainIncludingAnAlreadyRainingWorld() {
        assertEquals(.8f, sample(.8f, 999));
        assertEquals(.8f, sample(0, 0));
    }

    @Test void wettingAndDryingSettingsAreIndependent() {
        var fast = new WetnessSmoother(new WetnessSettings(10, 30));
        fast.update(world, new Object(), 0, 0);
        assertEquals(.5f, fast.update(world, new Object(), 1, 1).value());
        assertEquals(.25f, fast.update(world, new Object(), 0, 3).value());
    }

    @Test void repeatedConsumersReturnTheSameSampleWithoutAdvancing() {
        sample(0, 0);
        var frame = new Object();
        var value = smoother.update(world, frame, 1, 60);
        for (int consumer = 0; consumer < 20; consumer++)
            assertSame(value, smoother.update(world, frame, 0, 600));
        assertEquals(.5f, value.value());
        assertEquals(.75f, sample(1, 60));
    }

    @Test void equalWrappedFrameCountersDoNotDeduplicateDistinctFrames() {
        sample(0, 0);
        var first = new AnimationClock.Snapshot(0, 60, 0);
        var second = new AnimationClock.Snapshot(0, 60, 0);
        assertEquals(first, second);
        assertEquals(.5f, smoother.update(world, first, 1, 60).value());
        assertEquals(.75f, smoother.update(world, second, 1, 60).value());
    }

    @Test void partitioningElapsedTimeDoesNotChangeTheFilter() {
        sample(0, 0);
        for (int i = 0; i < 600; i++) sample(1, .1);
        assertEquals(.5f, sample(1, 0), 1e-7);
        for (int i = 0; i < 200; i++) sample(0, .1);
        assertEquals(.25f, sample(0, 0), 1e-7);
    }

    @Test void zeroHalfLifeSnapsAndLongFrameDoesNotClampToOneSecond() {
        var snap = new WetnessSmoother(new WetnessSettings(0, 0));
        snap.update(world, new Object(), 0, 0);
        assertEquals(1, snap.update(world, new Object(), 1, 0).value());
        assertEquals(0, snap.update(world, new Object(), 0, 0).value());
        sample(0, 0);
        assertEquals(.5f, sample(1, 60));
        assertEquals(1, sample(1, Double.MAX_VALUE));
        assertEquals(0, sample(0, Double.MAX_VALUE));
    }

    @Test void worldIdentityAndExplicitResetReseedRatherThanRetainingOldRain() {
        sample(0, 0); sample(1, 60);
        var frame = new Object();
        assertEquals(.9f, smoother.update(new Object(), frame, .9f, 0).value());
        assertEquals(.1f, smoother.update(world, frame, .1f, 0).value());
        smoother.reset(); smoother.reset();
        assertEquals(.7f, smoother.update(world, frame, .7f, 0).value());
    }

    @Test void invalidInputsEvenOnTheSameFrameDoNotPoisonHistory() {
        sample(0, 0);
        var frame = new Object();
        var good = smoother.update(world, frame, 1, 60);
        for (float bad : new float[]{Float.NaN, Float.POSITIVE_INFINITY, -.01f, 1.01f})
            assertThrows(IllegalArgumentException.class, () -> smoother.update(world, frame, bad, 1));
        for (double bad : new double[]{Double.NaN, Double.POSITIVE_INFINITY, -1})
            assertThrows(IllegalArgumentException.class, () -> smoother.update(world, frame, 1, bad));
        assertThrows(NullPointerException.class, () -> smoother.update(null, frame, 1, 1));
        assertThrows(NullPointerException.class, () -> smoother.update(world, null, 1, 1));
        assertSame(good, smoother.update(world, frame, 1, 60));
        assertEquals(.75f, sample(1, 60));
    }

    @Test void invalidSettingsRejectInsteadOfClamping() {
        for (double bad : new double[]{-1, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> new WetnessSettings(bad, 1));
            assertThrows(IllegalArgumentException.class, () -> new WetnessSettings(1, bad));
        }
        assertThrows(NullPointerException.class, () -> new WetnessSmoother(null));
    }
}
