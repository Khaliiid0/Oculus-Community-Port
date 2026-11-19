package dev.oculusforge.render.program;

import java.util.*;
import java.util.regex.*;

/** Specialize one-sampler leaf helpers at a validated argument position; preserve other callers. */
final class NativeOpaqueDepthHelpers {
    private static final Pattern HEADER = Pattern.compile(
        "\\b(float|vec[234])\\s+([A-Za-z_]\\w*)\\s*\\(([^(){};]*)\\)\\s*\\{");
    private static final String PARAM = "(?:const\\s+)?(?:(?:in|out|inout)\\s+)?(?:float|int|bool|[bi]?vec[234])\\s+[A-Za-z_]\\w*";
    private static final Pattern SAMPLER = Pattern.compile("(?:const\\s+)?(?:in\\s+)?sampler2D\\s+([A-Za-z_]\\w*)");
    record Helper(String internal, int samplerIndex, int arity) {}
    record Adapted(String source, Map<String,Helper> names) {}
    private record Edit(int offset, String text) {}
    private NativeOpaqueDepthHelpers() {}

    static Adapted adapt(String source) throws ShaderCompatibilityException {
        if (source.length() > GlslCallArguments.MAX_SOURCE)
            throw new ShaderCompatibilityException("Native depthtex1 helper source exceeds budget");
        if (!Pattern.compile("\\bdepthtex1\\b").matcher(source).find()) return new Adapted(source, Map.of());
        // Index candidate calls once, not once per declaration in a large include file.
        var helperNames = new LinkedHashSet<String>();
        var scan = HEADER.matcher(source);
        while (scan.find()) if (Pattern.compile("\\bsampler2D\\b").matcher(scan.group(3)).find()) {
            helperNames.add(scan.group(2));
            if (helperNames.size() > 32) throw new ShaderCompatibilityException("Native depthtex1 helper declaration index exceeds budget");
        }
        var callers = GlslCallArguments.find(source, helperNames).stream()
            .collect(java.util.stream.Collectors.groupingBy(GlslCallArguments.Call::name));
        var headers=HEADER.matcher(source);
        var edits=new ArrayList<Edit>();
        var names=new LinkedHashMap<String,Helper>();
        long expanded = source.length();
        while(headers.find()) {
            String name=headers.group(2);
            if (!Pattern.compile("\\bsampler2D\\b").matcher(headers.group(3)).find()) continue;
            var caller = callers.getOrDefault(name, List.of());
            if (caller.stream().noneMatch(call -> call.arguments().stream().anyMatch(a -> a.text(source).equals("depthtex1")))) continue;
            if(Set.of("texture","texture2D","textureLod","texture2DLod","texelFetch").contains(name)
                || names.size()>=16 || names.containsKey(name))
                throw new ShaderCompatibilityException("Ambiguous or excessive native depthtex1 helpers");
            // Reject overloads/prototypes rather than guess which GLSL signature is selected.
            var definitions=Pattern.compile("\\b(?:void|float|int|uint|bool|[biu]?vec[234]|mat[234])\\s+"+Pattern.quote(name)+"\\s*\\([^{};]*\\)\\s*[;{]").matcher(source);
            int count=0;
            while(definitions.find()) count++;
            String[] parameters = headers.group(3).split(",", -1);
            int samplerIndex = -1;
            String parameter = null;
            var parameterNames = new HashSet<String>();
            if (parameters.length > GlslCallArguments.MAX_ARGUMENTS)
                throw new ShaderCompatibilityException("Unsupported native depthtex1 helper parameter count");
            for (int i = 0; i < parameters.length; i++) {
                String value = parameters[i].trim();
                var sampler = SAMPLER.matcher(value);
                if (sampler.matches()) {
                    if (samplerIndex >= 0) throw new ShaderCompatibilityException("Native depthtex1 helpers require exactly one sampler parameter");
                    samplerIndex = i; parameter = sampler.group(1);
                } else if (!value.matches(PARAM))
                    throw new ShaderCompatibilityException("Unsupported native depthtex1 helper signature");
                String[] tokens = value.split("\\s+");
                String parameterName = tokens[tokens.length - 1];
                if (!parameterNames.add(parameterName) || parameterName.equals("depthtex1"))
                    throw new ShaderCompatibilityException("Ambiguous native depthtex1 helper parameters");
            }
            if(count!=1 || samplerIndex < 0)
                throw new ShaderCompatibilityException("Unsupported native depthtex1 helper signature");
            for (var call : caller) if (call.arguments().stream().anyMatch(a -> a.text(source).equals("depthtex1"))) {
                if (call.arguments().size() != parameters.length || !call.arguments().get(samplerIndex).text(source).equals("depthtex1"))
                    throw new ShaderCompatibilityException("Native depthtex1 helper called with an invalid sampler position or arity");
            }
            int end=headers.end(), depth=1;
            while(end<source.length() && depth>0) {
                char c=source.charAt(end++);
                if(c=='{')depth++; else if(c=='}')depth--;
            }
            if(depth!=0)throw new ShaderCompatibilityException("Unclosed native depthtex1 helper");
            String body=source.substring(headers.end(),end-1);
            var calls=Pattern.compile("\\b(texture2D|texture|texture2DLod|textureLod|texelFetch)\\s*\\(\\s*"+Pattern.quote(parameter)+"\\s*,");
            if(!calls.matcher(body).find() || Pattern.compile("\\b"+Pattern.quote(parameter)+"\\b")
                .matcher(calls.matcher(body).replaceAll("")).find())
                throw new ShaderCompatibilityException("Native depthtex1 helper requires direct sampling of its sampler parameter");
            String internal="oculusNativeWorldDepthHelper"+names.size();
            names.put(name,new Helper(internal,samplerIndex,parameters.length));
            var samples=calls.matcher(body);
            var rewritten=new StringBuilder();
            while(samples.find()) samples.appendReplacement(rewritten, NativeOpaqueDepth.function(samples.group(1))+"("+parameter+",");
            samples.appendTail(rewritten);
            String header=headers.group().replaceFirst("\\b"+Pattern.quote(name)+"\\b",internal);
            // Keep the original for non-depth callers; insert after it to preserve dependency order.
            String clone = "\n"+header+rewritten+"}\n";
            expanded += clone.length();
            if (expanded > GlslCallArguments.MAX_SOURCE)
                throw new ShaderCompatibilityException("Native depthtex1 helper expansion exceeds source budget");
            edits.add(new Edit(end,clone));
        }
        var result=new StringBuilder(source);
        for(int i=edits.size()-1;i>=0;i--) result.insert(edits.get(i).offset(),edits.get(i).text());
        return new Adapted(result.toString(),Map.copyOf(names));
    }
}
