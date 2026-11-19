package dev.oculusforge.render.program;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Bounded argument spans for selected function names in preprocessed, comment-free GLSL. */
final class GlslCallArguments {
    static final int MAX_SOURCE = 4 * 1024 * 1024, MAX_CALLS = 65536, MAX_ARGUMENTS = 32;
    private static final int MAX_NESTING = 64, MAX_VISITS = 32 * 1024 * 1024;
    record Argument(int start, int end) {
        String text(String source) { return source.substring(start, end).trim(); }
    }
    record Call(String name, int start, int nameEnd, List<Argument> arguments) {
        Call { arguments = List.copyOf(arguments); }
    }
    private GlslCallArguments() { }

    static List<Call> find(String source, Set<String> names) throws ShaderCompatibilityException {
        if (source.length() > MAX_SOURCE || names.size() > 32)
            throw new ShaderCompatibilityException("Native sampler call analysis exceeds source/name budget");
        if (names.isEmpty()) return List.of();
        var pattern = Pattern.compile("\\b(" + names.stream().sorted().map(Pattern::quote)
            .collect(java.util.stream.Collectors.joining("|")) + ")\\s*\\(");
        var matches = pattern.matcher(source);
        var result = new ArrayList<Call>();
        int visits = 0;
        while (matches.find()) {
            if (result.size() >= MAX_CALLS) throw new ShaderCompatibilityException("Native sampler call count exceeds budget");
            int before = matches.start() - 1;
            while (before >= 0 && Character.isWhitespace(source.charAt(before))) before--;
            if (before >= 0 && source.charAt(before) == '.') continue;
            int depth = 1, brackets = 0, braces = 0, argument = matches.end(), close = -1;
            var arguments = new ArrayList<Argument>();
            for (int i = matches.end(); i < source.length(); i++) {
                if (++visits > MAX_VISITS) throw new ShaderCompatibilityException("Native sampler call analysis exceeds work budget");
                char c = source.charAt(i);
                if (c == '(') { if (++depth > MAX_NESTING) throw new ShaderCompatibilityException("Native sampler call nesting exceeds budget"); }
                else if (c == ')') {
                    if (--depth == 0) {
                        if (brackets != 0 || braces != 0) throw new ShaderCompatibilityException("Unbalanced native sampler arguments");
                        if (!source.substring(argument, i).isBlank() || !arguments.isEmpty()) arguments.add(new Argument(argument, i));
                        close = i; break;
                    }
                } else if (c == '[') brackets++;
                else if (c == ']') { if (--brackets < 0) throw new ShaderCompatibilityException("Unbalanced native sampler arguments"); }
                else if (c == '{') braces++;
                else if (c == '}') { if (--braces < 0) throw new ShaderCompatibilityException("Unbalanced native sampler arguments"); }
                else if (c == ',' && depth == 1 && brackets == 0 && braces == 0) {
                    arguments.add(new Argument(argument, i)); argument = i + 1;
                }
                if (arguments.size() >= MAX_ARGUMENTS || brackets > MAX_NESTING || braces > MAX_NESTING)
                    throw new ShaderCompatibilityException("Native sampler argument count/nesting exceeds budget");
            }
            if (close < 0) throw new ShaderCompatibilityException("Unclosed native sampler call");
            if (arguments.size() > MAX_ARGUMENTS) throw new ShaderCompatibilityException("Native sampler argument count exceeds budget");
            result.add(new Call(matches.group(1), matches.start(1), matches.end(1), arguments));
        }
        return List.copyOf(result);
    }
}
