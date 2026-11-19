package dev.oculusforge.render.world;

/** Minecraft's packed environment sky RGB, normalized without gamma conversion. */
public record SkyColor(float red, float green, float blue) {
    /** CPU fixture default only; runtime always samples the camera environment probe. */
    public static final SkyColor BLACK = new SkyColor(0, 0, 0);

    public SkyColor {
        if (!channel(red) || !channel(green) || !channel(blue))
            throw new IllegalArgumentException("Invalid environment sky color");
    }

    private static boolean channel(float value) { return Float.isFinite(value) && value >= 0 && value <= 1; }

    public static SkyColor fromArgb(int color) {
        return new SkyColor((color >>> 16 & 255) / 255.0F, (color >>> 8 & 255) / 255.0F, (color & 255) / 255.0F);
    }
}
