package dev.oculusforge.render.program;

import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Immutable, bounded RGB white-noise pixels prepared off the render thread. */
public final class NoiseTextureData {
    public static final int DEFAULT_RESOLUTION = 256;
    public static final int MAX_RESOLUTION = 1024;
    private static final Pattern RESOLUTION = Pattern.compile(
        "\\bconst\\s+int\\s+noiseTextureResolution\\s*=\\s*([^;]+);");
    private final int resolution;
    private final int[] pixelsAbgr;

    private NoiseTextureData(int resolution, int[] pixelsAbgr) {
        this.resolution = resolution;
        this.pixelsAbgr = pixelsAbgr;
    }

    public static int mergeResolution(int previous, String preprocessedSource) throws ShaderCompatibilityException {
        String code = PostProcessCapabilities.withoutComments(preprocessedSource);
        Matcher matcher = RESOLUTION.matcher(code);
        boolean found = false;
        while (matcher.find()) {
            found = true;
            String expression = matcher.group(1).trim();
            if (!expression.matches("[0-9]{1,4}")) {
                throw new ShaderCompatibilityException("noiseTextureResolution requires an integer literal from 1 to 1024; expressions are not supported");
            }
            int requested = Integer.parseInt(expression);
            validate(requested);
            if (previous != 0 && previous != requested) {
                throw new ShaderCompatibilityException("Conflicting noiseTextureResolution values " + previous + " and " + requested
                    + "; all supported stages/dimensions must agree");
            }
            previous = requested;
        }
        if (!found && Pattern.compile("\\bnoiseTextureResolution\\b").matcher(code).find()) {
            throw new ShaderCompatibilityException("noiseTextureResolution must be declared as const int with a supported literal value");
        }
        return previous;
    }

    public static NoiseTextureData generate(int resolution) throws ShaderCompatibilityException {
        validate(resolution);
        int[] pixels = new int[resolution * resolution];
        // Independent implementation; stable across reloads, not a reference-renderer seed guarantee.
        Random random = new Random(0x4F43554C55534CL);
        for (int index = 0; index < pixels.length; index++) {
            // RGB8 sampled behavior, with opaque alpha in RGBA8 GPU storage.
            pixels[index] = 0xFF000000 | (random.nextInt() & 0x00FFFFFF);
        }
        return new NoiseTextureData(resolution, pixels);
    }

    private static void validate(int resolution) throws ShaderCompatibilityException {
        if (resolution < 1 || resolution > MAX_RESOLUTION) {
            throw new ShaderCompatibilityException("noiseTextureResolution " + resolution + " exceeds the supported range 1..1024");
        }
    }

    public int resolution() { return resolution; }
    public int byteSize() { return pixelsAbgr.length * Integer.BYTES; }
    public int pixelAbgr(int index) { return pixelsAbgr[index]; }
}
