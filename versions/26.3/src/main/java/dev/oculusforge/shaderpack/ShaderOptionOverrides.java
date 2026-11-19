package dev.oculusforge.shaderpack;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

/** Explicit, bounded #define options. Never edits a ZIP or injects arbitrary GLSL. */
public final class ShaderOptionOverrides {
    public static final int MAX_SETTINGS_BYTES = 16 * 1024;
    public static final int MAX_OPTIONS = 32;
    public static final int MAX_SCAN_BYTES = 16 * 1024 * 1024;
    public static final int MAX_ALLOWED_VALUES = 512;
    private static final Pattern NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,95}");
    private static final Pattern TOKEN = Pattern.compile("(?:[A-Za-z_][A-Za-z0-9_]*|[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?[fFuU]?|0[xX][0-9a-fA-F]+[uU]?)");
    private static final Pattern DEFINE = Pattern.compile("^([\\t ]*)(//[\\t ]*)?#[\\t ]*define[\\t ]+([A-Za-z_][A-Za-z0-9_]*)(?:[\\t ]+([^\\s/]+))?[\\t ]*(?://(.*))?$");
    private static final Pattern VALUES = Pattern.compile("^\\s*\\[([^]]+)].*$");
    private static final Pattern REFERENCE = Pattern.compile("(?m)^[\\t ]*#[\\t ]*(?:ifdef|ifndef)[\\t ]+([A-Za-z_][A-Za-z0-9_]*)[\\t ]*(?://[^\\r\\n]*)?$");
    private static final Set<String> EXTENSIONS = Set.of("glsl", "vsh", "fsh", "gsh", "tcs", "tes", "csh");
    private ShaderOptionOverrides() {}

    public static Map<String,String> validateValues(Map<String,String> values) throws ShaderPackException {
        Objects.requireNonNull(values);
        if (values.size() > MAX_OPTIONS) throw invalid("Too many selected shader options");
        for (var entry : values.entrySet()) {
            String name = entry.getKey(), value = entry.getValue();
            if (name == null || !NAME.matcher(name).matches() || reserved(name))
                throw invalid("Invalid or reserved shader option name");
            if (value == null || value.length() > 64 || !TOKEN.matcher(value).matches())
                throw invalid("Invalid shader option value for " + name);
        }
        return Map.copyOf(values);
    }

    private static boolean reserved(String name) {
        return name.equals("IS_IRIS") || name.equals("DISTANT_HORIZONS") || name.startsWith("IRIS_")
            || name.startsWith("MC_") || name.startsWith("OCULUS_") || name.startsWith("GL_")
            || name.startsWith("gl_") || name.startsWith("__");
    }

    /** Iris-style sibling filename: PACK.zip.txt (or DIRECTORY.txt). Read only. */
    public static Map<String,String> readSidecar(ShaderPackDescriptor descriptor) throws IOException {
        var pack = descriptor.path().toAbsolutePath().normalize();
        var sidecar = pack.resolveSibling(pack.getFileName() + ".txt");
        BasicFileAttributes attributes;
        try { attributes = Files.readAttributes(sidecar, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS); }
        catch (NoSuchFileException absent) { return Map.of(); }
        if (!attributes.isRegularFile() || attributes.isSymbolicLink() || attributes.isOther())
            throw invalid("Shader option settings must be a regular non-linked file");
        if (attributes.size() > MAX_SETTINGS_BYTES) throw invalid("Shader option settings exceed 16 KiB");
        try (var input = Files.newInputStream(sidecar, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            return parse(input.readNBytes(MAX_SETTINGS_BYTES + 1));
        }
    }

    public static Map<String,String> parse(byte[] encoded) throws ShaderPackException {
        if (encoded.length > MAX_SETTINGS_BYTES) throw invalid("Shader option settings exceed 16 KiB");
        var properties = new Properties() {
            private static final long serialVersionUID = 1L;
            @Override public synchronized Object put(Object key, Object value) {
                if (containsKey(key)) throw new IllegalArgumentException("Duplicate shader option");
                if (size() >= MAX_OPTIONS) throw new IllegalArgumentException("Too many shader options");
                return super.put(key, value);
            }
        };
        try { properties.load(new ByteArrayInputStream(encoded)); }
        catch (IOException | IllegalArgumentException failure) { throw invalid("Invalid or duplicate shader option settings"); }
        var result = new LinkedHashMap<String,String>();
        for (String key : properties.stringPropertyNames()) result.put(key, properties.getProperty(key).trim());
        return validateValues(result);
    }

    private record Declaration(int line, String prefix, String name, String value, List<String> allowed, boolean isSwitch) {}

    public record Applied(PackFileSystem pack, Set<String> switches) {
        public Applied { switches=Set.copyOf(switches); }
    }
    /** The returned view borrows its owner. The caller must close the original pack. */
    public static Applied apply(PackFileSystem pack, ShaderPackLimits limits, Map<String,String> selected) throws IOException {
        selected = validateValues(selected);
        if (selected.isEmpty()) return new Applied(pack,Set.of());
        var sources = new LinkedHashMap<VirtualPath,String>();
        var declarations = new LinkedHashMap<VirtualPath,List<Declaration>>();
        var references = new HashSet<String>();
        var catalog = new HashMap<String,Declaration>();
        var conflicts = new HashSet<String>();
        long bytes = 0;
        for (var path : pack.files().stream().sorted().toList()) {
            String name = path.toString();
            int dot = name.lastIndexOf('.');
            if (!path.startsWith("shaders") || dot < 0 || !EXTENSIONS.contains(name.substring(dot + 1))) continue;
            byte[] raw = pack.read(path);
            bytes += raw.length;
            if (bytes > Math.min(MAX_SCAN_BYTES, limits.maxExpandedSourceBytes())) throw invalid("Shader option discovery exceeds source budget");
            String source = decode(raw, path);
            sources.put(path, source);
            String masked = maskBlocks(source);
            var refs = REFERENCE.matcher(masked);
            while (refs.find()) references.add(refs.group(1));
            String[] lines = source.split("\\r\\n|\\r|\\n", -1), clean = masked.split("\\r\\n|\\r|\\n", -1);
            var found = new ArrayList<Declaration>();
            for (int line = 0; line < lines.length; line++) {
                // Keep complex block-comment boundaries untouched, even if they mask as whitespace.
                if (lines[line].contains("/*") || lines[line].contains("*/") || !lines[line].equals(clean[line])) continue;
                var match = DEFINE.matcher(clean[line]);
                if (!match.matches()) continue;
                String option = match.group(3), rawValue = match.group(4);
                if (reserved(option)) continue;
                var allowed = new ArrayList<String>();
                String value;
                if (rawValue == null) {
                    value = Boolean.toString(match.group(2) == null);
                    allowed.addAll(List.of("false", "true"));
                } else {
                    if (match.group(2) != null || rawValue.length() > 64 || !TOKEN.matcher(rawValue).matches()) continue;
                    var list = VALUES.matcher(match.group(5) == null ? "" : match.group(5));
                    if (!list.matches()) continue;
                    for (String item : list.group(1).trim().split("\\s+")) {
                        if (item.length() > 64 || !TOKEN.matcher(item).matches() || allowed.size() >= MAX_ALLOWED_VALUES)
                            throw invalid("Invalid shader option allowed-value list for " + option);
                        if (!allowed.contains(item)) allowed.add(item);
                    }
                    if (!allowed.contains(rawValue)) allowed.addFirst(rawValue);
                    value = rawValue;
                }
                var declaration = new Declaration(line, match.group(1), option, value, List.copyOf(allowed), rawValue == null);
                found.add(declaration);
                var previous = catalog.putIfAbsent(option, declaration);
                if (previous != null && (!previous.value().equals(value) || !previous.allowed().equals(allowed)
                    || previous.isSwitch() != declaration.isSwitch())) conflicts.add(option);
                if (catalog.size() > 4096) throw invalid("Too many discovered shader options");
            }
            declarations.put(path, found);
        }
        var switches=new HashSet<String>();
        for (var entry : selected.entrySet()) {
            var option = catalog.get(entry.getKey());
            if (option == null || conflicts.contains(entry.getKey()) || option.isSwitch() && !references.contains(entry.getKey()))
                throw invalid("Unknown, ambiguous or unsupported shader option: " + entry.getKey());
            if (!option.allowed().contains(entry.getValue())) throw invalid("Value is not declared for shader option " + entry.getKey());
            if(option.isSwitch())switches.add(entry.getKey());
        }
        var transformed = new HashMap<VirtualPath,byte[]>();
        for (var entry : sources.entrySet()) {
            // Normalizing line endings preserves source-map line numbers, including the final empty line.
            String[] lines = entry.getValue().split("\\r\\n|\\r|\\n", -1);
            for (var declaration : declarations.get(entry.getKey())) {
                String value = selected.get(declaration.name());
                if (value == null) continue;
                String original = lines[declaration.line()];
                int comment = original.indexOf("//", original.indexOf('#'));
                String suffix = comment < 0 ? "" : " " + original.substring(comment);
                lines[declaration.line()] = declaration.prefix() + (declaration.isSwitch() && value.equals("false") ? "//" : "")
                    + "#define " + declaration.name() + (declaration.isSwitch() ? "" : " " + value) + suffix;
            }
            byte[] encoded = String.join("\n", lines).getBytes(StandardCharsets.UTF_8);
            if (encoded.length > limits.maxFileBytes()) throw invalid("Shader option expansion exceeds file budget");
            transformed.put(entry.getKey(), encoded);
        }
        return new Applied(new PackFileSystem() {
            @Override public Set<VirtualPath> files() { return pack.files(); }
            @Override public byte[] read(VirtualPath path) throws IOException {
                byte[] changed = transformed.get(path);
                return changed == null ? pack.read(path) : changed.clone();
            }
            @Override public void close() { /* borrowed original pack; closed by prepare's owner */ }
        },switches);
    }

    private static String decode(byte[] bytes, VirtualPath path) throws ShaderPackException {
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString();
        } catch (java.nio.charset.CharacterCodingException invalid) {
            throw new ShaderPackException(ShaderPackException.Code.INVALID_TEXT_ENCODING, "Invalid option source encoding", path.toString(), invalid);
        }
    }

    private static String maskBlocks(String source) {
        char[] result = source.toCharArray();
        boolean block = false, line = false, quoted = false;
        for (int i = 0; i < result.length; i++) {
            char c = source.charAt(i), next = i + 1 < result.length ? source.charAt(i + 1) : 0;
            if (c == '\n' || c == '\r') { line = false; continue; }
            if (line) continue;
            if (block) {
                result[i] = ' ';
                if (c == '*' && next == '/') { result[++i] = ' '; block = false; }
            } else if (c == '"') quoted = !quoted;
            else if (quoted && c == '\\') i++;
            else if (!quoted && c == '/' && next == '/') line = true;
            else if (!quoted && c == '/' && next == '*') { result[i] = result[++i] = ' '; block = true; }
        }
        return new String(result);
    }

    /** GLSL sees only renderer macros; properties additionally see explicitly selected options. */
    private static final class Environment extends AbstractMap<String,String> {
        private final Map<String,String> base, selected, entries;
        private final Set<String> switches;
        private final boolean properties;
        private Environment(Map<String,String> base, Map<String,String> selected, Set<String> switches, boolean properties) {
            this.base = Map.copyOf(base); this.selected = Map.copyOf(selected); this.properties = properties;
            this.switches=Set.copyOf(switches);
            var entries = new LinkedHashMap<>(base);
            if (properties) selected.forEach((name,value) -> {
                if(!switches.contains(name))entries.put(name,value);
                else if(value.equals("true"))entries.put(name,"");
                else entries.remove(name);
            });
            this.entries = Map.copyOf(entries);
        }
        @Override public Set<Entry<String,String>> entrySet() { return entries.entrySet(); }
    }
    public static Map<String,String> environment(Map<String,String> base, Map<String,String> selected,Set<String> switches) {
        return selected.isEmpty() ? base : new Environment(base, selected, switches, false);
    }
    public static Map<String,String> forProperties(Map<String,String> environment) {
        return environment instanceof Environment context && !context.properties
            ? new Environment(context.base, context.selected, context.switches, true) : environment;
    }
    public static boolean knownAbsent(Map<String,String> environment, String name) {
        return environment instanceof Environment context && context.switches.contains(name) && "false".equals(context.selected.get(name));
    }
    private static ShaderPackException invalid(String message) {
        return new ShaderPackException(ShaderPackException.Code.PREPROCESSING_FAILED, message);
    }
}
