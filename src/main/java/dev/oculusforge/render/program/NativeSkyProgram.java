package dev.oculusforge.render.program;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Prepared sky pair, not a live renderer. Only scene-color output is supported. */
public record NativeSkyProgram(NativeSkyVertexAdapter.Prepared vertex, String fragmentSource,
                               List<LegacyFinalShaderAdapter.SamplerBinding> samplers,
                               boolean usesWorldMatrices, String fingerprint) {
    public NativeSkyProgram { samplers = List.copyOf(samplers); }

    public static NativeSkyProgram adapt(String vertex, String fragment, List<Integer> targets)
        throws ShaderCompatibilityException {
        return adapt(vertex, fragment, targets, NativeCustomUniforms.EMPTY);
    }

    static NativeSkyProgram adapt(String vertex, String fragment, List<Integer> targets, NativeCustomUniforms custom)
        throws ShaderCompatibilityException {
        if (!targets.equals(List.of(0))) throw new ShaderCompatibilityException("Native sky requires only colortex0 output");
        var v = new NativeSkyVertexAdapter().adapt(vertex, custom);
        if (fragment == null || fragment.isBlank()) throw new ShaderCompatibilityException("Missing sky fragment shader");
        String f = PostProcessCapabilities.withoutComments(fragment.replace("\r\n", "\n").replace('\r', '\n'));
        if (Pattern.compile("\\b(?:oculus\\w*|Oculus\\w*|Sampler0)\\b").matcher(f).find())
            throw new ShaderCompatibilityException("Reserved native sky identifier");
        f = NativeShaderExtensions.adapt(f);
        f = NativeShaderDeclarations.expandLists(ColorBufferFormats.stripFormatDeclarations(
            f.replaceAll("(?m)^\\s*#version[^\\n]*", "")));
        f = NativeSkyVertexAdapter.adaptStage(custom.adapt(f));
        var world = NativeWorldBindings.adapt(f);
        f = world.source();
        var main = Pattern.compile("\\bvoid\\s+main\\s*\\(\\s*(?:void\\s*)?\\)\\s*\\{").matcher(f);
        if (!main.find() || main.find()) throw new ShaderCompatibilityException("Sky fragment requires exactly one main function");
        if (Pattern.compile("\\b(?:gl_FragDepth|gl_FragDepthEXT)\\b").matcher(f).find())
            throw new ShaderCompatibilityException("Native sky cannot write fragment depth");
        var declarations = Pattern.compile("\\buniform\\s+(\\w+)\\s+(\\w+)\\s*([^;]*);").matcher(f);
        var rename = new LinkedHashMap<String, String>();
        while (declarations.find()) {
            String name = declarations.group(2);
            if ((NativeColorSamplers.index(name) >= 0 || List.of("texture", "tex", "gtexture", "noisetex").contains(name))
                && !declarations.group(1).equals("sampler2D"))
                throw new ShaderCompatibilityException("Invalid sky sampler type " + name);
            if (!declarations.group(1).equals("sampler2D")) continue;
            if (!declarations.group(3).isBlank()) throw new ShaderCompatibilityException("Invalid sky sampler declaration " + name);
            String canonical = switch (name) {
                case "texture", "tex", "gtexture" -> "Sampler0";
                case "noisetex" -> "OculusNoise";
                default -> NativeColorSamplers.validate(name, targets, false);
            };
            if (rename.containsValue(canonical) || rename.putIfAbsent(name, canonical) != null)
                throw new ShaderCompatibilityException("Duplicate sky sampler alias " + name);
        }
        for (var entry : rename.entrySet()) f = f.replaceAll("\\b" + Pattern.quote(entry.getKey()) + "\\b(?!\\s*\\()", entry.getValue());
        var bindings = new LinkedHashMap<>(Map.of("Sampler0", "native-sky-texture", "OculusNoise", "native-noise"));
        for (int index = 1; index < 16; index++) bindings.put(NativeColorSamplers.canonical(index), "native-color-" + index);
        var adapted = new LegacyFinalShaderAdapter().adapt(f, v.outputs(), bindings);
        if (!adapted.uniforms().isEmpty()) throw new ShaderCompatibilityException("Unbound sky fragment uniforms: "
            + adapted.uniforms().stream().map(LegacyFinalShaderAdapter.UniformBinding::name).limit(12).toList());
        if (adapted.samplers().stream().anyMatch(s -> s.texture() != LegacyFinalShaderAdapter.TextureSemantic.CUSTOM))
            throw new ShaderCompatibilityException("Unbound sky fragment textures");
        f = adapted.source();
        var locations = Pattern.compile("layout\\(location\\s*=\\s*(\\d+)\\)").matcher(f);
        while (locations.find()) if (!locations.group(1).equals("0"))
            throw new ShaderCompatibilityException("Sky output outside colortex0 layout");
        var builtins = Pattern.compile("\\bgl_\\w+\\b").matcher(f);
        while (builtins.find()) if (!List.of("gl_FragCoord", "gl_FrontFacing").contains(builtins.group()))
            throw new ShaderCompatibilityException("Unsupported sky fragment built-in " + builtins.group());
        f = f.replaceFirst("(?m)^#version[^\\n]*", Matcher.quoteReplacement("#version 330 core\n"
            + NativeSkyVertexAdapter.STAGE_BLOCK + (world.used() ? NativeWorldBindings.block() : "")));
        return new NativeSkyProgram(v, f, adapted.samplers(), v.usesWorldMatrices() || world.used(),
            NativeTerrainProgram.hash(v.source() + '\0' + f));
    }
}
