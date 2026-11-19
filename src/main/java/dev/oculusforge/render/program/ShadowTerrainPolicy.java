package dev.oculusforge.render.program;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/** CPU-side ordered terrain shadow work, with no framebuffer clears between stages. */
public record ShadowTerrainPolicy(boolean opaque, boolean translucent) {
    public static final ShadowTerrainPolicy DEFAULT = new ShadowTerrainPolicy(true, true);
    public enum Stage { OPAQUE, SNAPSHOT, TRANSLUCENT }
    private static final List<Stage> BOTH = List.of(Stage.OPAQUE, Stage.SNAPSHOT, Stage.TRANSLUCENT);
    private static final List<Stage> OPAQUE_ONLY = List.of(Stage.OPAQUE, Stage.SNAPSHOT);
    private static final List<Stage> TRANSLUCENT_ONLY = List.of(Stage.SNAPSHOT, Stage.TRANSLUCENT);
    private static final List<Stage> EMPTY = List.of(Stage.SNAPSHOT);

    public List<Stage> stages() {
        return opaque ? (translucent ? BOTH : OPAQUE_ONLY) : (translucent ? TRANSLUCENT_ONLY : EMPTY);
    }

    public static ShadowTerrainPolicy parse(String source, Map<String, String> environment)
        throws IOException, ShaderCompatibilityException {
        String selected;
        try {
            // Pack properties may define unrelated options. Do not reject those, but
            // never guess locally redefined macros when they govern shadow flags.
            var known = new java.util.HashMap<>(environment);
            var macros = java.util.regex.Pattern.compile("(?m)^[\\t ]*#[\\t ]*(?:define|undef)\\s+([A-Za-z_]\\w*)[^\\r\\n]*").matcher(source);
            var filtered = new StringBuilder();
            while (macros.find()) {
                if (macros.group().stripTrailing().endsWith("\\"))
                    throw new IllegalArgumentException("Continued property macros are unsupported");
                known.remove(macros.group(1));
                macros.appendReplacement(filtered, "");
            }
            macros.appendTail(filtered);
            selected = CustomUniformProperties.select(filtered.toString(), known);
        }
        catch (IllegalArgumentException invalid) {
            throw new ShaderCompatibilityException("Could not select shadow terrain properties: " + invalid.getMessage());
        }
        boolean unresolved = false;
        Properties properties = new Properties();
        for (String line : selected.split("\n")) {
            if (line.equals("#if OCULUS_UNRESOLVED_PROPERTY")) { unresolved = true; continue; }
            if (line.equals("#endif")) { unresolved = false; continue; }
            Properties entry = new Properties();
            entry.load(new StringReader(line));
            if (unresolved && (entry.containsKey("shadowTerrain") || entry.containsKey("shadowTranslucent")))
                throw new ShaderCompatibilityException("Shadow terrain properties depend on an unresolved shader option");
            properties.putAll(entry);
        }
        return new ShadowTerrainPolicy(flag(properties, "shadowTerrain"), flag(properties, "shadowTranslucent"));
    }

    private static boolean flag(Properties properties, String name) throws ShaderCompatibilityException {
        String value = properties.getProperty(name, "true").trim();
        if (!value.equals("true") && !value.equals("false"))
            throw new ShaderCompatibilityException("Unsupported " + name + " value: " + value);
        return Boolean.parseBoolean(value);
    }
}
