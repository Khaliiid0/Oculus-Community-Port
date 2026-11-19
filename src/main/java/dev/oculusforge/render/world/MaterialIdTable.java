package dev.oculusforge.render.world;

import java.util.Arrays;

/** Immutable RGBA8 lookup payload: RGB encodes material ID + 1; zero means unmapped (-1). */
public final class MaterialIdTable {
    public static final int WIDTH = 256, MAX_STATES = 262144;
    private final int[] materialIds;

    public MaterialIdTable(int[] values) {
        if (values.length < 1 || values.length > MAX_STATES || values[0] != -1)
            throw new IllegalArgumentException("Invalid material lookup table size or sentinel");
        materialIds = values.clone();
        for (int value : materialIds) if (value < -1 || value > 65535) throw new IllegalArgumentException("Invalid material lookup ID");
    }

    public int height() { return (materialIds.length + WIDTH - 1) / WIDTH; }
    public int size() { return materialIds.length; }
    public int material(int encodedState) {
        long index = Math.abs((long) encodedState);
        return index < materialIds.length ? materialIds[(int) index] : -1;
    }
    public int pixelAbgr(int pixel) {
        if (pixel < 0 || pixel >= WIDTH * height()) throw new IndexOutOfBoundsException(pixel);
        int id = pixel < materialIds.length ? materialIds[pixel] : -1;
        return 0xFF000000 | (id + 1);
    }
    public static int[] unmapped(int size) { int[] values = new int[size]; Arrays.fill(values, -1); return values; }
}
