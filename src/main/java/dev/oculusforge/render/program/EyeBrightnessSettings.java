package dev.oculusforge.render.program;

import java.util.regex.Pattern;

/** Collects one unambiguous half-life from the active, preprocessed shader stages. */
final class EyeBrightnessSettings {
    private static final Pattern DECLARATION = Pattern.compile(
        "\\bconst\\s+float\\s+eyeBrightnessHalflife\\s*=\\s*([^;]+);");
    private static final Pattern NUMBER = Pattern.compile(
        "[+]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?[fF]?");
    private Double value;

    void read(String source) throws ShaderCompatibilityException {
        var matcher = DECLARATION.matcher(PostProcessCapabilities.withoutComments(source));
        while (matcher.find()) {
            String literal = matcher.group(1).trim();
            if (!NUMBER.matcher(literal).matches())
                throw new ShaderCompatibilityException("eyeBrightnessHalflife requires a nonnegative numeric literal");
            double parsed = Double.parseDouble(literal.replaceFirst("[fF]$", ""));
            if (!Double.isFinite(parsed))
                throw new ShaderCompatibilityException("eyeBrightnessHalflife must be finite");
            if (value != null && value.doubleValue() != parsed)
                throw new ShaderCompatibilityException("Conflicting eyeBrightnessHalflife values across active shader stages");
            value = parsed;
        }
    }

    double ticks() { return value == null ? EyeBrightnessSmoother.DEFAULT_HALF_LIFE_TICKS : value; }
}
