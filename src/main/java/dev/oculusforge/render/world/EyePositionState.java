package dev.oculusforge.render.world;

/** Frame-local extension; null means extraction has not produced a valid snapshot. */
public interface EyePositionState {
    RelativeEyePosition oculus$getRelativeEye();
    void oculus$setRelativeEye(RelativeEyePosition value);
}
