package dev.oculusforge.render.program;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/** CPU preparation only: POSITION_TEX_COLOR sky geometry, never chunk geometry.
 * A runtime consumer must bind Projection, DynamicTransforms, OculusSkyStage and,
 * when requested, OculusWorldMatrices. No sky interception is enabled here.
 */
public final class NativeSkyVertexAdapter {
    static final String STAGE_BLOCK = "layout(std140) uniform OculusSkyStage { int oculusSkyRenderStage; };\n";
    private static final Pattern VARYING = Pattern.compile(
        "(?m)^[\\t ]*varying\\s+(float|vec[234])\\s+(\\w+)\\s*;[\\t ]*$");
    private static final String INPUTS = """
        layout(std140) uniform Projection { mat4 ProjMat; };
        layout(std140) uniform DynamicTransforms {
            mat4 ModelViewMat; vec4 ColorModulator; vec3 ModelOffset; mat4 TextureMat;
        };
        layout(std140) uniform OculusSkyStage { int oculusSkyRenderStage; };
        in vec3 Position;
        in vec2 UV0;
        in vec4 Color;
        """;

    public record Prepared(String source, Map<String, String> outputs, boolean usesWorldMatrices) {
        public Prepared { outputs = Map.copyOf(outputs); }
    }

    public Prepared adapt(String source) throws ShaderCompatibilityException {
        return adapt(source, NativeCustomUniforms.EMPTY);
    }

    Prepared adapt(String source, NativeCustomUniforms custom) throws ShaderCompatibilityException {
        if (source == null || source.isBlank()) throw new ShaderCompatibilityException("Missing sky vertex shader");
        String code = PostProcessCapabilities.withoutComments(source.replace("\r\n", "\n").replace('\r', '\n'));
        if (Pattern.compile("\\b(?:oculus\\w*|Oculus\\w*|Position|UV0|Color|ProjMat|ModelViewMat|ColorModulator|ModelOffset|TextureMat)\\b").matcher(code).find())
            throw new ShaderCompatibilityException("Reserved native sky identifier");
        code = NativeShaderExtensions.adapt(code);
        code = NativeShaderDeclarations.expandLists(code.replaceAll("(?m)^\\s*#version[^\\n]*", ""));
        code = custom.adapt(code);
        code = adaptStage(code);
        var world = NativeWorldBindings.adapt(code);
        code = world.source();
        if (Pattern.compile("\\b(?:uniform|attribute|in|layout)\\b").matcher(code).find())
            throw new ShaderCompatibilityException("Sky vertex requires unsupported uniforms or mesh inputs");
        var outputs = new LinkedHashMap<String, String>();
        var varying = VARYING.matcher(code);
        while (varying.find()) if (outputs.putIfAbsent(varying.group(2), varying.group(1)) != null)
            throw new ShaderCompatibilityException("Duplicate sky varying " + varying.group(2));
        if (Pattern.compile("\\bvarying\\b").matcher(varying.replaceAll("")).find())
            throw new ShaderCompatibilityException("Unsupported sky varying declaration");
        code = code.replaceAll("\\bvarying\\b", "out")
            .replaceAll("\\bftransform\\s*\\(\\s*\\)", "(ProjMat * ModelViewMat * vec4(Position, 1.0))")
            .replaceAll("\\bgl_ModelViewProjectionMatrix\\b", "(ProjMat * ModelViewMat)")
            .replaceAll("\\bgl_ModelViewMatrix\\b", "ModelViewMat")
            .replaceAll("\\bgl_ProjectionMatrix\\b", "ProjMat")
            .replaceAll("\\bgl_Vertex\\b", "vec4(Position, 1.0)")
            .replaceAll("\\bgl_Color\\b", "Color")
            .replaceAll("\\bgl_MultiTexCoord0\\b", "vec4(UV0, 0.0, 1.0)")
            .replaceAll("\\bgl_TextureMatrix\\s*\\[\\s*0\\s*]", "TextureMat");
        var builtins = Pattern.compile("\\bgl_\\w+\\b").matcher(code);
        while (builtins.find()) if (!builtins.group().equals("gl_Position"))
            throw new ShaderCompatibilityException("Unsupported sky vertex built-in " + builtins.group());
        var main = Pattern.compile("\\bvoid\\s+main\\s*\\(\\s*(?:void\\s*)?\\)\\s*\\{").matcher(code);
        if (!main.find() || main.find()) throw new ShaderCompatibilityException("Sky vertex requires exactly one main function");
        return new Prepared("#version 330 core\n" + INPUTS + (world.used() ? NativeWorldBindings.block() : "") + code,
            outputs, world.used());
    }

    static String adaptStage(String code) throws ShaderCompatibilityException {
        var stage = Pattern.compile("\\buniform\\s+(\\w+)\\s+renderStage\\s*;").matcher(code);
        int count = 0;
        while (stage.find()) if (!stage.group(1).equals("int") || ++count > 1)
            throw new ShaderCompatibilityException("Invalid sky renderStage declaration");
        code = stage.replaceAll("");
        if (count == 1) return NativeUniformReferences.replace(code, "renderStage", "oculusSkyRenderStage");
        if (Pattern.compile("\\brenderStage\\b").matcher(code).find())
            throw new ShaderCompatibilityException("Missing sky renderStage declaration");
        return code;
    }
}
