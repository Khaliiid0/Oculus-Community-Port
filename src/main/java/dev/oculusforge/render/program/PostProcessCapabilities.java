package dev.oculusforge.render.program;

import dev.oculusforge.shaderpack.PackFileSystem;
import dev.oculusforge.shaderpack.VirtualPath;
import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Validates entry-point shapes before the post and world compilers inspect their own programs. */
public final class PostProcessCapabilities {
    public static final List<String> ROOTS = List.of("shaders", "shaders/world0", "shaders/world-1", "shaders/world1");
    private static final Pattern DIRECTIVE = Pattern.compile("/\\*\\s*(DRAWBUFFERS|RENDERTARGETS)\\s*:\\s*([^*]*?)\\s*\\*/");

    private PostProcessCapabilities() {}

    public static PackTextureBindings validatePack(PackFileSystem pack, List<String> stages)
        throws IOException, ShaderCompatibilityException {
        // Only program entry points are checked, not .glsl include libraries.
        for (VirtualPath path : pack.files().stream().sorted().toList()) {
            String value = path.toString();
            int slash = value.lastIndexOf('/');
            String parent = slash < 0 ? "" : value.substring(0, slash);
            String file = path.fileName();
            boolean program = file.matches(".*\\.(?:fsh|vsh|gsh|csh|tcs|tes)");
            if (!program || !(parent.equals("shaders") || parent.matches("shaders/world-?\\d+"))) continue;
            if (!ROOTS.contains(parent)) {
                throw new ShaderCompatibilityException("Unsupported dimension program directory: " + parent);
            }
            int dot = file.lastIndexOf('.');
            String stage = file.substring(0, dot);
            if (!(file.endsWith(".fsh") || file.endsWith(".vsh"))) {
                // Modern packs may ship an optional shadow-compute accelerator without a fragment fallback.
                // The native world bridge currently uses its own bounded shadow path instead.
                if (stage.equals("shadowcomp") && file.endsWith(".csh")) continue;
                if (pack.exists(VirtualPath.parse(parent + "/" + stage + ".fsh"))) continue;
                throw new ShaderCompatibilityException("Pack requires unsupported rendering program " + value
                    + ". No fragment-shader fallback exists for this geometry, tessellation or compute stage.");
            }
            if (!stages.contains(stage) && !isWorldProgram(stage)) {
                throw new ShaderCompatibilityException("Pack uses unknown renderer entry point " + value);
            }
        }
        return PackTextureBindings.read(pack);
    }

    private static boolean isWorldProgram(String stage) {
        return stage.startsWith("gbuffers_") || stage.startsWith("shadow") || stage.startsWith("dh_")
            || stage.startsWith("clrwl_") || stage.matches("(?:prepare|setup|begin)\\d*");
    }

    public static int outputBuffer(String stageName, String source) throws ShaderCompatibilityException {
        List<Integer> outputs = outputBuffers(stageName, source);
        if (outputs.size() != 1) throw new ShaderCompatibilityException(stageName + " uses multiple render targets");
        return outputs.getFirst();
    }

    public static List<Integer> outputBuffers(String stageName, String source) throws ShaderCompatibilityException {
        String code = withoutComments(source);
        // Routing only; formats are collected separately. Custom clears and mipmaps
        // are not implemented by the current compatibility bridge.
        if (Pattern.compile("\\b(?:discard|gl_FragDepth)\\b").matcher(code).find()) {
            throw new ShaderCompatibilityException(stageName + " uses discard or fragment-depth writes, which this post-processing bridge does not support");
        }
        Matcher comments = Pattern.compile("(?s)/\\*.*?\\*/|//[^\\r\\n]*").matcher(source);
        List<Integer> output = null;
        while (comments.find()) {
            Matcher directive = DIRECTIVE.matcher(comments.group());
            if (!directive.matches()) continue;
            String value = directive.group(2).trim();
            List<Integer> parsed = new java.util.ArrayList<>();
            if (directive.group(1).equals("DRAWBUFFERS") && value.matches("[0-9a-fA-F]+")) {
                value.chars().forEach(ch -> parsed.add(Character.digit(ch, 16)));
            } else if (directive.group(1).equals("RENDERTARGETS")
                && value.matches("\\s*(?:[0-9]|1[0-5])(?:\\s*,\\s*(?:[0-9]|1[0-5]))*\\s*")) {
                for (String token : value.split(",")) parsed.add(Integer.parseInt(token.trim()));
            } else {
                throw new ShaderCompatibilityException(stageName + " requests unsupported render targets '" + value
                    + "'; at most eight MRT color attachments are supported");
            }
            if (new java.util.LinkedHashSet<>(parsed).size() != parsed.size()) {
                throw new ShaderCompatibilityException(stageName + " repeats a render target");
            }
            if (parsed.size() > WorldProgramPlan.MAX_COLOR_ATTACHMENTS) {
                throw new ShaderCompatibilityException(stageName + " requests more than eight simultaneous color outputs");
            }
            // Iris/OptiFine packs extend or replace a base directive in conditional
            // branches. The last active directive wins; unioning them corrupts locations.
            output = List.copyOf(parsed);
        }
        List<Integer> result;
        if (stageName.equals("final")) {
            if (output != null && !output.equals(List.of(0))) {
                throw new ShaderCompatibilityException("final must write the screen, not a nonzero color attachment");
            }
            result = List.of(ColorBufferPlan.SCREEN);
        } else if (output == null) {
            throw new ShaderCompatibilityException(stageName + " needs an explicit single-target /* DRAWBUFFERS:0 */ or "
                + "/* RENDERTARGETS:0 */ directive");
        } else {
            result = output;
        }
        validateFragmentOutputs(stageName, code, result.size());
        return result;
    }

