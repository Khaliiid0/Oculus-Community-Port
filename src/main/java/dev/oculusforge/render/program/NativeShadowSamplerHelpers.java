package dev.oculusforge.render.program;

import java.util.*;
import java.util.regex.*;

/** Single-level comparison helpers, specialized separately from raw sampler overloads. */
final class NativeShadowSamplerHelpers {
    private static final Pattern HEADER=Pattern.compile(
        "\\b(float|vec[234])\\s+([A-Za-z_]\\w*)\\s*\\(\\s*sampler2DShadow\\s+([A-Za-z_]\\w*)\\s*,([^(){};]+)\\)\\s*\\{");
    private static final Pattern PARAMETERS=Pattern.compile(
        "\\s*(?:float|vec[234])\\s+[A-Za-z_]\\w*\\s*(?:,\\s*(?:float|vec[234])\\s+[A-Za-z_]\\w*\\s*)*");
    record Adapted(String source,Map<String,String> names) {
        Adapted { names=Map.copyOf(names); }
    }
    private record Edit(int start,int end,String text) {}
    private NativeShadowSamplerHelpers() {}
    static Adapted adapt(String source) throws ShaderCompatibilityException {
        return adapt(source, Set.of("shadowtex0"));
    }
    static Adapted adapt(String source, Set<String> comparisonSamplers) throws ShaderCompatibilityException {
        var headers=HEADER.matcher(source);
        var edits=new ArrayList<Edit>();var names=new LinkedHashMap<String,String>();
        while(headers.find()) {
            String name=headers.group(2),parameter=headers.group(3);
            // Only helpers directly called with our comparison texture are candidates.
            if(comparisonSamplers.stream().noneMatch(sampler -> Pattern.compile(
                "\\b"+Pattern.quote(name)+"\\s*\\(\\s*"+Pattern.quote(sampler)+"\\s*,").matcher(source).find())) continue;
            if(Set.of("shadow2D","texture","texture2D","textureLod","texelFetch").contains(name))
                throw new ShaderCompatibilityException("Native shadow helper cannot override a sampling builtin");
            int end=headers.end(),depth=1;
            while(end<source.length()&&depth>0) {
                char character=source.charAt(end++);
                if(character=='{') depth++; else if(character=='}') depth--;
            }
            if(depth!=0) throw new ShaderCompatibilityException("Unclosed native shadow sampler helper");
            if(!PARAMETERS.matcher(headers.group(4)).matches())
                throw new ShaderCompatibilityException("Unsupported native shadow helper parameters");
            if(names.size()>=16 || names.containsKey(name))
                throw new ShaderCompatibilityException("Ambiguous or excessive native shadow sampler helpers");
            String body=source.substring(headers.end(),end-1);
            var calls=Pattern.compile("\\b(shadow2D|texture)\\s*\\(\\s*"+Pattern.quote(parameter)+"\\s*,");
            if(!calls.matcher(body).find() || Pattern.compile("\\b"+Pattern.quote(parameter)+"\\b")
                .matcher(calls.matcher(body).replaceAll("")).find())
                throw new ShaderCompatibilityException("Native comparison helper requires direct sampling of its sampler parameter");
            String internal="oculusNativeShadowHelper"+names.size();
            names.put(name,internal);
            var samples=calls.matcher(body);var rewritten=new StringBuilder();
            while(samples.find()) samples.appendReplacement(rewritten,
                (samples.group(1).equals("shadow2D")?"oculusNativeShadowCompareVector":"oculusNativeShadowCompare")
                    +"("+parameter+",");
            samples.appendTail(rewritten);
            String header=headers.group().replaceFirst("\\b"+Pattern.quote(name)+"\\b",internal)
                .replaceFirst("\\bsampler2DShadow\\b","sampler2D");
            edits.add(new Edit(headers.start(),end,header+rewritten+"}"));
        }
        var result=new StringBuilder(source);
        for(int i=edits.size()-1;i>=0;i--) {
            var edit=edits.get(i);result.replace(edit.start,edit.end,edit.text);
        }
        return new Adapted(result.toString(),names);
    }
}
