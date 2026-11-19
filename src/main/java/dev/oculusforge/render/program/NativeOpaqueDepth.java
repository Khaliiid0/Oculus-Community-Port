package dev.oculusforge.render.program;

import java.util.regex.Pattern;

/** Raw reads and specialized leaf helpers; reverse depth becomes legacy depth once. */
final class NativeOpaqueDepth {
    private NativeOpaqueDepth() {}
    static String function(String name) {
        return name.equals("texelFetch")?"oculusNativeWorldDepthFetch":
            name.endsWith("Lod")?"oculusNativeWorldDepthLod":"oculusNativeWorldDepthSample";
    }
    static String adapt(String source, boolean shadow) throws ShaderCompatibilityException {
        var declaration=Pattern.compile("\\buniform\\s+(\\w+)\\s+depthtex1\\s*([^;]*);").matcher(source);
        int count=0;
        while(declaration.find()) {
            if(++count!=1 || !declaration.group(1).equals("sampler2D") || !declaration.group(2).isBlank())
                throw new ShaderCompatibilityException("Invalid native depthtex1 declaration");
        }
        if(count==0)return source;
        String body=declaration.replaceAll("");
        // Common include files can declare this sampler without reading it in shadow.
        // Validate the declaration first, but do not require an unused world snapshot.
        if(!Pattern.compile("\\bdepthtex1\\b").matcher(body).find())return body;
        if(shadow)throw new ShaderCompatibilityException("Native shadow cannot sample world depthtex1");
        var helpers=NativeOpaqueDepthHelpers.adapt(body);
        body=helpers.source();
        var functions=new java.util.HashSet<>(java.util.Set.of("texture2D","texture","texture2DLod","textureLod","texelFetch"));
        functions.addAll(helpers.names().keySet());
        record Edit(int start,int end,String text) {}
        var edits=new java.util.ArrayList<Edit>();
        var checked=new StringBuilder(body);
        for (var call : GlslCallArguments.find(body,functions)) {
            var helper=helpers.names().get(call.name());
            int index=helper==null?0:helper.samplerIndex();
            if(call.arguments().size()<=index || !call.arguments().get(index).text(body).equals("depthtex1")) continue;
            if(helper!=null && call.arguments().size()!=helper.arity())
                throw new ShaderCompatibilityException("Invalid native depthtex1 helper arity");
            if(helper==null && (call.arguments().size()<2 || call.arguments().size()>3
                || (call.name().equals("texelFetch") || call.name().endsWith("Lod")) && call.arguments().size()!=3))
                throw new ShaderCompatibilityException("Unsupported native depthtex1 sample arity");
            var argument=call.arguments().get(index);
            for(int i=argument.start();i<argument.end();i++) checked.setCharAt(i,' ');
            edits.add(new Edit(call.start(),call.nameEnd(),helper==null?function(call.name()):helper.internal()));
            edits.add(new Edit(argument.start(),argument.end(),"OculusOpaqueWorldDepth"));
        }
        if(Pattern.compile("\\bdepthtex1\\b").matcher(checked).find())
            throw new ShaderCompatibilityException("Native depthtex1 supports direct sampling and validated single-sampler leaf helpers only");
        if(edits.isEmpty())return body;
        var result=new StringBuilder(body);
        edits.sort(java.util.Comparator.comparingInt(Edit::start).reversed());
        for(var edit:edits)result.replace(edit.start(),edit.end(),edit.text());
        return """
            uniform sampler2D OculusOpaqueWorldDepth;
            vec4 oculusNativeWorldDepthReverse(vec4 value) { value.r=1.0-value.r; return value; }
            vec4 oculusNativeWorldDepthSample(sampler2D image, vec2 uv) {
                return oculusNativeWorldDepthReverse(texture(image,uv));
            }
            vec4 oculusNativeWorldDepthSample(sampler2D image, vec2 uv, float bias) {
                return oculusNativeWorldDepthReverse(texture(image,uv,bias));
            }
            vec4 oculusNativeWorldDepthLod(sampler2D image, vec2 uv, float lod) {
                return oculusNativeWorldDepthReverse(textureLod(image,uv,lod));
            }
            vec4 oculusNativeWorldDepthFetch(sampler2D image, ivec2 uv, int lod) {
                return oculusNativeWorldDepthReverse(texelFetch(image,uv,lod));
            }
            """+result;
    }
}
