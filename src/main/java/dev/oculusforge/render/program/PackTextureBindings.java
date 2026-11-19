package dev.oculusforge.render.program;

import dev.oculusforge.shaderpack.PackFileSystem;
import dev.oculusforge.shaderpack.VirtualPath;
import java.io.IOException;
import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/** A deliberately bounded PNG subset of Iris/OptiFine custom texture directives. */
public final class PackTextureBindings {
    private final Map<String, String> global;
    private final Map<String, String> deferred;
    private final Map<String, String> composite;

    private PackTextureBindings(Map<String, String> global, Map<String, String> deferred, Map<String, String> composite) {
        this.global = Map.copyOf(global); this.deferred = Map.copyOf(deferred); this.composite = Map.copyOf(composite);
    }

    public static PackTextureBindings read(PackFileSystem pack) throws IOException, ShaderCompatibilityException {
        Map<String, String> global = new LinkedHashMap<>(), deferred = new LinkedHashMap<>(), composite = new LinkedHashMap<>();
        VirtualPath propertiesPath = VirtualPath.parse("shaders/shaders.properties");
        if (!pack.exists(propertiesPath)) return new PackTextureBindings(global, deferred, composite);
        String source = unconditionalProperties(pack.readUtf8(propertiesPath));
        Properties properties = new Properties() {
            private static final long serialVersionUID = 1L;
            @Override public synchronized Object put(Object key, Object value) {
                if (containsKey(key)) throw new IllegalArgumentException("Duplicate property " + key);
                return super.put(key, value);
            }
        };
        try { properties.load(new StringReader(source)); }
        catch (IllegalArgumentException exception) { throw new ShaderCompatibilityException("Invalid shaders.properties: " + exception.getMessage()); }
        int textures = 0;
        for (String key : properties.stringPropertyNames().stream().sorted().toList()) {
            if (key.equals("sliders") || key.equals("screen") || key.startsWith("screen.") || key.startsWith("profile.")) continue;
            String sampler;
            Map<String, String> destination;
            boolean alias = false;
            if (key.equals("texture.noise")) { sampler = "noisetex"; destination = global; }
            else if (key.startsWith("customTexture.")) {
                sampler = key.substring("customTexture.".length());
                if (!sampler.matches("[A-Za-z_][A-Za-z0-9_]*") || sampler.startsWith("gl_")
                    || sampler.startsWith("oculus_") || canonicalColor(sampler) != null
                    || sampler.matches("(?:noisetex|[gs]?depth.*|shadow.*|watershadow|tex|texture|lightmap|normals|specular)")) {
                    throw new ShaderCompatibilityException("Reserved or invalid custom sampler name: " + sampler);
                }
                destination = global;
            } else if (key.startsWith("texture.deferred.") || key.startsWith("texture.composite.")) {
                boolean isDeferred = key.startsWith("texture.deferred.");
                sampler = key.substring((isDeferred ? "texture.deferred." : "texture.composite.").length());
                if (canonicalColor(sampler) == null) throw new ShaderCompatibilityException("Only color-buffer PNG overrides are supported: " + key);
                destination = isDeferred ? deferred : composite;
                alias = true;
            } else continue; // Renderer/profile directives are owned by their dedicated parsers.
            if (++textures > 32) throw new ShaderCompatibilityException("At most 32 custom texture declarations are supported");
            String value = properties.getProperty(key).trim();
            if (!value.endsWith(".png") || value.chars().anyMatch(Character::isWhitespace)) {
                throw new ShaderCompatibilityException("Only pack-local PNG paths are supported for " + key + "; raw/resource-pack textures are not implemented");
            }
            VirtualPath relative = VirtualPath.parse(value);
            VirtualPath path = VirtualPath.parse("shaders/" + relative);
            if (alias) {
                String canonical = canonicalColor(sampler);
                if (destination.putIfAbsent(canonical, path.toString()) != null) {
                    throw new ShaderCompatibilityException("Conflicting aliases for custom texture " + sampler);
                }
            } else if (destination.putIfAbsent(sampler, path.toString()) != null) {
                throw new ShaderCompatibilityException("Conflicting custom texture " + sampler);
            }
        }
        return new PackTextureBindings(global, deferred, composite);
    }

    /** Conditional property blocks depend on the pack option evaluator; ignore them conservatively here. */
    private static String unconditionalProperties(String source) throws ShaderCompatibilityException {
        StringBuilder result = new StringBuilder(source.length());
        int depth = 0;
        for (String line : source.split("\\R", -1)) {
            String trimmed = line.trim();
            if (trimmed.matches("#\\s*(?:if|ifdef|ifndef)\\b.*")) { depth++; continue; }
            if (trimmed.matches("#\\s*endif\\b.*")) {
                if (depth == 0) throw new ShaderCompatibilityException("Unbalanced #endif in shaders.properties");
                depth--;
                continue;
            }
            if (trimmed.matches("#\\s*(?:elif|else)\\b.*")) {
                if (depth == 0) throw new ShaderCompatibilityException("Unbalanced conditional in shaders.properties");
                continue;
            }
            if (depth == 0) result.append(line).append('\n');
        }
        if (depth != 0) throw new ShaderCompatibilityException("Unclosed conditional in shaders.properties");
        return result.toString();
    }

    public Map<String, String> forStage(String stage) {
        Map<String, String> bindings = new LinkedHashMap<>(global);
        Map<String, String> overrides = stage.startsWith("deferred") ? deferred : composite;
        for (int index = 0; index < 16; index++) {
            String path = overrides.get("colortex" + index);
            if (path == null) continue;
            bindings.put("colortex" + index, path);
            for (String alias : Set.of("gcolor", "gdepth", "gnormal", "composite", "gaux1", "gaux2", "gaux3", "gaux4", "insampler")) {
                if (("colortex" + index).equals(canonicalColor(alias))) bindings.put(alias, path);
            }
        }
        return Map.copyOf(bindings);
    }

    private static String canonicalColor(String name) {
        if (name.matches("colortex(?:[0-9]|1[0-5])")) return name;
        return switch (name) {
            case "gcolor", "insampler" -> "colortex0";
            case "gdepth" -> "colortex1";
            case "gnormal" -> "colortex2";
            case "composite" -> "colortex3";
            case "gaux1" -> "colortex4";
            case "gaux2" -> "colortex5";
            case "gaux3" -> "colortex6";
            case "gaux4" -> "colortex7";
            default -> null;
        };
    }
}
