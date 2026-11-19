package dev.oculusforge.render.program;

/** Render-thread-confined clock. Consumers only read an immutable per-frame snapshot. */
public final class AnimationClock {
    public static final Snapshot ZERO = new Snapshot(0, 0, 0);
    private boolean started, ended;
    private long previousStart, previousEnd;
    private int nextFrame;
    private double seconds;
    private float completedFrameSeconds;
    private Snapshot current = ZERO;

    public Snapshot beginFrame(long nanos) {
        if (started) seconds = (seconds + elapsed(previousStart, nanos)) % 3600.0;
        previousStart = nanos;
        started = true;
        // Rounding a double just below 3600 to float must not escape the documented range.
        float counter = Math.min((float) seconds, Math.nextDown(3600.0F));
        current = new Snapshot(nextFrame, completedFrameSeconds, counter);
        nextFrame = (nextFrame + 1) % 720720;
        return current;
    }

    public void endFrame(long nanos) {
        completedFrameSeconds = ended ? (float) elapsed(previousEnd, nanos) : 0;
        previousEnd = nanos;
        ended = true;
    }

    private static double elapsed(long before, long after) {
        // nanoTime subtraction also handles its signed-long rollover for realistic intervals.
        return Math.max(0L, after - before) / 1.0E9;
    }

    public Snapshot current() { return current; }

    public record Snapshot(int frameCounter, float frameTime, float frameTimeCounter) { }
}
