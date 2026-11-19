package dev.oculusforge.render.world;

import dev.oculusforge.render.program.ColorBufferPlan;
import dev.oculusforge.render.program.ColorBufferFormats;
import java.util.HashSet;
import java.util.Set;

/** CPU-only allocation contract; colortex0 is borrowed from Minecraft, not owned here. */
public record WorldTargetLayout(int width, int height, Set<Integer> auxiliaryBuffers, int shadowResolution, ColorBufferFormats formats, boolean opaqueWorldDepth,
                                Set<Integer> shadowColors, boolean sceneColor) {
    public static final long MAX_BYTES = 192L * 1024 * 1024;
    public WorldTargetLayout(int width,int height,Set<Integer> auxiliaryBuffers,int shadowResolution,
        ColorBufferFormats formats,boolean opaqueWorldDepth,Set<Integer> shadowColors) {
        this(width,height,auxiliaryBuffers,shadowResolution,formats,opaqueWorldDepth,shadowColors,false);
    }

    public WorldTargetLayout(int width, int height, Set<Integer> auxiliaryBuffers, int shadowResolution) {
        this(width, height, auxiliaryBuffers, shadowResolution, ColorBufferFormats.DEFAULT);
    }
    public WorldTargetLayout(int width, int height, Set<Integer> auxiliaryBuffers, int shadowResolution, ColorBufferFormats formats) {
        this(width,height,auxiliaryBuffers,shadowResolution,formats,false);
    }
    public WorldTargetLayout(int width, int height, Set<Integer> auxiliaryBuffers, int shadowResolution, ColorBufferFormats formats, boolean opaqueWorldDepth) {
        this(width,height,auxiliaryBuffers,shadowResolution,formats,opaqueWorldDepth,shadowResolution == 0 ? Set.of() : Set.of(0));
    }

    public WorldTargetLayout {
        if (width <= 0 || height <= 0 || shadowResolution < 0 || shadowResolution > 4096) {
            throw new IllegalArgumentException("Invalid world-render target dimensions");
        }
        Set<Integer> owned = new HashSet<>(auxiliaryBuffers);
        if (owned.stream().anyMatch(index -> index == null || index < 0 || index >= ColorBufferPlan.MAX_BUFFERS)) {
            throw new IllegalArgumentException("Invalid logical world color buffer");
        }
        owned.remove(0);
        auxiliaryBuffers = Set.copyOf(owned);
        formats = java.util.Objects.requireNonNull(formats);
        shadowColors = Set.copyOf(shadowColors);
        if (shadowColors.stream().anyMatch(i -> i < 0 || i > 1)
            || (shadowResolution == 0) != shadowColors.isEmpty())
            throw new IllegalArgumentException("Shadow colors must match the active shadow layout");
        long bytes = estimateBytes(width, height, owned, shadowResolution, formats, opaqueWorldDepth, shadowColors.size(),sceneColor);
        if (bytes > MAX_BYTES) {
            throw new IllegalStateException("World gbuffers would exceed the 192 MiB safety budget");
        }
    }

    public long byteSize() {
        return estimateBytes(width, height, auxiliaryBuffers, shadowResolution, formats, opaqueWorldDepth, shadowColors.size(),sceneColor);
    }

    public int textureCount() {
        return auxiliaryBuffers.size() + (shadowResolution == 0 ? 0 : shadowColors.size() + 2) + (opaqueWorldDepth ? 1 : 0) + (sceneColor ? 1 : 0);
    }

    private static long estimateBytes(int width, int height, Set<Integer> colors, int shadowResolution, ColorBufferFormats formats, boolean opaqueWorldDepth, int shadowColorCount,boolean sceneColor) {
        long bytesPerPixel = colors.stream().mapToLong(index -> formats.get(index).bytesPerPixel()).sum()
            + (opaqueWorldDepth ? 4 : 0) + (sceneColor ? formats.get(0).bytesPerPixel() : 0);
        long colorBytes = Math.multiplyExact(Math.multiplyExact((long) width, height), bytesPerPixel);
        // Independent RGBA8 shadow colors, D32 live depth, and D32 opaque snapshot.
        long shadowBytes = Math.multiplyExact(Math.multiplyExact((long) shadowResolution, shadowResolution), (shadowColorCount + 2) * 4L);
        return Math.addExact(colorBytes, shadowBytes);
    }
}
