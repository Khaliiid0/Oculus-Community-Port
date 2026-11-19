package dev.oculusforge.render.world;

/** Reject feedback loops and reads before this frame's shadow producer has completed. */
public final class ShadowColorReadGuard {
    private ShadowColorReadGuard() {}
    public static void requireCompleted(boolean shadowPass, boolean completed) {
        if (shadowPass || !completed)
            throw new IllegalStateException("Shadow texture is not readable before its producer completes");
    }
}
