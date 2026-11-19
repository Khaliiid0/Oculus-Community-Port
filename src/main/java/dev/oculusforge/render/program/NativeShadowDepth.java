package dev.oculusforge.render.program;

import java.util.regex.Pattern;

/** Bounded reads of completed live and independently captured opaque shadow depth. */
final class NativeShadowDepth {
    private static final Pattern DECLARATION=Pattern.compile("\\buniform\\s+(sampler2D|sampler2DShadow)\\s+(shadowtex[01])\\s*;");
    private NativeShadowDepth() {}
    static String adapt(String source,boolean shadowPass) throws ShaderCompatibilityException {
        var declaration=DECLARATION.matcher(source);
        var samplers=new java.util.LinkedHashMap<String,Boolean>();
        while(declaration.find()) {
            String name=declaration.group(2);
            if(samplers.putIfAbsent(name,declaration.group(1).equals("sampler2DShadow"))!=null)
                throw new ShaderCompatibilityException("Duplicate native "+name+" sampler");
        }
        if(samplers.isEmpty()) return source;
        String body=DECLARATION.matcher(source).replaceAll("");
        String code=body;
        samplers.entrySet().removeIf(entry -> !Pattern.compile("\\b"+entry.getKey()+"\\b").matcher(code).find());
        if(samplers.isEmpty()) return body;
        if(shadowPass) throw new ShaderCompatibilityException("Native shadow cannot sample its own shadow depth outputs");
        var comparisonSamplers=new java.util.LinkedHashSet<String>();
        samplers.forEach((name,compare) -> {if(compare) comparisonSamplers.add(name);});
        // Specialize comparison leaf helpers without merging their raw overloads.
        var helpers=NativeShadowSamplerHelpers.adapt(body,comparisonSamplers);
        body=helpers.source();
        var uniforms=new StringBuilder();
        for(var sampler:samplers.entrySet()) {
            String name=sampler.getKey();
            boolean compare=sampler.getValue();
            String canonical=name.equals("shadowtex0")?"OculusShadowDepth":"OculusOpaqueShadowDepth";
            String functions=compare?"shadow2D|texture":"texture2D|texture|texture2DLod|textureLod|texelFetch";
            if(compare) for(String helper:helpers.names().keySet()) functions+="|"+Pattern.quote(helper);
            Pattern call=Pattern.compile("\\b("+functions+")\\s*\\(\\s*"+name+"\\s*,");
            if(Pattern.compile("\\b"+name+"\\b").matcher(call.matcher(body).replaceAll("")).find())
                throw new ShaderCompatibilityException("Native "+name+" currently supports direct sampling calls only");
            var calls=call.matcher(body);
            if(!calls.find()) continue;
            calls.reset();
            var result=new StringBuilder();
            while(calls.find()) {
                String function=calls.group(1);
                String replacement=helpers.names().containsKey(function)?helpers.names().get(function):compare
                    ? (function.equals("shadow2D")?"oculusNativeShadowCompareVector":"oculusNativeShadowCompare")
                    : (function.equals("texelFetch")?"oculusNativeShadowFetch"
                        :function.endsWith("Lod")?"oculusNativeShadowLod":"oculusNativeShadowSample");
                calls.appendReplacement(result,replacement+"("+canonical+",");
            }
            calls.appendTail(result);
            body=result.toString();
            uniforms.append("uniform sampler2D ").append(canonical).append(";\n");
        }
        return uniforms+(comparisonSamplers.isEmpty()?"":"""
            float oculusNativeShadowCompare(sampler2D image, vec3 coordinate) {
                float stored = 1.0 - texture(image, coordinate.xy).r;
                return step(coordinate.z, stored);
            }
            vec4 oculusNativeShadowCompareVector(sampler2D image, vec3 coordinate) {
                return vec4(oculusNativeShadowCompare(image, coordinate));
            }
            """)+(samplers.containsValue(false)?"""
            vec4 oculusNativeShadowReverse(vec4 value) { value.r = 1.0 - value.r; return value; }
            vec4 oculusNativeShadowSample(sampler2D image, vec2 uv) {
                return oculusNativeShadowReverse(texture(image, uv));
            }
            vec4 oculusNativeShadowSample(sampler2D image, vec2 uv, float bias) {
                return oculusNativeShadowReverse(texture(image, uv, bias));
            }
            vec4 oculusNativeShadowLod(sampler2D image, vec2 uv, float lod) {
                return oculusNativeShadowReverse(textureLod(image, uv, lod));
            }
            vec4 oculusNativeShadowFetch(sampler2D image, ivec2 uv, int lod) {
                return oculusNativeShadowReverse(texelFetch(image, uv, lod));
            }
            """:"")+body;
    }
}