    private static void validateFragmentOutputs(String stage, String code, int expectedOutputs) throws ShaderCompatibilityException {
        int braces = 0;
        int parentheses = 0;
        int outputs = 0;
        for (int index = 0; index < code.length(); index++) {
            char ch = code.charAt(index);
            if (ch == '{') braces++;
            else if (ch == '}') braces--;
            else if (ch == '(') parentheses++;
            else if (ch == ')') parentheses--;
            else if (braces == 0 && parentheses == 0 && Character.isJavaIdentifierStart(ch)) {
                int end = index + 1;
                while (end < code.length() && Character.isJavaIdentifierPart(code.charAt(end))) end++;
                if (code.substring(index, end).equals("out")) {
                    outputs++;
                    int semicolon = code.indexOf(';', end);
                    String declaration = semicolon < 0 ? code.substring(end) : code.substring(end, semicolon);
                    if (declaration.contains(",") || declaration.contains("[")) {
                        throw new ShaderCompatibilityException(stage + " uses an ambiguous fragment-output declaration");
                    }
                }
                index = end - 1;
            }
        }
        if (outputs > 0 && Pattern.compile("\\b(?:gl_FragColor|gl_FragData)\\b").matcher(code).find()) {
            throw new ShaderCompatibilityException(stage + " mixes modern and legacy fragment outputs");
        }
        if (outputs > 0 && outputs != expectedOutputs) {
            throw new ShaderCompatibilityException(stage + " declares " + outputs + " fragment output(s), but its render-target directive selects "
                + expectedOutputs);
        }
        Matcher legacy = Pattern.compile("\\bgl_FragData\\s*\\[\\s*(\\d+)\\s*]").matcher(code);
        while (legacy.find()) {
            if (Integer.parseInt(legacy.group(1)) >= expectedOutputs) {
                throw new ShaderCompatibilityException(stage + " writes gl_FragData[" + legacy.group(1)
                    + "] without a matching draw-buffer slot");
            }
        }
        Matcher layouts = Pattern.compile("layout\\s*\\(([^)]*)\\)\\s*(?:(?:flat|smooth|noperspective|centroid|sample|invariant|precise)\\s+)*out\\b").matcher(code);
        java.util.Set<Integer> locations = new java.util.HashSet<>();
        while (layouts.find()) {
            String layout = layouts.group(1).replaceAll("\\s+", "");
            Matcher location = Pattern.compile("location=(\\d+)").matcher(layout);
            if (!location.matches()) {
                throw new ShaderCompatibilityException(stage + " requires a literal fragment-output location");
            }
            int value = Integer.parseInt(location.group(1));
            if (value >= expectedOutputs || !locations.add(value)) {
                throw new ShaderCompatibilityException(stage + " uses an invalid or duplicate fragment-output location " + value);
            }
        }
    }

    static String withoutComments(String source) {
        // Preserve newlines and positions so diagnostics and directive matching remain stable.
        return Pattern.compile("(?s)/\\*.*?\\*/|//[^\\r\\n]*").matcher(source)
            .replaceAll(match -> match.group().replaceAll("[^\\r\\n]", " "));
    }
}
