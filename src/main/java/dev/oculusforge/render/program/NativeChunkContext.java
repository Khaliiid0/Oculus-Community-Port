package dev.oculusforge.render.program;

import java.util.Map;
import java.util.regex.Pattern;

/** This adapter runs only for chunk terrain, never block-entity or item draws. */
final class NativeChunkContext {
    private NativeChunkContext() {}
    static String adapt(String source) throws ShaderCompatibilityException {
        // These optional inputs are often declared outside a disabled voxel-lighting branch.
        // Remove only a correctly typed, unique declaration with no remaining use.
        for(var entry:Map.of("at_midBlock","attribute vec3", "renderStage","uniform int",
            "cameraPositionFract","uniform vec3").entrySet()) {
            String name=entry.getKey();
            var declaration=Pattern.compile("\\b"+entry.getValue().replace(" ","\\s+")+"\\s+"+name+"\\s*;").matcher(source);
            int count=0;
            while(declaration.find())count++;
            if(count!=1)continue;
            String without=declaration.replaceAll("");
            if(!Pattern.compile("\\b"+name+"\\b").matcher(without).find())source=without;
        }
        var declaration=Pattern.compile("\\buniform\\s+(\\w+)\\s+blockEntityId\\s*([^;]*);").matcher(source);
        int count=0;
        while(declaration.find()) {
            if(++count>1 || !declaration.group(1).equals("int") || !declaration.group(2).isBlank())
                throw new ShaderCompatibilityException("Invalid native chunk blockEntityId declaration");
        }
        // -1 is the absence sentinel: actual block entities are not submitted through this path.
        // Packs can shadow this uniform with a local material ID; never rewrite that
        // declaration, its scoped uses or an unrelated struct member into the sentinel.
        if(count==1)source=NativeUniformReferences.replace(declaration.replaceAll(""),"blockEntityId","(-1)");
        return source;
    }
}
