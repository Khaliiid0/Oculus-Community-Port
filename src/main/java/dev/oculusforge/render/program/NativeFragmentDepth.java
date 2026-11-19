package dev.oculusforge.render.program;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Legacy fragment depth and projection inputs. */
public final class NativeFragmentDepth {
    public static final String ZERO_TO_ONE_DEFINE = NativeProjection.ZERO_TO_ONE_DEFINE;

    private NativeFragmentDepth() {}

    static String adapt(String source) {
        StringBuilder helpers = new StringBuilder();
        if (Pattern.compile("\\bgl_FragCoord\\b").matcher(source).find()) {
            source = source.replaceAll("\\bgl_FragCoord\\b", "oculusNativeFragmentCoord()");
            helpers.append("""
                vec4 oculusNativeFragmentCoord() {
                    return vec4(gl_FragCoord.xy, 1.0 - gl_FragCoord.z, gl_FragCoord.w);
                }
                """);
        }
        var projection = NativeProjection.adaptExpressions(source, false, "Fragment");
        source = projection.source();
        helpers.append(projection.helpers());
        if (helpers.isEmpty()) return source;
        return source.replaceFirst("(?m)^#version[^\\n]*", Matcher.quoteReplacement("#version 330 core\n" + helpers));
    }
}
