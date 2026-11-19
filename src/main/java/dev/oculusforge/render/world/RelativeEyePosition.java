package dev.oculusforge.render.world;

/** World-aligned camera-minus-eye displacement, narrowed only after subtraction. */
public record RelativeEyePosition(float x, float y, float z) {
    public static final RelativeEyePosition ZERO = new RelativeEyePosition(0, 0, 0);
    public RelativeEyePosition {
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z))
            throw new IllegalArgumentException("Non-finite relative eye position");
    }
    public static RelativeEyePosition between(double cx, double cy, double cz, double ex, double ey, double ez) {
        return new RelativeEyePosition((float) (cx - ex), (float) (cy - ey), (float) (cz - ez));
    }
}
