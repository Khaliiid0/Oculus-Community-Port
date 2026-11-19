package dev.oculusforge.render.program;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Per-native-program legacy alpha tests. Missing settings retain the inherited layer default. */
public record TerrainAlphaSettings(Map<String, Test> programs) {
    public static final String MODE = "OCULUS_NATIVE_ALPHA_MODE", REF = "OCULUS_NATIVE_ALPHA_REF";
    public static final TerrainAlphaSettings EMPTY = new TerrainAlphaSettings(Map.of());
    public TerrainAlphaSettings { programs = Map.copyOf(programs); }
    public enum Function { NEVER, LESS, EQUAL, LEQUAL, GREATER, NOTEQUAL, GEQUAL, ALWAYS }
    public record Test(Function function, float reference) {
        public Test {
            java.util.Objects.requireNonNull(function);
            if (!Float.isFinite(reference) || reference < 0 || reference > 1)
                throw new IllegalArgumentException("Alpha reference must be finite and within [0,1]");
            if (reference == 0) reference = 0; // Canonical positive zero for descriptor identities.
        }
        public boolean accepts(float alpha) {
            return switch (function) {
                case NEVER -> false; case LESS -> alpha < reference; case EQUAL -> alpha == reference;
                case LEQUAL -> alpha <= reference; case GREATER -> alpha > reference; case NOTEQUAL -> alpha != reference;
                case GEQUAL -> alpha >= reference; case ALWAYS -> true;
            };
        }
        public Map<String, String> defines() { return Map.of(MODE, Integer.toString(function.ordinal()), REF, Float.toString(reference)); }
    }
    public Optional<Test> program(String name) { return Optional.ofNullable(programs.get(name)); }
    public String signature() { return new java.util.TreeMap<>(programs).toString(); }
    public Map<String,String> defines(String name, Float fallback) {
        Test test = programs.get(name);
        return test != null ? test.defines() : fallback == null ? Map.of() : Map.of("ALPHA_CUTOUT", Float.toString(fallback));
    }

    public void apply(RenderPipeline.Builder builder, String name, Float fallback) {
        Test test = programs.get(name);
        if (test != null) {
            builder.withShaderDefine(MODE, test.function().ordinal());
            builder.withShaderDefine(REF, test.reference());
        } else if (fallback != null) builder.withShaderDefine("ALPHA_CUTOUT", fallback);
    }

    static TerrainAlphaSettings parse(String source, Map<String,String> environment, String root, Set<String> names)
        throws IOException, ShaderCompatibilityException {
        var selected = TerrainBlendSettings.properties(source, environment, root, names, "alphaTest.");
        var result = new java.util.HashMap<String,Test>();
        for (String name : names) {
            if (name.isEmpty()) continue;
            for (String prefix : TerrainBlendSettings.prefixes(name, root, "alphaTest.")) {
                for (String key : selected.stringPropertyNames()) if (key.startsWith(prefix + "."))
                    throw new ShaderCompatibilityException("Unsupported terrain alpha-test suffix: " + key);
                if (selected.containsKey(prefix)) result.put(name, parseTest(selected.getProperty(prefix)));
            }
        }
        return new TerrainAlphaSettings(result);
    }
    private static Test parseTest(String value) throws ShaderCompatibilityException {
        String text = value.trim();
        if (text.equals("off")) return new Test(Function.ALWAYS, 0);
        String[] parts = text.split("\\s+");
        try {
            if (parts.length != 2) throw new IllegalArgumentException("Expected function and reference");
            return new Test(Function.valueOf(parts[0]), Float.parseFloat(parts[1]));
        } catch (IllegalArgumentException invalid) {
            throw new ShaderCompatibilityException("Invalid terrain alpha test: " + text);
        }
    }
}
