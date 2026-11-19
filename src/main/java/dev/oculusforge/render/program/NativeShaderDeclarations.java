package dev.oculusforge.render.program;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Expands plain GLSL declaration lists before the native binding validators run. */
final class NativeShaderDeclarations {
    private static final String IDENTIFIER = "[A-Za-z_][A-Za-z0-9_]*";
    private static final Pattern DECLARATION = Pattern.compile(
        "(?m)^[\\t ]*(uniform|varying|attribute)[\\t ]+(" + IDENTIFIER + ")[\\t ]+([^;{}]+);");
    private static final Pattern NAMES = Pattern.compile(IDENTIFIER + "(?:\\s*,\\s*" + IDENTIFIER + ")+");

    private NativeShaderDeclarations() { }

    /** Expects preprocessed, comment-free source. Unknown bindings remain for later rejection. */
    static String expandLists(String source) throws ShaderCompatibilityException {
        Matcher declarations = DECLARATION.matcher(source);
        StringBuilder result = new StringBuilder();
        while (declarations.find()) {
            String names = declarations.group(3).trim();
            if (!names.contains(",")) continue;
            if (!NAMES.matcher(names).matches())
                throw new ShaderCompatibilityException("Native " + declarations.group(1)
                    + " declaration lists require plain names; arrays and initializers are unsupported");
            StringBuilder expanded = new StringBuilder();
            for (String name : names.split("\\s*,\\s*")) {
                if (!expanded.isEmpty()) expanded.append('\n');
                expanded.append(declarations.group(1)).append(' ').append(declarations.group(2))
                    .append(' ').append(name).append(';');
            }
            declarations.appendReplacement(result, Matcher.quoteReplacement(expanded.toString()));
        }
        declarations.appendTail(result);
        return result.toString();
    }
}
