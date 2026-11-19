package dev.oculusforge.render.program;

import java.lang.ref.WeakReference;
import java.util.Objects;

/** Render-thread-confined history shared by every native/post consumer of one variant. */
public final class CameraHistory {
    private WeakReference<Object> world = new WeakReference<>(null);
    private Object frame;
    private Snapshot current;

    public Snapshot update(Object worldIdentity, Object frameIdentity, double x, double y, double z) {
        Objects.requireNonNull(worldIdentity, "worldIdentity");
        Objects.requireNonNull(frameIdentity, "frameIdentity");
        Position position = new Position(x,y,z); // Validate before mutating any history.
        boolean sameWorld = world.get() == worldIdentity;
        if (sameWorld && frame == frameIdentity) {
            if (!current.position().equals(position))
                throw new IllegalStateException("Conflicting camera positions in one render frame");
            return current;
        }
        Position previous = sameWorld && current != null ? current.position() : position;
        current = new Snapshot(position,previous);
        if (!sameWorld) world = new WeakReference<>(worldIdentity);
        frame = frameIdentity;
        return current;
    }

    /** Logout/reload/close must release frame identity and start with previous=current. */
    public void reset() {
        world.clear();
        frame = null;
        current = null;
    }

    public record Snapshot(Position position, Position previous) {
        public Snapshot { Objects.requireNonNull(position); Objects.requireNonNull(previous); }
    }
    public record Position(double x, double y, double z) {
        public Position {
            if (!Float.isFinite((float)x) || !Float.isFinite((float)y) || !Float.isFinite((float)z))
                throw new IllegalArgumentException("Non-finite camera position");
        }
    }
}
