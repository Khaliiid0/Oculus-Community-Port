package dev.oculusforge.render.program;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/** Narrow, scope-aware native stage interfaces; does not invent mesh attributes. */
final class NativeStageInterface {
    private static final String ID = "[A-Za-z_][A-Za-z0-9_]*";
    private static final Pattern DECLARATION = Pattern.compile("(?s)\\s*((?:(?:flat|smooth|noperspective|centroid|in|out|varying)\\s+)+)"
        + "(float|vec[234]|int|uint|[iu]vec[234])\\s+(" + ID + "(?:\\s*,\\s*" + ID + ")*)\\s*;\\s*");
    private static final Pattern FUNCTION = Pattern.compile("(?s)\\s*(?:(?:lowp|mediump|highp)\\s+)?" + ID
        + "\\s+" + ID + "\\s*\\(([^()]*)\\)\\s*");
    private static final Pattern PARAMETER = Pattern.compile("\\s*((?:(?:const|in|out|inout|lowp|mediump|highp)\\s+)*)"
        + ID + "(?:\\s+" + ID + ")?(?:\\s*\\[\\s*(?:[0-9]+|" + ID + ")?\\s*])?\\s*");
    private static final Pattern DIRECTION = Pattern.compile("\\b(?:in|out|inout)\\b");
    private static final Pattern DIRECTIVE = Pattern.compile("(?m)^[\\t ]*#[^\\r\\n]*");
    private static final Set<String> INTERNAL_NAMES = Set.of("Position", "Color", "UV0", "UV2", "Globals", "Projection", "ChunkSection",
        "ProjMat", "ModelViewMat", "CameraBlockPos", "CameraOffset", "ScreenSize", "GlintAlpha", "GameTime", "MenuBlurRadius",
        "UseRgss", "ChunkVisibility", "TextureSize", "ChunkPosition", "Sampler0", "Sampler2");

    record Varying(String type, String interpolation, boolean centroid) { }
    record Stage(String source, String validationSource, Map<String, Varying> varyings) {
        Stage { varyings = Map.copyOf(varyings); }
        Map<String, String> types() {
            var result = new LinkedHashMap<String, String>();
            varyings.forEach((name, varying) -> result.put(name, varying.type()));
            return result;
        }
    }
    private record Edit(int start, int end, String replacement) { }

    private NativeStageInterface() { }

