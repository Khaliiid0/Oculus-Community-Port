package dev.oculusforge.render.program;

import dev.oculusforge.shaderpack.GlslPreprocessor;
import dev.oculusforge.shaderpack.PackFileSystem;
import dev.oculusforge.shaderpack.VirtualPath;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/** Bounded, ordered block.properties subset. Never substitutes registry IDs for pack IDs. */
public record BlockMaterialMap(List<Rule> rules, Map<String, RenderLayer> layers) {
    public static final BlockMaterialMap EMPTY = new BlockMaterialMap(List.of());
    public static final int MAX_SOURCE = 1024 * 1024, MAX_RULES = 16384, MAX_ID = 65535;

    public enum RenderLayer { SOLID, CUTOUT, TRANSLUCENT }

    public BlockMaterialMap(List<Rule> rules) { this(rules, Map.of()); }
    public BlockMaterialMap { rules = List.copyOf(rules); layers = Map.copyOf(layers); }

    public static BlockMaterialMap load(PackFileSystem pack, Map<String, String> environment)
        throws IOException, ShaderCompatibilityException {
        return load(pack, environment, true);
    }

    public static BlockMaterialMap load(PackFileSystem pack, Map<String, String> environment, boolean required)
        throws IOException, ShaderCompatibilityException {
        var path = VirtualPath.parse("shaders/block.properties");
        if (!pack.exists(path)) {
            if (!required) return EMPTY;
            throw new ShaderCompatibilityException("Native material IDs require explicit block.properties; legacy default IDs are not implemented");
        }
        return parse(pack.readUtf8(path), environment);
    }

    public static BlockMaterialMap parse(String source, Map<String, String> environment)
        throws IOException, ShaderCompatibilityException {
        if (source.length() > MAX_SOURCE || source.indexOf('\0') >= 0)
            throw new ShaderCompatibilityException("block.properties exceeds bounds or contains NUL");
        StringBuilder input = new StringBuilder();
        for (String line : source.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)) {
            String trimmed = line.stripLeading();
            if (trimmed.startsWith("#")) {
                String directive = trimmed.substring(1).stripLeading().split("\\s+", 2)[0];
                if (Set.of("if", "ifdef", "ifndef", "elif", "else", "endif", "error").contains(directive)) input.append(line);
                else if (Set.of("include", "define", "undef", "extension", "version", "pragma").contains(directive))
                    throw new ShaderCompatibilityException("Unsupported block.properties directive #" + directive);
            } else if (!trimmed.startsWith("!")) input.append(line);
            input.append('\n');
        }
        String processed = GlslPreprocessor.preprocess(input.toString(), dev.oculusforge.shaderpack.ShaderOptionOverrides.forProperties(environment));
        if (processed.length() > MAX_SOURCE * 2) throw new ShaderCompatibilityException("Expanded block.properties exceeds bounds");
        Map<String, String> entries = new LinkedHashMap<>();
        Properties properties = new Properties() {
            @Override public synchronized Object put(Object key, Object value) {
                entries.put((String) key, (String) value);
                return super.put(key, value);
            }
        };
        try { properties.load(new StringReader(processed)); }
        catch (IllegalArgumentException malformed) { throw new ShaderCompatibilityException("Invalid block.properties escape sequence"); }
        List<Rule> rules = new ArrayList<>();
        Map<String, RenderLayer> layers = new LinkedHashMap<>();
        int selectors = 0;
        for (var entry : entries.entrySet()) {
            if (entry.getKey().startsWith("layer.")) {
                RenderLayer layer = switch (entry.getKey()) {
                    case "layer.solid" -> RenderLayer.SOLID;
                    // Minecraft 26.2 has one CUTOUT mesh/sampler, as does current Iris.
                    case "layer.cutout", "layer.cutout_mipped" -> RenderLayer.CUTOUT;
                    case "layer.translucent" -> RenderLayer.TRANSLUCENT;
                    default -> throw new ShaderCompatibilityException("Unsupported block render layer " + entry.getKey());
                };
                if (entry.getValue().isBlank()) continue;
                for (String selector : entry.getValue().trim().split("\\s+")) {
                    if (++selectors > MAX_RULES) throw new ShaderCompatibilityException("Too many block material/layer selectors");
                    Rule rule = parseRule(0, selector);
                    // Layer overrides identify blocks, not material-state predicates or tags.
                    if (!rule.properties().isEmpty()) throw new ShaderCompatibilityException("Block layer overrides do not support state filters: " + selector);
                    RenderLayer previous = layers.putIfAbsent(rule.block(), layer);
                    if (previous != null && previous != layer)
                        throw new ShaderCompatibilityException("Conflicting block layer overrides for " + rule.block());
                }
                continue;
            }
            if (!entry.getKey().matches("block\\.[0-9]{1,5}"))
                throw new ShaderCompatibilityException("Unsupported block.properties key " + entry.getKey() + " (legacy syntax is not implemented)");
            int id = Integer.parseInt(entry.getKey().substring(6));
            if (id > MAX_ID) throw new ShaderCompatibilityException("Material ID exceeds " + MAX_ID);
            if (entry.getValue().isBlank()) continue;
            for (String selector : entry.getValue().trim().split("\\s+")) {
                if (++selectors > MAX_RULES) throw new ShaderCompatibilityException("Too many block material/layer selectors");
                rules.add(parseRule(id, selector));
            }
        }
        return new BlockMaterialMap(rules, layers);
    }

    private static Rule parseRule(int id, String selector) throws ShaderCompatibilityException {
        if (selector.startsWith("%") || selector.startsWith("#"))
            throw new ShaderCompatibilityException("Material tag selectors need synchronized tag binding and are not yet supported");
        String[] parts = selector.split(":", -1);
        int start = parts.length > 1 && !parts[1].contains("=") ? 2 : 1;
        String block = start == 2 ? parts[0] + ":" + parts[1] : "minecraft:" + parts[0];
        if (!block.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || parts[0].matches("[0-9]+") || parts.length > start + 16)
            throw new ShaderCompatibilityException("Invalid or legacy block selector " + selector);
        Map<String, Set<String>> predicates = new LinkedHashMap<>();
        for (int i = start; i < parts.length; i++) {
            String[] pair = parts[i].split("=", -1);
            if (pair.length != 2 || !pair[0].matches("[a-z0-9_]+") || !pair[1].matches("[a-z0-9_-]+(?:,[a-z0-9_-]+)*"))
                throw new ShaderCompatibilityException("Invalid material property filter " + parts[i]);
            if (predicates.putIfAbsent(pair[0], new LinkedHashSet<>(List.of(pair[1].split(",")))) != null)
                throw new ShaderCompatibilityException("Repeated material property filter " + pair[0]);
        }
        return new Rule(id, block, predicates);
    }

    public record Rule(int materialId, String block, Map<String, Set<String>> properties) {
        public Rule {
            if (materialId < 0 || materialId > MAX_ID) throw new IllegalArgumentException("Material ID out of range");
            Map<String, Set<String>> copy = new LinkedHashMap<>();
            properties.forEach((key, values) -> copy.put(key, Set.copyOf(values)));
            properties = Map.copyOf(copy);
        }

        public boolean matches(Map<String, String> state) {
            return properties.entrySet().stream().allMatch(e -> state.containsKey(e.getKey()) && e.getValue().contains(state.get(e.getKey())));
        }
    }
}
