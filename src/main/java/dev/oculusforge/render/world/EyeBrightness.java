package dev.oculusforge.render.world;

/** Raw light layers at the extracted camera block, before temporal smoothing. */
public record EyeBrightness(int block, int sky) {
    public static final EyeBrightness DARK = new EyeBrightness(0, 0);
    public EyeBrightness {
        if (block < 0 || block > 15 || sky < 0 || sky > 15)
            throw new IllegalArgumentException("Invalid eye light layers");
    }
    public int blockLightmap() { return block * 16; }
    public int skyLightmap() { return sky * 16; }
    public float maximum() { return Math.max(block, sky) / 15.0f; }
}
