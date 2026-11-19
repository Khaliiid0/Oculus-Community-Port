package dev.oculusforge.render.program;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Immutable per-dimension formats collected from active, include-expanded program sources. */
public record ColorBufferFormats(Map<Integer, ColorBufferFormat> declared, Map<Integer, Boolean> clearFlags,
                                 Map<Integer, ColorClearValue> clearColors) {
    public static final ColorBufferFormats DEFAULT = new ColorBufferFormats(Map.of());
    private static final List<String> ALIASES = List.of("gcolor", "gdepth", "gnormal", "composite", "gaux1", "gaux2", "gaux3", "gaux4");
    private static final String BUFFER = "(colortex[0-9]+|gcolor|gdepth|gnormal|composite|gaux[1-4])";
    private static final Pattern DECLARATION = Pattern.compile("(?m)^[\\t ]*const[\\t ]+int[\\t ]+" + BUFFER + "Format[\\t ]*=[\\t ]*([^;\\r\\n]+);");
    private static final Pattern CLEAR = Pattern.compile("(?m)^[\\t ]*const[\\t ]+(bool|vec4)[\\t ]+" + BUFFER + "(ClearColor|Clear)[\\t ]*=[\\t ]*([^;\\r\\n]+);");

    public ColorBufferFormats(Map<Integer, ColorBufferFormat> declared) { this(declared, Map.of(), Map.of()); }

    public ColorBufferFormats {
        declared = Map.copyOf(declared);
        clearFlags = Map.copyOf(clearFlags);
        clearColors = Map.copyOf(clearColors);
        if (java.util.stream.Stream.of(declared, clearFlags, clearColors).flatMap(map -> map.keySet().stream())
            .anyMatch(index -> index < 0 || index >= ColorBufferPlan.MAX_BUFFERS)) {
            throw new IllegalArgumentException("Invalid color-buffer format index");
        }
    }

    public ColorBufferFormat get(int index) {
        return index == ColorBufferPlan.SCREEN ? ColorBufferFormat.RGBA8 : declared.getOrDefault(index, ColorBufferFormat.RGBA8);
    }

    public boolean shouldClear(int index) { return clearFlags.getOrDefault(index, true); }
    public ColorClearValue clearValue(int index) {
        return clearColors.getOrDefault(index, index == 1 ? ColorClearValue.WHITE : ColorClearValue.BLACK);
    }
    public static boolean hasDeclarations(String source) { return DECLARATION.matcher(source).find() || CLEAR.matcher(source).find(); }

    /** Format names such as RGBA16F are renderer metadata, not built-in GLSL constants. */
    static String stripFormatDeclarations(String commentFreeSource) throws ShaderCompatibilityException {
        var declarations = DECLARATION.matcher(commentFreeSource);
        String shaderCode = declarations.replaceAll("");
        declarations.reset();
        while (declarations.find()) {
            String name = declarations.group(1) + "Format";
            if (Pattern.compile("\\b" + Pattern.quote(name) + "\\b").matcher(shaderCode).find()) {
                throw new ShaderCompatibilityException("Shader expressions using format metadata " + name + " are not implemented");
            }
        }
        return shaderCode;
    }

    private static int bufferIndex(String name, String label) throws ShaderCompatibilityException {
        try { return name.startsWith("colortex") ? Integer.parseInt(name.substring(8)) : ALIASES.indexOf(name); }
        catch (NumberFormatException failure) { throw new ShaderCompatibilityException(label + ": invalid color-buffer index " + name); }
    }

    public static final class Builder {
        private final Map<Integer, ColorBufferFormat> formats = new LinkedHashMap<>();
        private final Map<Integer, Boolean> clearFlags = new LinkedHashMap<>();
        private final Map<Integer, ColorClearValue> clearColors = new LinkedHashMap<>();
        public Builder(ColorBufferFormats initial) {
            formats.putAll(initial.declared());
            clearFlags.putAll(initial.clearFlags());
            clearColors.putAll(initial.clearColors());
        }

        public void read(String preprocessedSource, String label) throws ShaderCompatibilityException {
            // Iris reads line-leading const directives even inside block comments.
            // Both BSL and Complementary intentionally put their format table there.
            // A //-disabled line is not line-leading const and therefore does not match.
            var matcher = DECLARATION.matcher(preprocessedSource);
            while (matcher.find()) {
                String name = matcher.group(1);
                int index = bufferIndex(name, label);
                // Packs can declare unused extension buffers (e.g. BSL's colortex16).
                // Those declarations do not grant support for sampling or drawing them.
                if (index >= ColorBufferPlan.MAX_BUFFERS) continue;
                try { formats.put(index, ColorBufferFormat.parse(matcher.group(2).trim())); }
                catch (ShaderCompatibilityException failure) {
                    throw new ShaderCompatibilityException(label + ": " + name + "Format: " + failure.getMessage());
                }
            }
            matcher = CLEAR.matcher(preprocessedSource);
            while (matcher.find()) {
                int index = bufferIndex(matcher.group(2), label);
                if (index >= ColorBufferPlan.MAX_BUFFERS) continue;
                String value = matcher.group(4).trim();
                if (matcher.group(1).equals("bool") && matcher.group(3).equals("Clear")) {
                    if (!value.equals("true") && !value.equals("false"))
                        throw new ShaderCompatibilityException(label + ": Clear requires a literal boolean");
                    clearFlags.put(index, Boolean.parseBoolean(value));
                } else if (matcher.group(1).equals("vec4") && matcher.group(3).equals("ClearColor")) {
                    clearColors.put(index, ColorClearValue.parse(value));
                } else throw new ShaderCompatibilityException(label + ": invalid clear directive type");
            }
        }

        public ColorBufferFormats build() { return new ColorBufferFormats(formats, clearFlags, clearColors); }
    }
}
