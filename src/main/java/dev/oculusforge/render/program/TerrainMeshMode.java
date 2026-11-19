package dev.oculusforge.render.program;

/** Immutable for the JVM lifetime: uploaded chunk strides must never change on pack reload. */
public final class TerrainMeshMode {
    public static final boolean EXTENDED = Boolean.getBoolean("oculus.experimental.extendedTerrain");
    private TerrainMeshMode() { }
}
