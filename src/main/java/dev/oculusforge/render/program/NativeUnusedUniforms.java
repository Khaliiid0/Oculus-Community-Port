package dev.oculusforge.render.program;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/** Remove only unreferenced, plain declarations; never replace a missing input with a value. */
final class NativeUnusedUniforms {
    private static final Pattern DECLARATION = Pattern.compile("\\buniform\\s+(\\w+)\\s+(\\w+)\\s*([^;{}]*);");
    private static final Pattern PLAIN_TYPE = Pattern.compile("bool|int|uint|float|[biu]?vec[234]|mat[234](?:x[234])?|sampler2D(?:Shadow)?");
    private static final Pattern LITERAL_DEFAULT = Pattern.compile("\\s*=\\s*(?:true|false|[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?[fFuU]?)\\s*");
    private static final Pattern IDENTIFIER = Pattern.compile("\\b[A-Za-z_]\\w*\\b");
    private static final Set<String> TEXTURES = Set.of("texture", "tex", "gtexture", "lightmap", "noisetex",
        "depthtex0", "depthtex1", "depthtex2", "shadowcolor0", "shadowcolor1", "normals", "specular");

    private NativeUnusedUniforms() { }

    /** Validate declarations before removing helpers, then discard only newly unused inputs. */
    static String adaptReachable(String source) throws ShaderCompatibilityException {
        String validated = adapt(source);
        return adapt(GlslFunctionReachability.prune(validated));
    }

    static String adapt(String source) throws ShaderCompatibilityException {
        // This pass does not prune functions: references in even unused helpers are
        // conservative roots. Macros must have been expanded by the pack preprocessor.
        if (Pattern.compile("(?m)^[\\t ]*#(?!\\s*(?:version|extension|line)\\b)").matcher(source).find()) return source;
        var declarations = DECLARATION.matcher(source);
        String references = declarations.replaceAll("$3");
        var identifiers = new HashSet<String>();
        var tokens = IDENTIFIER.matcher(references);
        while (tokens.find()) identifiers.add(tokens.group());
        declarations.reset();
        var seen = new HashSet<String>();
        var result = new StringBuilder();
        while (declarations.find()) {
            String type = declarations.group(1), name = declarations.group(2), suffix = declarations.group(3);
            if (!seen.add(name)) throw new ShaderCompatibilityException("Duplicate native uniform " + name);
            if (seen.size() > 512) throw new ShaderCompatibilityException("Native shader exceeds 512 uniform declarations");
            String expected = NativeWorldBindings.type(name);
            if (expected != null) {
                if (!type.equals(expected) || !suffix.isBlank())
                    throw new ShaderCompatibilityException("Invalid native world uniform " + name);
                continue; // Existing world binding validation/layout remains unchanged.
            }
            String chunkType = switch(name) { case "renderStage" -> "int"; case "cameraPositionFract" -> "vec3"; default -> null; };
            if (chunkType != null && (!type.equals(chunkType) || !suffix.isBlank()))
                throw new ShaderCompatibilityException("Invalid native chunk declaration " + name);
            boolean shadowDepth = name.matches("shadowtex[01]");
            if ((TEXTURES.contains(name) || NativeColorSamplers.index(name) >= 0 || shadowDepth)
                && (!suffix.isBlank() || !(type.equals("sampler2D") || shadowDepth && type.equals("sampler2DShadow"))))
                throw new ShaderCompatibilityException("Invalid native texture declaration " + name);
            if (!PLAIN_TYPE.matcher(type).matches() || !(suffix.isBlank() || LITERAL_DEFAULT.matcher(suffix).matches())) continue;
            if (!identifiers.contains(name))
                declarations.appendReplacement(result, declarations.group().replaceAll("[^\\r\\n]", " "));
        }
        declarations.appendTail(result);
        return result.toString();
    }
}
