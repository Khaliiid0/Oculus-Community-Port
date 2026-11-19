package dev.oculusforge.render.program;

/** Immutable clear metadata; expressions are preprocessed but never evaluated as Java code. */
public record ColorClearValue(float red, float green, float blue, float alpha) {
    public static final ColorClearValue BLACK = new ColorClearValue(0, 0, 0, 0);
    public static final ColorClearValue WHITE = new ColorClearValue(1, 1, 1, 1);
    private static final java.util.regex.Pattern NUMBER = java.util.regex.Pattern.compile(
        "[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?[fF]?");
    private static final java.util.regex.Pattern CONSTRUCTOR = java.util.regex.Pattern.compile("vec4\\s*\\(([^()]*)\\)");

    public ColorClearValue {
        if (!Float.isFinite(red) || !Float.isFinite(green) || !Float.isFinite(blue) || !Float.isFinite(alpha))
            throw new IllegalArgumentException("Clear colors must be finite");
    }

    public org.joml.Vector4fc vector(ColorBufferFormat format) {
        return new org.joml.Vector4f(red, green, blue, format.writeMask() == 7 ? 1 : alpha);
    }

    public static ColorClearValue parse(String value) throws ShaderCompatibilityException {
        var constructor = CONSTRUCTOR.matcher(value.trim());
        if (!constructor.matches()) throw new ShaderCompatibilityException("Expected a literal vec4 clear color");
        String[] parts = constructor.group(1).split(",", -1);
        if (parts.length != 1 && parts.length != 4) throw new ShaderCompatibilityException("Clear color needs one or four components");
        float[] components = new float[4];
        for (int i = 0; i < 4; i++) {
            String token = parts[parts.length == 1 ? 0 : i].trim();
            if (!NUMBER.matcher(token).matches()) throw new ShaderCompatibilityException("Unsupported clear-color component '" + token + "'");
            components[i] = Float.parseFloat(token);
            if (!Float.isFinite(components[i])) throw new ShaderCompatibilityException("Clear colors must be finite");
        }
        return new ColorClearValue(components[0], components[1], components[2], components[3]);
    }
}
