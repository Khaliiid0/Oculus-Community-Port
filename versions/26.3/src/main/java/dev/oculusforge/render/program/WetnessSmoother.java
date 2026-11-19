package dev.oculusforge.render.program;

import java.util.Objects;

/** One render-thread state per variant; native and all post consumers share a frame sample. */
public final class WetnessSmoother {
    private final WetnessSettings settings;
    private Object world, frame;
    private double accumulator;
    private Value current;

    public WetnessSmoother(WetnessSettings settings) { this.settings = Objects.requireNonNull(settings); }
    public WetnessSettings settings() { return settings; }

    public Value update(Object worldIdentity, Object frameIdentity, float rain, double deltaSeconds) {
        Objects.requireNonNull(worldIdentity, "worldIdentity");
        Objects.requireNonNull(frameIdentity, "frameIdentity");
        new Value(rain); // Validate even duplicate-frame input before touching history.
        if (!Double.isFinite(deltaSeconds) || deltaSeconds < 0)
            throw new IllegalArgumentException("Invalid wetness frame duration");
        if (world == worldIdentity && frame == frameIdentity) return current;
        double next = rain;
        if (world == worldIdentity && current != null) {
            double halfLife = rain > accumulator ? settings.wettingSeconds() : settings.dryingSeconds();
            double blend = halfLife == 0 ? 1 : -Math.expm1(-Math.log(2) * deltaSeconds / halfLife);
            next = accumulator * (1 - blend) + rain * blend;
        }
        var value = new Value((float) next);
        accumulator = next;
        current = value;
        world = worldIdentity;
        frame = frameIdentity;
        return value;
    }

    public void reset() { world = null; frame = null; current = null; accumulator = 0; }
    public record Value(float value) {
        public Value {
            if (!Float.isFinite(value) || value < 0 || value > 1)
                throw new IllegalArgumentException("Invalid wetness/rain sample");
        }
    }
}
