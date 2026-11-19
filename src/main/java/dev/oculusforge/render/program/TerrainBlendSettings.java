package dev.oculusforge.render.program;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.platform.BlendFactor;
import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/** Immutable terrain-only blend overrides, indexed by logical colortex, never MRT location. */
public record TerrainBlendSettings(Map<String, Program> programs) {
    public static final TerrainBlendSettings EMPTY = new TerrainBlendSettings(Map.of());
    public TerrainBlendSettings { programs = Map.copyOf(programs); }

    /** Empty outer optional inherits; a present Mode with an empty blend disables blending. */
    public record Mode(Optional<BlendFunction> blend) {
        public Mode { java.util.Objects.requireNonNull(blend); }
    }
    public record Program(Optional<Mode> all, Map<Integer, Mode> buffers) {
        public static final Program INHERIT = new Program(Optional.empty(), Map.of());
        public Program {
            java.util.Objects.requireNonNull(all);
            buffers = Map.copyOf(buffers);
            if (buffers.keySet().stream().anyMatch(i -> i < 0 || i >= 16))
                throw new IllegalArgumentException("Invalid logical blend target");
        }
        public ColorTargetState target(int logical, boolean translucent, ColorBufferFormat format) {
            if (logical < 0 || logical >= 16) throw new IllegalArgumentException("Invalid logical blend target");
            Mode selected = buffers.get(logical);
            if (selected == null) selected = all.orElse(null);
            Optional<BlendFunction> blend = selected != null ? selected.blend()
                : translucent ? Optional.of(BlendFunction.TRANSLUCENT) : Optional.empty();
            return new ColorTargetState(blend, format.gpu(), format.writeMask());
        }
        public String signature() { return all + ":" + new TreeMap<>(buffers); }
    }
    public Program program(String name) { return programs.getOrDefault(name, Program.INHERIT); }

    static TerrainBlendSettings parse(String source, Map<String, String> environment, String root, Set<String> names)
        throws IOException, ShaderCompatibilityException {
        // Property-local macros must remain unknown, not reuse compiler environment values.
        var known = new HashMap<>(environment);
        var macros = Pattern.compile("(?m)^[\\t ]*#[\\t ]*(?:define|undef)\\s+([A-Za-z_]\\w*)[^\\r\\n]*").matcher(source);
        var filtered = new StringBuilder();
        while (macros.find()) {
            if (macros.group().stripTrailing().endsWith("\\"))
                throw new ShaderCompatibilityException("Continued terrain blend property macros are unsupported");
            known.remove(macros.group(1));
            if (macros.group(1).equals("DISTANT_HORIZONS")) known.remove("OCULUS_POST_PROCESSING");
            macros.appendReplacement(filtered, "");
        }
        macros.appendTail(filtered);
        String selected;
        try { selected = CustomUniformProperties.select(filtered.toString(), known); }
        catch (IllegalArgumentException invalid) {
            throw new ShaderCompatibilityException("Could not select terrain blend properties: " + invalid.getMessage());
        }
        Properties properties = new Properties();
        boolean unresolved = false;
        for (String line : selected.split("\n")) {
            if (line.equals("#if OCULUS_UNRESOLVED_PROPERTY")) { unresolved = true; continue; }
            if (line.equals("#endif")) { unresolved = false; continue; }
            Properties entry = new Properties();
            entry.load(new StringReader(line));
            for (String key : entry.stringPropertyNames()) {
                for (String name : names) if (!name.isEmpty() && relevant(key, name, root)) {
                    if (unresolved) throw new ShaderCompatibilityException("Terrain blend property depends on an unresolved shader option: " + key);
                    properties.setProperty(key, entry.getProperty(key));
                }
            }
        }
        Map<String, Program> result = new HashMap<>();
        for (String name : names) {
            if (name.isEmpty()) continue;
            Optional<Mode> all = Optional.empty();
            Map<Integer, Mode> buffers = new HashMap<>();
            for (String prefix : prefixes(name, root)) {
                if (properties.containsKey(prefix)) all = Optional.of(mode(properties.getProperty(prefix)));
                for (String key : properties.stringPropertyNames()) if (key.startsWith(prefix + ".")) {
                    String buffer = key.substring(prefix.length() + 1);
                    if (!buffer.matches("colortex(?:[0-9]|1[0-5])"))
                        throw new ShaderCompatibilityException("Unsupported terrain blend buffer: " + key);
                    buffers.put(Integer.parseInt(buffer.substring(8)), mode(properties.getProperty(key)));
                }
            }
            if (all.isPresent() || !buffers.isEmpty()) result.put(name, new Program(all, buffers));
        }
        return new TerrainBlendSettings(result);
    }
    private static java.util.List<String> prefixes(String name, String root) {
        String base = "blend." + name;
        return root.equals("shaders") ? java.util.List.of(base)
            : java.util.List.of(base, "blend." + root.substring("shaders/".length()) + "/" + name);
    }
    private static boolean relevant(String key, String name, String root) {
        return prefixes(name, root).stream().anyMatch(prefix -> key.equals(prefix) || key.startsWith(prefix + "."));
    }
    private static Mode mode(String value) throws ShaderCompatibilityException {
        String text = value.trim();
        if (text.equals("off")) return new Mode(Optional.empty());
        String[] parts = text.split("\\s+");
        if (parts.length != 4) throw new ShaderCompatibilityException("Terrain blend needs off or four factors");
        BlendFactor[] factors = new BlendFactor[4];
        for (int i = 0; i < 4; i++) {
            if (!Set.of("ZERO", "ONE", "SRC_COLOR", "ONE_MINUS_SRC_COLOR", "DST_COLOR", "ONE_MINUS_DST_COLOR",
                "SRC_ALPHA", "ONE_MINUS_SRC_ALPHA", "DST_ALPHA", "ONE_MINUS_DST_ALPHA", "SRC_ALPHA_SATURATE").contains(parts[i]))
                throw new ShaderCompatibilityException("Unsupported terrain blend factor: " + parts[i]);
            factors[i] = BlendFactor.valueOf(parts[i]);
            // OpenGL only permits saturation in the source-color factor slot.
            if (i != 0 && factors[i] == BlendFactor.SRC_ALPHA_SATURATE)
                throw new ShaderCompatibilityException("SRC_ALPHA_SATURATE requires the source-color factor slot");
        }
        return new Mode(Optional.of(new BlendFunction(factors[0], factors[1], factors[2], factors[3])));
    }
}
