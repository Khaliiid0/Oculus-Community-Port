package dev.oculusforge.render.program;

import java.util.regex.Pattern;

/** Legacy pack alpha tests after the pack computes location-zero color, including early returns. */
final class NativeAlphaTest {
    private static final Pattern COLOR_ZERO = Pattern.compile("layout\\(location\\s*=\\s*0\\)\\s+out\\s+vec4\\s+oculus_FragColor0\\s*;");
    private static final Pattern MAIN = Pattern.compile("\\bvoid\\s+main\\s*\\(\\s*(?:void\\s*)?\\)");
    private NativeAlphaTest() { }

    static String adapt(String source) throws ShaderCompatibilityException {
        if (!COLOR_ZERO.matcher(source).find()) return source;
        var main = MAIN.matcher(source);
        if (!main.find()) throw new ShaderCompatibilityException("Native fragment shader has no main function");
        return main.replaceFirst("void oculusNativeFragmentMain()") + """

            void main() {
                oculusNativeFragmentMain();
            #ifdef OCULUS_NATIVE_ALPHA_MODE
            #if OCULUS_NATIVE_ALPHA_MODE == 0
                discard;
            #elif OCULUS_NATIVE_ALPHA_MODE == 1
                if (!(oculus_FragColor0.a < OCULUS_NATIVE_ALPHA_REF)) discard;
            #elif OCULUS_NATIVE_ALPHA_MODE == 2
                if (oculus_FragColor0.a != OCULUS_NATIVE_ALPHA_REF) discard;
            #elif OCULUS_NATIVE_ALPHA_MODE == 3
                if (!(oculus_FragColor0.a <= OCULUS_NATIVE_ALPHA_REF)) discard;
            #elif OCULUS_NATIVE_ALPHA_MODE == 4
                if (!(oculus_FragColor0.a > OCULUS_NATIVE_ALPHA_REF)) discard;
            #elif OCULUS_NATIVE_ALPHA_MODE == 5
                if (oculus_FragColor0.a == OCULUS_NATIVE_ALPHA_REF) discard;
            #elif OCULUS_NATIVE_ALPHA_MODE == 6
                if (!(oculus_FragColor0.a >= OCULUS_NATIVE_ALPHA_REF)) discard;
            #elif OCULUS_NATIVE_ALPHA_MODE != 7
            #error Invalid Oculus alpha-test mode
            #endif
            #else
            #ifdef ALPHA_CUTOUT
                if (oculus_FragColor0.a < ALPHA_CUTOUT) discard;
            #endif
            #endif
            }
            """;
    }
}
