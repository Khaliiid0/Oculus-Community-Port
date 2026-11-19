package dev.oculusforge.render.world;

/** Finite 26.3 environment RGB; no integer quantization, clamping or gamma conversion. */
public record SkyColor(float red, float green, float blue) {
    public static final SkyColor BLACK = new SkyColor(0,0,0);
    public SkyColor {
        if (!Float.isFinite(red) || !Float.isFinite(green) || !Float.isFinite(blue))
            throw new IllegalArgumentException("Invalid environment sky color");
    }
    public static SkyColor fromRgb(org.joml.Vector3fc value) {
        java.util.Objects.requireNonNull(value, "sky color");
        return new SkyColor(value.x(),value.y(),value.z());
    }
    public static SkyColor fromArgb(int color) {
        return new SkyColor((color >>> 16 & 255)/255.0F,(color >>> 8 & 255)/255.0F,(color & 255)/255.0F);
    }
}
