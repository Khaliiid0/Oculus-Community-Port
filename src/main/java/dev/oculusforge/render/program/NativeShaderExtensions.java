package dev.oculusforge.render.program;

import java.util.Map;
import java.util.regex.Pattern;

/** Narrow compatibility mapping to functionality already present in our GLSL 330 target. */
final class NativeShaderExtensions {
    private static final Pattern DIRECTIVE = Pattern.compile("(?m)^[\\t ]*#[\\t ]*extension\\b([^\\r\\n]*)");
    private static final Pattern CORE_TEXTURE_LOD = Pattern.compile(
        "\\s*GL_ARB_shader_texture_lod\\s*:\\s*(?:enable|require|warn)\\s*");
    private static final Pattern OPTIONAL_IMAGES = Pattern.compile(
        "\\s*GL_ARB_shader_image_load_store\\s*:\\s*(?:enable|warn)\\s*");
    private static final Pattern IMAGE_FEATURE = Pattern.compile(
        "\\b(?:[iu]?image\\w*|memoryBarrier\\w*|groupMemoryBarrier|barrier|coherent|volatile|restrict|readonly|writeonly|early_fragment_tests|gl_\\w*Image\\w*)\\b");
    private static final Map<String, String> CALLS = Map.of(
        "texture2DLod", "textureLod",
        "texture2DProjLod", "textureProjLod",
        "texture2DGradARB", "textureGrad",
        "texture2DProjGradARB", "textureProjGrad");

    private NativeShaderExtensions() { }

    /** Input must already be preprocessed and comment-free. No driver-extension claims are made. */
    static String adapt(String source) throws ShaderCompatibilityException {
        var directives = DIRECTIVE.matcher(source);
        // An optional request is inert only when no extension feature survives preprocessing.
        boolean imageFeatures=IMAGE_FEATURE.matcher(DIRECTIVE.matcher(source).replaceAll("")).find();
        var result = new StringBuilder();
        while (directives.find()) {
            if (!CORE_TEXTURE_LOD.matcher(directives.group(1)).matches()
                && !(OPTIONAL_IMAGES.matcher(directives.group(1)).matches() && !imageFeatures))
                throw new ShaderCompatibilityException("Native extension is not mapped to GLSL 330 core: "
                    + directives.group(1).strip().substring(0, Math.min(120, directives.group(1).strip().length())));
            // Keep line positions, but do not ask the driver for the old extension string.
            directives.appendReplacement(result, " ".repeat(directives.group().length()));
        }
        directives.appendTail(result);
        String adapted = result.toString();
        for (var call : CALLS.entrySet())
            adapted = adapted.replaceAll("\\b" + call.getKey() + "\\b(?=\\s*\\()", call.getValue());
        var unsupported = Pattern.compile("\\b(?:texture|shadow)\\w*(?:ARB|EXT)\\b(?=\\s*\\()").matcher(adapted);
        if (unsupported.find())
            throw new ShaderCompatibilityException("Unsupported native texture extension function " + unsupported.group());
        return adapted;
    }
}
