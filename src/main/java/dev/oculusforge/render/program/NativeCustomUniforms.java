package dev.oculusforge.render.program;

import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Bounded pure-expression subset lowered to GLSL over existing native clock/camera bindings. */
public final class NativeCustomUniforms {
    private static final int MAX_ADAPTED_SOURCE_CHARS = 4 * 1024 * 1024;
    public static final NativeCustomUniforms EMPTY = new NativeCustomUniforms(Map.of(),Map.of(),Set.of(),Set.of());
    private static final Pattern PROPERTY = Pattern.compile("(uniform|variable)\\.(\\w+)\\.(\\w+)\\s*=\\s*(.*)");
    private static final Pattern EXPRESSION = Pattern.compile("(frameCounter|worldTime)\\s*([%/])\\s*([0-9]{1,7})");
    private static final Pattern UNIFORM = Pattern.compile("\\buniform\\s+(\\w+)\\s+(\\w+)\\s*;");
    private final Map<String, Expression> expressions;
    private final Map<String,Definition> definitions;
    private final Set<String> blocked;
    private final Set<String> nativeAliases;
    private CustomUniformBindings bindings;
    private record Expression(Set<String> dependencies, String glsl) { }
    private record Definition(boolean uniform, String source) { }
    private NativeCustomUniforms(Map<String, Expression> expressions,Map<String,Definition> definitions,Set<String> blocked,Set<String> nativeAliases) {
        this.expressions=Map.copyOf(expressions); this.definitions=Map.copyOf(definitions); this.blocked=Set.copyOf(blocked);
        this.nativeAliases=Set.copyOf(nativeAliases);
    }

    /** Uses the same selected, alias-checked definitions; variables remain dependency-only. */
    CustomExpressionRuntime.Program prepareCpu(Set<String> requestedUniforms) {
        for(String name:requestedUniforms) {
            Definition definition=definitions.get(name);
            if(definition==null||!definition.uniform()) throw new IllegalArgumentException("Unknown custom uniform "+name);
        }
        var sources=new LinkedHashMap<String,String>();
        definitions.forEach((name,definition)->sources.put(name,definition.source()));
        return CustomExpressionRuntime.compile(sources,requestedUniforms,blocked);
    }

    boolean declaresUniform(String name) {
        return !nativeAliases.contains(name) && (blocked.contains(name)||definitions.containsKey(name)&&definitions.get(name).uniform());
    }
    NativeCustomUniforms withBindings(CustomUniformBindings bindings) {
        var result=new NativeCustomUniforms(expressions,definitions,blocked,nativeAliases);
        result.bindings=java.util.Objects.requireNonNull(bindings);
        return result;
    }

    /** Only plain, unconditional, single-line float definitions are eligible. */
    public static NativeCustomUniforms parse(String source, Map<String,String> environment) {
        try { return parse(CustomUniformProperties.select(source, environment)); }
        catch (IllegalArgumentException unsupported) { return EMPTY; }
    }

