package dev.oculusforge.render.program;

import dev.oculusforge.render.world.EyeBrightness;
import java.util.Objects;

/**
 * Render-thread-confined smoothing state shared by native and post stages
 * of one pack variant.
 */
public final class EyeBrightnessSmoother {
    public static final double DEFAULT_HALF_LIFE_TICKS = 10;
    private final ScalarSmoother blockFilter, skyFilter;
    private Object world;
    private Object frame;
    private Value current;

    public EyeBrightnessSmoother(double halfLifeTicks) {
        if (!Double.isFinite(halfLifeTicks) || halfLifeTicks < 0)
            throw new IllegalArgumentException("Invalid eye-brightness half-life");
        blockFilter = new ScalarSmoother(halfLifeTicks, halfLifeTicks);
        skyFilter = new ScalarSmoother(halfLifeTicks, halfLifeTicks);
    }

    /**
     * frameIdentity must be the same object for all consumers of one render frame
     * (for example its AnimationClock.Snapshot), not a wrapping frame counter.
     * Validation precedes mutation, so missing extraction cannot poison history.
     */
    public Value update(Object worldIdentity, Object frameIdentity, EyeBrightness raw, double deltaSeconds) {
        Objects.requireNonNull(worldIdentity, "worldIdentity");
        Objects.requireNonNull(frameIdentity, "frameIdentity");
        Objects.requireNonNull(raw, "raw");
        if (!Double.isFinite(deltaSeconds) || deltaSeconds < 0)
            throw new IllegalArgumentException("Invalid eye-brightness frame duration");
        if (world == worldIdentity && frame == frameIdentity) return current;

        double block = blockFilter.update(worldIdentity, frameIdentity, raw.blockLightmap(), deltaSeconds);
        double sky = skyFilter.update(worldIdentity, frameIdentity, raw.skyLightmap(), deltaSeconds);
        world = worldIdentity;
        frame = frameIdentity;
        current = new Value((int) Math.round(block), (int) Math.round(sky));
        return current;
    }

    /** Call on logout, reload and close to release old world/frame references. */
    public void reset() {
        world = null;
        frame = null;
        current = null;
        blockFilter.reset();
        skyFilter.reset();
    }

    public record Value(int block, int sky) {
        public Value {
            if (block < 0 || block > 240 || sky < 0 || sky > 240)
                throw new IllegalArgumentException("Invalid smoothed lightmap values");
        }
    }
}
