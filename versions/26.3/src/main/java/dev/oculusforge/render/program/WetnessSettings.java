package dev.oculusforge.render.program;

import java.util.regex.Pattern;

/** Iris runtime convention: wetting/drying half-lives are in deciseconds. */
public record WetnessSettings(double wettingHalflife, double dryingHalflife) {
    public static final WetnessSettings DEFAULT = new WetnessSettings(600, 200);
    public WetnessSettings {
        if (!Double.isFinite(wettingHalflife) || wettingHalflife < 0
            || !Double.isFinite(dryingHalflife) || dryingHalflife < 0)
            throw new IllegalArgumentException("Invalid wetness half-life");
    }
    public double wettingSeconds() { return wettingHalflife * .1; }
    public double dryingSeconds() { return dryingHalflife * .1; }

    /** Only active preprocessed stages, not arbitrary inactive files, contribute. */
    static final class Collector {
        private static final Pattern DECLARATION = Pattern.compile(
            "\\bconst\\s+(\\w+)\\s+(wetnessHalflife|drynessHalflife)\\s*=\\s*([^;]+);");
        private static final Pattern NUMBER = Pattern.compile(
            "[+]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?[fF]?");
        private Double wetting, drying;
        void read(String source) throws ShaderCompatibilityException {
            var declarations = DECLARATION.matcher(PostProcessCapabilities.withoutComments(source));
            while (declarations.find()) {
                String name = declarations.group(2), literal = declarations.group(3).trim();
                if (!declarations.group(1).equals("float") || !NUMBER.matcher(literal).matches())
                    throw new ShaderCompatibilityException(name + " requires a nonnegative float literal");
                double value;
                try { value = Double.parseDouble(literal.replaceFirst("[fF]$", "")); }
                catch (NumberFormatException failure) { throw new ShaderCompatibilityException("Invalid " + name); }
                if (!Double.isFinite(value)) throw new ShaderCompatibilityException(name + " must be finite");
                Double previous = name.equals("wetnessHalflife") ? wetting : drying;
                if (previous != null && previous.doubleValue() != value)
                    throw new ShaderCompatibilityException("Conflicting " + name + " across active stages");
                if (name.equals("wetnessHalflife")) wetting = value;
                else drying = value;
            }
        }
        WetnessSettings settings() {
            return new WetnessSettings(wetting == null ? DEFAULT.wettingHalflife() : wetting,
                drying == null ? DEFAULT.dryingHalflife() : drying);
        }
    }
}
