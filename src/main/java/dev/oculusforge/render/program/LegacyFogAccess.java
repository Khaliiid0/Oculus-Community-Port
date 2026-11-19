package dev.oculusforge.render.program;

import java.util.List;
import java.util.regex.Pattern;

/** Bounded member-read subset of gl_Fog, on comment-free/preprocessed GLSL. */
final class LegacyFogAccess {
    static final int MAX_SOURCE=4*1024*1024,MAX_REFERENCES=65536;
    private static final Pattern TOKEN=Pattern.compile("\\bgl_Fog\\b");
    private static final Pattern MEMBER=Pattern.compile("\\G\\s*\\.\\s*([A-Za-z_]\\w*)");
    private static final Pattern STORE=Pattern.compile("\\G\\s*(?:\\.\\s*[A-Za-z_]\\w*\\s*)?(?:\\+\\+|--|=(?!=)|[+*/%&|^\\-]=)");
    private static final Pattern RESERVED=Pattern.compile("\\boculusLegacyFog(?:Color|Start|End)\\b");
    private LegacyFogAccess() { }
    static final List<LegacyFinalShaderAdapter.UniformBinding> POST_BINDINGS=List.of(
        new LegacyFinalShaderAdapter.UniformBinding("oculusLegacyFogColor",LegacyFinalShaderAdapter.UniformSemantic.LEGACY_FOG_COLOR),
        new LegacyFinalShaderAdapter.UniformBinding("oculusLegacyFogStart",LegacyFinalShaderAdapter.UniformSemantic.LEGACY_FOG_START),
        new LegacyFinalShaderAdapter.UniformBinding("oculusLegacyFogEnd",LegacyFinalShaderAdapter.UniformSemantic.LEGACY_FOG_END));
    record Adapted(String source,boolean used) { }
    static Adapted adapt(String source,boolean nativeWorld) throws ShaderCompatibilityException {
        if(source.length()>MAX_SOURCE)throw new ShaderCompatibilityException("Legacy fog source exceeds bounds");
        if(RESERVED.matcher(source).find())throw new ShaderCompatibilityException("Reserved legacy fog binding name");
        var tokens=TOKEN.matcher(source);var members=MEMBER.matcher(source);var store=STORE.matcher(source);
        var result=new StringBuilder();int copied=0,count=0;
        while(tokens.find()) {
            if(++count>MAX_REFERENCES)throw new ShaderCompatibilityException("Too many legacy fog references");
            int before=tokens.start()-1;while(before>=0&&Character.isWhitespace(source.charAt(before)))before--;
            if(before>=0&&source.charAt(before)=='.')throw new ShaderCompatibilityException("Unsupported member named gl_Fog");
            members.region(tokens.end(),source.length());
            if(!members.lookingAt())throw new ShaderCompatibilityException("Legacy fog requires an explicit supported member read");
            String member=members.group(1);
            String start=nativeWorld?"oculusNative_fogStart":"oculusLegacyFogStart";
            String end=nativeWorld?"oculusNative_fogEnd":"oculusLegacyFogEnd";
            String expression=switch(member) {
                case "color" -> nativeWorld?"vec4(oculusNative_fogColor.xyz, oculusNative_fogAlpha)":"oculusLegacyFogColor";
                case "start" -> start;
                case "end" -> end;
                case "scale" -> "1.0 / ("+end+" - "+start+")";
                default -> throw new ShaderCompatibilityException("Unsupported legacy fog member "+member+"; color/start/end/scale only");
            };
            store.region(members.end(),source.length());
            if(store.lookingAt()||before>=1&&(source.substring(before-1,before+1).equals("++")||source.substring(before-1,before+1).equals("--")))
                throw new ShaderCompatibilityException("Legacy fog is read-only");
            long size=(long)result.length()+tokens.start()-copied+expression.length()+2;
            if(size>MAX_SOURCE)throw new ShaderCompatibilityException("Expanded legacy fog source exceeds bounds");
            result.append(source,copied,tokens.start()).append('(').append(expression).append(')');
            copied=members.end();tokens.region(copied,source.length());
        }
        if(count==0)return new Adapted(source,false);
        if((long)result.length()+source.length()-copied>MAX_SOURCE)throw new ShaderCompatibilityException("Expanded legacy fog source exceeds bounds");
        return new Adapted(result.append(source,copied,source.length()).toString(),true);
    }
}
