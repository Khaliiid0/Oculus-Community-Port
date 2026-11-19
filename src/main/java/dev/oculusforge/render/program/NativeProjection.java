package dev.oculusforge.render.program;

import java.util.regex.Pattern;

/** Shared legacy projection expressions for both native shader stages. */
public final class NativeProjection {
    // Retain the existing pipeline define; it is supplied to both stages.
    public static final String ZERO_TO_ONE_DEFINE = "OCULUS_FRAGMENT_ZERO_TO_ONE";
    public static final String CLIP_CONVERSION = ShadowClipSpace.CONVERSION
        .replace(ShadowClipSpace.ZERO_TO_ONE_DEFINE, ZERO_TO_ONE_DEFINE);

    private NativeProjection() { }

    record Adapted(String source, String helpers) { }

    static Adapted adaptExpressions(String source, boolean terrainBuiltins, String stage) {
        String projection = NativeWorldBindings.internal("gbufferProjection");
        String inverse = NativeWorldBindings.internal("gbufferProjectionInverse");
        String function = "oculusNative" + stage + "Projection";
        StringBuilder helpers = new StringBuilder();
        var projectionReads = Pattern.compile("\\b(?:" + projection + (terrainBuiltins ? "|ProjMat" : "") + ")\\b");
        if (projectionReads.matcher(source).find()) {
            source = projectionReads.matcher(source).replaceAll(function + "($0)");
            helpers.append("""
                mat4 %s(mat4 raw) {
                    mat4 conversion = mat4(1.0);
                #ifdef OCULUS_FRAGMENT_ZERO_TO_ONE
                    conversion[2][2] = -2.0;
                    conversion[3][2] = 1.0;
                #else
                    conversion[2][2] = -1.0;
                #endif
                    return conversion * raw;
                }
                """.formatted(function));
        }
        if (Pattern.compile("\\b" + inverse + "\\b").matcher(source).find()) {
            source = source.replaceAll("\\b" + inverse + "\\b", function + "Inverse(" + inverse + ")");
            helpers.append("""
                mat4 %sInverse(mat4 rawInverse) {
                    mat4 conversionInverse = mat4(1.0);
                #ifdef OCULUS_FRAGMENT_ZERO_TO_ONE
                    conversionInverse[2][2] = -0.5;
                    conversionInverse[3][2] = 0.5;
                #else
                    conversionInverse[2][2] = -1.0;
                #endif
                    return rawInverse * conversionInverse;
                }
                """.formatted(function));
        }
        return new Adapted(source, helpers.toString());
    }
}