    /** Input has passed preprocessing, comment removal and native declaration validation. */
    static Stage parse(String source, boolean vertex) throws ShaderCompatibilityException {
        String code = PostProcessCapabilities.withoutComments(source);
        var scan = new StringBuilder(code);
        var directives = DIRECTIVE.matcher(code);
        while (directives.find()) {
            if (!directives.group().matches("[\\t ]*#\\s*line\\b.*"))
                throw new ShaderCompatibilityException("Native stage interfaces require preprocessed source");
            blank(scan, directives.start(), directives.end());
        }
        code = scan.toString();
        var validation = new StringBuilder(code);
        var edits = new ArrayList<Edit>();
        var varyings = new LinkedHashMap<String, Varying>();
        int braces = 0, parentheses = 0, boundary = 0;
        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);
            if (c == '(') parentheses++;
            else if (c == ')' && --parentheses < 0) throw malformed();
            else if (c == '{') {
                if (braces == 0) {
                    String header = code.substring(boundary, i);
                    if (!parameters(header, boundary, validation)
                        && (interfaceKeyword(header, vertex) || DIRECTION.matcher(header).find()))
                        throw new ShaderCompatibilityException("Native stage interface blocks or qualified declarations are unsupported");
                }
                braces++;
            } else if (c == '}') {
                if (--braces < 0) throw malformed();
                if (braces == 0) boundary = i + 1;
            } else if (c == ';' && braces == 0 && parentheses == 0) {
                String statement = code.substring(boundary, i + 1);
                if (!parameters(statement.substring(0, statement.length() - 1), boundary, validation)
                    && interfaceKeyword(statement, vertex)) {
                    var declaration = DECLARATION.matcher(statement);
                    if (!declaration.matches())
                        throw new ShaderCompatibilityException("Unsupported native " + (vertex ? "vertex output" : "fragment input") + " declaration");
                    Set<String> qualifiers = new HashSet<>();
                    for (String qualifier : declaration.group(1).strip().split("\\s+"))
                        if (!qualifiers.add(qualifier)) throw invalidQualifiers();
                    int directions = 0;
                    for (String direction : List.of("in", "out", "varying")) if (qualifiers.contains(direction)) directions++;
                    if (directions != 1 || qualifiers.contains(vertex ? "in" : "out")) throw invalidQualifiers();
                    String interpolation = "smooth";
                    int interpolationCount = 0;
                    for (String qualifier : List.of("flat", "smooth", "noperspective")) if (qualifiers.contains(qualifier)) {
                        interpolation = qualifier;
                        interpolationCount++;
                    }
                    if (interpolationCount > 1) throw invalidQualifiers();
                    String type = declaration.group(2);
                    if (!type.matches("float|vec[234]") && !interpolation.equals("flat"))
                        throw new ShaderCompatibilityException("Native integer stage interfaces require flat interpolation");
                    boolean centroid = qualifiers.contains("centroid");
                    var varying = new Varying(type, interpolation, centroid);
                    var replacement = new StringBuilder();
                    // Preserve the leading whitespace/directives outside the actual declaration.
                    int start = boundary;
                    while (start < i && Character.isWhitespace(code.charAt(start))) start++;
                    for (String name : declaration.group(3).split("\\s*,\\s*")) {
                        if (name.startsWith("gl_") || name.startsWith("oculus_") || name.startsWith("Oculus") || INTERNAL_NAMES.contains(name))
                            throw new ShaderCompatibilityException("Reserved native stage interface name");
                        if (varyings.putIfAbsent(name, varying) != null)
                            throw new ShaderCompatibilityException("Duplicate native stage interface " + bounded(name));
                        if (varyings.size() > 64)
                            throw new ShaderCompatibilityException("Native shader exceeds 64 stage-interface variables");
                        if (!replacement.isEmpty()) replacement.append('\n');
                        if (!interpolation.equals("smooth")) replacement.append(interpolation).append(' ');
                        if (centroid) replacement.append("centroid ");
                        replacement.append(vertex ? "out " : "in ").append(type).append(' ').append(name).append(';');
                    }
                    edits.add(new Edit(start, i + 1, replacement.toString()));
                    blank(validation, start, i + 1);
                }
                boundary = i + 1;
            }
        }
        if (braces != 0 || parentheses != 0) throw malformed();
        if (interfaceKeyword(code.substring(boundary), vertex))
            throw new ShaderCompatibilityException("Unterminated native stage interface declaration");
        if (!vertex && Pattern.compile("\\b(?:in|inout|varying)\\b").matcher(validation).find())
            throw new ShaderCompatibilityException("Unsupported native fragment interface or parameter declaration");
        var result = new StringBuilder(source.length());
        int position = 0;
        for (var edit : edits) {
            result.append(source, position, edit.start()).append(edit.replacement());
            position = edit.end();
        }
        result.append(source, position, source.length());
        return new Stage(result.toString(), validation.toString(), varyings);
    }

    static void match(Stage vertex, Stage fragment) throws ShaderCompatibilityException {
        for (var input : fragment.varyings().entrySet()) {
            if (!input.getValue().equals(vertex.varyings().get(input.getKey())))
                throw new ShaderCompatibilityException("Native fragment varying '" + bounded(input.getKey())
                    + "' has no matching vertex output type/interpolation");
        }
    }

    private static boolean interfaceKeyword(String text, boolean vertex) {
        return Pattern.compile(vertex ? "\\b(?:varying|out)\\b" : "\\b(?:varying|in)\\b").matcher(text).find();
    }

    /** Mask only direction qualifiers belonging to proven function parameter declarations. */
    private static boolean parameters(String header, int offset, StringBuilder validation) throws ShaderCompatibilityException {
        var function = FUNCTION.matcher(header);
        if (!function.matches()) return false;
        String parameters = function.group(1);
        int cursor = 0;
        for (String parameter : parameters.split(",", -1)) {
            if (DIRECTION.matcher(parameter).find()) {
                var declaration = PARAMETER.matcher(parameter);
                if (!declaration.matches()) throw new ShaderCompatibilityException("Unsupported native function parameter declaration");
                Set<String> qualifiers = new HashSet<>();
                for (String qualifier : declaration.group(1).strip().split("\\s+"))
                    if (!qualifiers.add(qualifier)) throw invalidQualifiers();
                long directions = qualifiers.stream().filter(q -> Set.of("in", "out", "inout").contains(q)).count();
                long precisions = qualifiers.stream().filter(q -> Set.of("lowp", "mediump", "highp").contains(q)).count();
                if (directions != 1 || precisions > 1 || qualifiers.contains("const") && !qualifiers.contains("in")) throw invalidQualifiers();
                var direction = DIRECTION.matcher(declaration.group(1));
                while (direction.find()) blank(validation, offset + function.start(1) + cursor + direction.start(),
                    offset + function.start(1) + cursor + direction.end());
            }
            cursor += parameter.length() + 1;
        }
        return true;
    }

    private static void blank(StringBuilder source, int start, int end) {
        for (int i = start; i < end; i++) if (source.charAt(i) != '\n' && source.charAt(i) != '\r') source.setCharAt(i, ' ');
    }
    private static String bounded(String name) { return name.substring(0, Math.min(64, name.length())); }
    private static ShaderCompatibilityException malformed() { return new ShaderCompatibilityException("Unbalanced native shader interface scope"); }
    private static ShaderCompatibilityException invalidQualifiers() { return new ShaderCompatibilityException("Invalid native stage/parameter qualifiers"); }
}