    /** Strict parser also used after conservative property selection. */
    public static NativeCustomUniforms parse(String source) {
        Map<String, Expression> result = new LinkedHashMap<>();
        Map<String, Definition> definitions = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>(), blocked = new HashSet<>();
        // Decode property escapes/continuations solely to detect hidden aliases and overrides.
        // Only the plain-line grammar below can produce an executable expression.
        Map<String, Integer> occurrences = new java.util.HashMap<>();
        var canonical = new java.util.Properties() {
            @Override public synchronized Object put(Object key, Object value) {
                String[] parts = key.toString().split("\\.", 3);
                if (parts.length == 3 && (parts[0].equals("uniform") || parts[0].equals("variable"))) {
                    occurrences.merge(parts[2], 1, Integer::sum);
                }
                return super.put(key, value);
            }
        };
        try { canonical.load(new java.io.StringReader(source)); }
        catch (java.io.IOException | IllegalArgumentException invalid) { return EMPTY; }
        if (occurrences.size() > 512) return EMPTY;
        occurrences.forEach((name, count) -> { if (count != 1) blocked.add(name); });
        int depth = 0;
        boolean continuation = false;
        for (String line : source.split("\\R")) {
            String text = line.trim();
            if (text.matches("#\\s*(if|ifdef|ifndef)\\b.*")) { depth++; continue; }
            if (text.matches("#\\s*endif\\b.*")) {
                if (depth == 0) return EMPTY;
                depth--; continue;
            }
            if (text.matches("#\\s*(else|elif)\\b.*")) { if (depth == 0) return EMPTY; continue; }
            var property = PROPERTY.matcher(text);
            if (property.matches()) {
                String type = property.group(2), name = property.group(3), expression = property.group(4).trim();
                boolean uniform = property.group(1).equals("uniform");
                definitions.put(name, new Definition(uniform, expression));
                if (!seen.add(name) || depth != 0 || continuation || !type.equals("float")
                    || name.startsWith("gl_") || name.startsWith("oculusNative") || name.startsWith("Oculus")
                    || name.equals("biome") || name.equals("biome_precipitation") || name.equals("blindness")||name.equals("darknessFactor")
                    || name.startsWith("BIOME_") || NativeWorldBindings.supports(name)
                        && !equivalentNativeAlias(uniform,type,name,expression)) blocked.add(name);
                var parsed = EXPRESSION.matcher(expression);
                if (parsed.matches()) {
                    int divisor = Integer.parseInt(parsed.group(3));
                    String dependency = parsed.group(1), operator = parsed.group(2);
                    if (divisor < 1 || divisor > 1_000_000) blocked.add(name);
                    else if (operator.equals("%")) result.put(name,
                        new Expression(Set.of(dependency), "float(" + dependency + " % " + divisor + ")"));
                    else result.put(name, new Expression(Set.of(dependency), "(float(" + dependency + ") / " + divisor + ".0)"));
                }
                if (seen.size() > 256) return EMPTY;
            }
            continuation = text.endsWith("\\");
        }
        if (depth != 0) return EMPTY;
        // Escaped/colon/continued keys cannot silently override an existing world input.
        occurrences.keySet().stream().filter(name->!seen.contains(name)).forEach(blocked::add);
        var nativeAliases=new HashSet<String>();
        definitions.forEach((name,definition)->{
            if(equivalentNativeAlias(definition.uniform(),"float",name,definition.source()) && !blocked.contains(name)) {
                if(blocked.contains("blindness")||blocked.contains("darknessFactor")) blocked.add(name);
                else nativeAliases.add(name);
            }
        });
        nativeAliases.forEach(definitions::remove);
        nativeAliases.forEach(result::remove);
        blocked.forEach(result::remove);
        blocked.forEach(definitions::remove);
        var resolved = new java.util.HashMap<String, CustomFloatExpression.Value>();
        var resolving = new HashSet<String>();
        var failed = new HashSet<String>();
        class Resolver implements java.util.function.Function<String, CustomFloatExpression.Value> {
            @Override public CustomFloatExpression.Value apply(String name) {
                if (blocked.contains(name) || failed.contains(name)) return null;
                if (name.startsWith("@biome:")) {
                    String symbol=name.substring(7);
                    if(blocked.contains("biome") || blocked.contains(symbol) || blocked.contains("oculusBiomeCode")) return null;
                    int code=dev.oculusforge.render.world.NativeBiomeSymbols.fromSymbol(symbol);
                    return code==0?null:new CustomFloatExpression.Value("(oculusBiomeCode == "+code+")",Set.of("oculusBiomeCode"),null);
                }
                if (name.equals("worldTime") || name.equals("frameCounter"))
                    return new CustomFloatExpression.Value("float(" + name + ")", Set.of(name), null);
                if (name.equals("eyeAltitude"))
                    return new CustomFloatExpression.Value(name, Set.of(name), null);
                if (name.matches("cameraPosition\\.[xyz]") && !blocked.contains("cameraPosition"))
                    return new CustomFloatExpression.Value(name, Set.of("cameraPosition"), null);
                if (resolved.containsKey(name)) return resolved.get(name);
                Definition definition = definitions.get(name);
                if (definition == null || resolving.size() >= 24 || !resolving.add(name)) return null;
                try {
                    Expression simple = result.get(name);
                    var value = simple == null ? CustomFloatExpression.parse(definition.source(), this)
                        : new CustomFloatExpression.Value(simple.glsl(), simple.dependencies(), null);
                    resolved.put(name, value);
                    return value;
                } catch (IllegalArgumentException | NullPointerException unsupported) {
                    failed.add(name);
                    return null;
                } finally { resolving.remove(name); }
            }
        }
        var resolver = new Resolver();
        for (var entry : definitions.entrySet()) {
            if (!entry.getValue().uniform()) continue;
            var value = resolver.apply(entry.getKey());
            if (value != null) result.put(entry.getKey(), new Expression(value.dependencies(), value.glsl()));
        }
        // Variables are dependencies only, never shader-visible custom uniforms.
        result.keySet().removeIf(name -> !definitions.get(name).uniform());
        return new NativeCustomUniforms(result,definitions,blocked,nativeAliases);
    }

    /** One proven-equivalent identity, not permission for arbitrary builtin overrides. */
    private static boolean equivalentNativeAlias(boolean uniform,String type,String name,String expression) {
        return uniform && type.equals("float") && name.equals("maxBlindnessDarkness")
            && expression.matches("max\\s*\\(\\s*(?:blindness\\s*,\\s*darknessFactor|darknessFactor\\s*,\\s*blindness)\\s*\\)");
    }

    /** Input has already had comments removed and plain declaration lists expanded. */
    public String adapt(String source) throws ShaderCompatibilityException {
        if(bindings!=null) source=bindings.adapt(source);
        var declarations = UNIFORM.matcher(source);
        var stripped = new StringBuilder();
        Map<String, Expression> used = new LinkedHashMap<>();
        Set<String> dependencies = new java.util.LinkedHashSet<>();
        while (declarations.find()) {
            String name = declarations.group(2);
            Expression expression = expressions.get(name);
            if (expression == null) continue;
            if (!declarations.group(1).equals("float") || used.putIfAbsent(name, expression) != null)
                throw new ShaderCompatibilityException("Invalid or duplicate native custom uniform " + name);
            dependencies.addAll(expression.dependencies());
            declarations.appendReplacement(stripped, "");
        }
        declarations.appendTail(stripped);
        String result = stripped.toString();
        for (var entry : used.entrySet()) {
            var references = Pattern.compile("\\b" + Pattern.quote(entry.getKey()) + "\\b").matcher(result);
            String replacement = "(" + entry.getValue().glsl() + ")";
            long expandedLength = result.length();
            while (references.find()) {
                expandedLength += replacement.length() - references.group().length();
                if (expandedLength > MAX_ADAPTED_SOURCE_CHARS)
                    throw new ShaderCompatibilityException("Native custom uniform expansion exceeds source budget");
            }
            result = references.replaceAll(java.util.regex.Matcher.quoteReplacement(replacement));
        }
        for (String dependency : dependencies.stream().sorted().toList()) {
            // Keep existing declarations so the normal binding pass still validates their types/duplicates.
            if (!Pattern.compile("\\buniform\\s+\\w+\\s+" + dependency + "\\b").matcher(result).find())
                result = "uniform " + NativeWorldBindings.type(dependency) + " " + dependency + ";\n" + result;
        }
        return result;
    }
}
