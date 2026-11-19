package dev.oculusforge.render.program;

import java.util.regex.Pattern;

/** Restores the pipeline's fixed-function alpha test after the pack computes color. */
final class NativeAlphaTest {
    private static final Pattern COLOR_ZERO = Pattern.compile(
        "layout\\(location\\s*=\\s*0\\)\\s+out\\s+vec4\\s+oculus_FragColor0\\s*;");
    private static final Pattern MAIN = Pattern.compile("\\bvoid\\s+main\\s*\\(\\s*(?:void\\s*)?\\)");

    private NativeAlphaTest() {}

    static String adapt(String source) throws ShaderCompatibilityException {
        // Do not invent an alpha value for depth-only or auxiliary-only programs.
        if (!COLOR_ZERO.matcher(source).find()) return source;
        var main = MAIN.matcher(source);
        if (!main.find()) throw new ShaderCompatibilityException("Native fragment shader has no main function");
        // A wrapper also handles early returns in the pack's main. Explicit discard
        // still terminates the invocation before this test, and MRT alpha is read
        // from output location zero, not logical colortex0 or an atlas resample.
        return main.replaceFirst("void oculusNativeFragmentMain()") + """

            void main() {
                oculusNativeFragmentMain();
            #ifdef ALPHA_CUTOUT
                if (oculus_FragColor0.a < ALPHA_CUTOUT) discard;
            #endif
            }
            """;
    }
}
