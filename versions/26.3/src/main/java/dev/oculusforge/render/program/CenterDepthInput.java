package dev.oculusforge.render.program;

import java.util.regex.Pattern;

/** A GPU expression, never a cursor-ray approximation or CPU depth readback. */
public final class CenterDepthInput {
    public static final String SAMPLER="oculus_CenterDepthTexture";
    private static final Pattern DECLARATION=Pattern.compile("\\buniform\\s+(\\w+)\\s+centerDepthSmooth\\s*([^;{}]*);");
    private CenterDepthInput() {}

    static String adapt(String source) throws ShaderCompatibilityException {
        if(Pattern.compile("\\b"+SAMPLER+"\\b").matcher(source).find())
            throw new ShaderCompatibilityException("Reserved center-depth sampler identifier");
        var declarations=DECLARATION.matcher(source);int count=0;
        while(declarations.find())if(++count>1||!declarations.group(1).equals("float")||!declarations.group(2).isBlank())
            throw new ShaderCompatibilityException("Invalid or duplicate centerDepthSmooth declaration");
        if(count==0)return source;
        source=declarations.replaceAll("");
        if(!Pattern.compile("\\bcenterDepthSmooth\\b").matcher(source).find())return source;
        source=NativeUniformReferences.replace(source,"centerDepthSmooth","texelFetch("+SAMPLER+", ivec2(0), 0).r");
        // Keep declarations after #version when one exists; normal adapter emits
        // the definitive version and interface declarations afterward.
        var version=Pattern.compile("(?m)^[\\t ]*#version[^\\n]*").matcher(source);
        String declaration="\nuniform sampler2D "+SAMPLER+";\n";
        return version.find()?source.substring(0,version.end())+declaration+source.substring(version.end()):declaration+source;
    }
}
