package dev.oculusforge.render.program;

/** One client-renderer timeline, independent of pack reloads, dimensions and program count. */
public final class RenderAnimationClock {
    private static final AnimationClock CLOCK = new AnimationClock();
    private RenderAnimationClock() { }
    public static void beginFrame() { CLOCK.beginFrame(System.nanoTime()); }
    public static void endFrame() { CLOCK.endFrame(System.nanoTime()); }
    public static AnimationClock.Snapshot current() { return CLOCK.current(); }
}
