package dev.oculusforge.render.program;

import dev.oculusforge.render.world.HeldItemIds;
import dev.oculusforge.render.world.HeldItemKeys;
import dev.oculusforge.shaderpack.*;
import java.io.*;
import java.util.*;

/** Strict bounded item.properties subset. Conflicting selector assignments fail closed. */
public record ItemMaterialMap(Map<String, Integer> ids) {
    public static final ItemMaterialMap EMPTY = new ItemMaterialMap(Map.of());
    public static final int MAX_SOURCE = 1024 * 1024, MAX_SELECTORS = 16384;
    public ItemMaterialMap { ids = Map.copyOf(ids); }
    public int id(String key) { return key == null ? -1 : ids.getOrDefault(key, -1); }
    public HeldItemIds resolve(HeldItemKeys keys) { return new HeldItemIds(id(keys.mainHand()), id(keys.offHand())); }
    public static ItemMaterialMap load(PackFileSystem pack, Map<String, String> environment)
        throws IOException, ShaderCompatibilityException {
        var path = VirtualPath.parse("shaders/item.properties");
        return pack.exists(path) ? parse(pack.readUtf8(path), environment) : EMPTY;
    }
    public static ItemMaterialMap parse(String source, Map<String, String> environment)
        throws IOException, ShaderCompatibilityException {
        if (source.length() > MAX_SOURCE || source.indexOf('\0') >= 0)
            throw new ShaderCompatibilityException("item.properties exceeds bounds or contains NUL");
        var input = new StringBuilder();
        for (String line : source.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)) {
            String trimmed = line.stripLeading();
            if (trimmed.startsWith("#")) {
                String directive = trimmed.substring(1).stripLeading().split("\\s+", 2)[0];
                if (Set.of("if", "ifdef", "ifndef", "elif", "else", "endif", "error").contains(directive)) input.append(line);
                else if (Set.of("include", "define", "undef", "extension", "version", "pragma").contains(directive))
                    throw new ShaderCompatibilityException("Unsupported item.properties directive #" + directive);
            } else if (!trimmed.startsWith("!")) input.append(line);
            input.append('\n');
        }
        String processed = GlslPreprocessor.preprocess(input.toString(), dev.oculusforge.shaderpack.ShaderOptionOverrides.forProperties(environment));
        if (processed.length() > MAX_SOURCE * 2) throw new ShaderCompatibilityException("Expanded item.properties exceeds bounds");
        var entries = new LinkedHashMap<String, String>();
        var properties = new Properties() {
            @Override public synchronized Object put(Object key, Object value) {
                entries.put((String) key, (String) value); return super.put(key, value);
            }
        };
        try { properties.load(new StringReader(processed)); }
        catch (IllegalArgumentException invalid) { throw new ShaderCompatibilityException("Invalid item.properties escape sequence"); }
        var ids = new LinkedHashMap<String, Integer>();
        int selectors = 0;
        for (var entry : entries.entrySet()) {
            if (!entry.getKey().matches("item\\.(?:0|-?[1-9][0-9]{0,9})"))
                throw new ShaderCompatibilityException("Unsupported item.properties key " + entry.getKey());
            int id;
            try { id = Integer.parseInt(entry.getKey().substring(5)); }
            catch (NumberFormatException invalid) { throw new ShaderCompatibilityException("Item material ID is outside signed int range"); }
            if (entry.getValue().isBlank()) continue;
            for (String selector : entry.getValue().trim().split("\\s+")) {
                if (++selectors > MAX_SELECTORS) throw new ShaderCompatibilityException("Too many item material selectors");
                String key = selector.contains(":") ? selector : "minecraft:" + selector;
                if (!HeldItemKeys.valid(key) || selector.matches("[0-9]+"))
                    throw new ShaderCompatibilityException("Unsupported item/model selector " + selector);
                Integer previous = ids.putIfAbsent(key, id);
                if (previous != null && previous != id) throw new ShaderCompatibilityException("Conflicting item material IDs for " + key);
            }
        }
        return new ItemMaterialMap(ids);
    }
}
