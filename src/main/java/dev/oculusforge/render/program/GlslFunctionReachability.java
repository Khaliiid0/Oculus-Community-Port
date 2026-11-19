package dev.oculusforge.render.program;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import dev.oculusforge.render.program.LegacyFinalShaderAdapter.SamplerBinding;

/** Conservative function-name reachability after preprocessing and adapter validation. */
final class GlslFunctionReachability {
    private static final Pattern CALL = Pattern.compile("\\b([A-Za-z_]\\w*)\\s*\\(");
    private static final Pattern HEADER = Pattern.compile("(?:[A-Za-z_]\\w*\\s+)+([A-Za-z_]\\w*)");
    private static final Pattern DIRECTIVE = Pattern.compile("(?m)^[\\t ]*#[^\\r\\n]*");
    private static final Pattern SAMPLER = Pattern.compile("\\buniform\\s+sampler2D(?:Shadow)?\\s+([A-Za-z_]\\w*)\\s*;");

    private GlslFunctionReachability() { }

    record Active(String source, List<SamplerBinding> samplers) { }
    private record Function(String name, int start, int body, int end) { }

    static Active activeSamplers(String source, List<SamplerBinding> samplers) {
        String pruned = prune(source);
        String references = SAMPLER.matcher(PostProcessCapabilities.withoutComments(pruned)).replaceAll("");
        var active = new ArrayList<SamplerBinding>();
        var inactive = new HashSet<String>();
        for (var sampler : samplers) {
            if (Pattern.compile("\\b" + Pattern.quote(sampler.name()) + "\\b").matcher(references).find()) active.add(sampler);
            else inactive.add(sampler.name());
        }
        var declarations = SAMPLER.matcher(pruned);
        StringBuilder result = new StringBuilder(pruned.length());
        while (declarations.find()) if (inactive.contains(declarations.group(1)))
            declarations.appendReplacement(result, spaces(declarations.group()));
        declarations.appendTail(result);
        return new Active(result.toString(), List.copyOf(active));
    }

    static String prune(String source) {
        String code = PostProcessCapabilities.withoutComments(source);
        var directives = DIRECTIVE.matcher(code);
        while (directives.find()) {
            // Conditional/macro code must first pass through the real preprocessor.
            if (!directives.group().matches("[\\t ]*#\\s*(?:version|extension|line)\\b.*")) return source;
        }
        code = DIRECTIVE.matcher(code).replaceAll(match -> spaces(match.group()));
        // Subroutine uniforms can select functions without naming them at the callsite.
        if (code.indexOf('"') >= 0 || Pattern.compile("\\bsubroutine\\b").matcher(code).find()) return source;
        var functions = new ArrayList<Function>();
        int boundary = 0;
        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);
            if (c == ';') boundary = i + 1;
            else if (c == '{') {
                int end = closingBrace(code, i);
                if (end < 0) return source;
                int start = boundary;
                while (start < i && Character.isWhitespace(code.charAt(start))) start++;
                int close = i - 1;
                while (close >= start && Character.isWhitespace(code.charAt(close))) close--;
                if (close >= start && code.charAt(close) == ')') {
                    int open = close, depth = 1;
                    while (--open >= start && depth > 0) {
                        if (code.charAt(open) == ')') depth++;
                        else if (code.charAt(open) == '(') depth--;
                    }
                    // The loop advances once past the opening parenthesis.
                    open++;
                    if (depth != 0) return source;
                    var header = HEADER.matcher(code.substring(start, open).strip());
                    if (header.matches()) functions.add(new Function(header.group(1), start, i + 1, end));
                }
                i = end - 1;
                boundary = end;
            } else if (c == '}') return source;
        }
        if (functions.stream().noneMatch(f -> f.name().equals("main"))) return source;

        var byName = new LinkedHashMap<String, Set<String>>();
        StringBuilder globals = new StringBuilder(code);
        for (var function : functions) {
            var calls = byName.computeIfAbsent(function.name(), ignored -> new HashSet<>());
            calls.addAll(calls(code, function.body(), function.end() - 1));
            for (int i = function.start(); i < function.end(); i++)
                if (globals.charAt(i) != '\n' && globals.charAt(i) != '\r') globals.setCharAt(i, ' ');
        }
        var pending = new ArrayDeque<String>();
        pending.add("main");
        // Global initializers, prototypes and unrecognized constructs are conservative roots.
        pending.addAll(calls(globals.toString(), 0, globals.length()));
        var reachable = new HashSet<String>();
        while (!pending.isEmpty()) {
            String name = pending.removeFirst();
            if (reachable.add(name)) pending.addAll(byName.getOrDefault(name, Set.of()));
        }
        StringBuilder result = null;
        for (var function : functions) if (!reachable.contains(function.name())) {
            if (result == null) result = new StringBuilder(source);
            for (int i = function.start(); i < function.end(); i++)
                if (result.charAt(i) != '\n' && result.charAt(i) != '\r') result.setCharAt(i, ' ');
        }
        return result == null ? source : result.toString();
    }

    private static Set<String> calls(String source, int start, int end) {
        var matcher = CALL.matcher(source).region(start, end);
        var calls = new HashSet<String>();
        while (matcher.find()) calls.add(matcher.group(1));
        return calls;
    }

    private static int closingBrace(String code, int start) {
        int depth = 1;
        for (int i = start + 1; i < code.length(); i++) {
            if (code.charAt(i) == '{') depth++;
            else if (code.charAt(i) == '}' && --depth == 0) return i + 1;
        }
        return -1;
    }

    private static String spaces(String source) { return source.replaceAll("[^\\r\\n]", " "); }
}
