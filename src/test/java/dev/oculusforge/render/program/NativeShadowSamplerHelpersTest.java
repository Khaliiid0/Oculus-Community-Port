package dev.oculusforge.render.program;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

final class NativeShadowSamplerHelpersTest {
    @Test void helperGpuDiagnosticPreparesWithSupportedNativeProducer(
        @org.junit.jupiter.api.io.TempDir java.nio.file.Path root) throws Exception {
        var original=java.nio.file.Path.of(getClass().getClassLoader().getResource("shadow-depth-read/shaders").toURI());
        var shaders=java.nio.file.Files.createDirectories(root.resolve("shaders"));
        try(var files=java.nio.file.Files.list(original)) {
            for(var file:files.toList()) java.nio.file.Files.copy(file,shaders.resolve(file.getFileName()));
        }
        var fragment=shaders.resolve("gbuffers_terrain.fsh");
        java.nio.file.Files.writeString(fragment,java.nio.file.Files.readString(fragment)
            .replace("#define COMPARE_PROBE 0","#define COMPARE_PROBE 1")
            .replace("#define HELPER_PROBE 0","#define HELPER_PROBE 1"));
        var p=new PackProgramCompiler(dev.oculusforge.shaderpack.ShaderPackLimits.DEFAULT).prepare(
            new dev.oculusforge.shaderpack.ShaderPackDescriptor("helper","Helper",root,
                dev.oculusforge.shaderpack.ShaderPackDescriptor.Kind.DIRECTORY)).world().variant("shaders");
        assertNotNull(p.nativeTerrain(),p.nativeTerrainIssue());assertNotNull(p.nativeShadow(),p.nativeShadowIssue());
        assertTrue(p.nativeTerrain().usesShadowDepth());assertTrue(p.nativeTerrain().usesShadowColor());
        assertTrue(p.nativeTerrain().fragmentSource().contains("oculusNativeShadowHelper1(OculusShadowDepth,"));
    }
    private static final String V="void main(){gl_Position=ftransform();}";
    private static String source(String helper) {
        return "uniform sampler2DShadow shadowtex0;\n"+helper
            +"\nvoid main(){gl_FragColor=vec4(readShadow(shadowtex0,vec3(.5)));}";
    }
    @Test void bslStyleComparisonOverloadIsSpecializedWithoutChangingRawOverload() throws Exception {
        String raw="float readShadow(sampler2D image,vec3 position){return texture2D(image,position.xy).r;}";
        String mixed="uniform sampler2D texture;\n"+source(raw+"\n"
            +"float readShadow(sampler2DShadow image,vec3 position){return shadow2D(image,position).x;}")
            .replace("vec4(readShadow(shadowtex0,vec3(.5)))", "vec4(readShadow(shadowtex0,vec3(.5))+readShadow(texture,vec3(.5)))");
        var p=NativeTerrainProgram.adapt(V,mixed,List.of(0));
        String f=p.fragmentSource();
        assertTrue(p.usesShadowDepth());
        assertTrue(f.contains("float readShadow(sampler2D image,vec3 position)"));
        assertTrue(f.contains("return texture(image,position.xy).r"));
        assertTrue(f.contains("float oculusNativeShadowHelper0(sampler2D image,vec3 position)"));
        assertTrue(f.contains("return oculusNativeShadowCompareVector(image,position).x"));
        assertTrue(f.contains("vec4(oculusNativeShadowHelper0(OculusShadowDepth,"));
        assertFalse(f.contains("sampler2DShadow"));
    }
    @Test void modernScalarCallsAndNestedBodyBracesArePreserved() throws Exception {
        var p=NativeTerrainProgram.adapt(V,source(
            "float readShadow(sampler2DShadow image,vec3 position){"
            +"if(position.z>0.0){return texture(image,position);}return shadow2D(image,position).r;}"),List.of(0));
        assertTrue(p.fragmentSource().contains("return oculusNativeShadowCompare(image,position)"));
        assertTrue(p.fragmentSource().contains("return oculusNativeShadowCompareVector(image,position).r"));
        assertTrue(p.fragmentSource().contains("if(position.z>0.0){"));
    }
    @Test void differentLeafHelpersGetDifferentSpecializations() throws Exception {
        String f=source("float readShadow(sampler2DShadow a,vec3 p){return texture(a,p);}\n"
            +"float other(sampler2DShadow b,vec3 q){return shadow2D(b,q).r;}")
            .replace("vec4(readShadow(shadowtex0,vec3(.5)))",
                "vec4(readShadow(shadowtex0,vec3(.5))+other(shadowtex0,vec3(.5)))");
        var p=NativeTerrainProgram.adapt(V,f,List.of(0));
        assertTrue(p.fragmentSource().contains("oculusNativeShadowHelper0(OculusShadowDepth,"));
        assertTrue(p.fragmentSource().contains("oculusNativeShadowHelper1(OculusShadowDepth,"));
    }
    @Test void forwardingQueriesInvalidParametersAndAmbiguousOverloadsFailClosed() {
        for(String helper:List.of(
            "float readShadow(sampler2DShadow image,vec3 p){return other(image,p);}",
            "float readShadow(sampler2DShadow image,vec3 p){return texture(image,p)+float(textureSize(image,0).x);}",
            "float readShadow(sampler2DShadow image,sampler2D other){return texture(image,vec3(.5));}",
            "float readShadow(sampler2DShadow image,vec3 p){return texture(image,p);}\n"
                +"float readShadow(sampler2DShadow image,vec2 p){return texture(image,vec3(p,0));}"))
            assertThrows(ShaderCompatibilityException.class,()->NativeTerrainProgram.adapt(V,source(helper),List.of(0)));
    }
    @Test void rawSamplerForwardingAndShadowFeedbackRemainRejected() {
        String f=source("float readShadow(sampler2DShadow image,vec3 p){return texture(image,p);}");
        assertThrows(ShaderCompatibilityException.class,()->NativeTerrainProgram.adaptShadow(V,f,List.of(0)));
        assertThrows(ShaderCompatibilityException.class,()->NativeTerrainProgram.adapt(V,
            f.replace("sampler2DShadow","sampler2D"),List.of(0)));
    }

    @Test void bothSnapshotsReuseOneComparisonSpecialization() throws Exception {
        var p=NativeTerrainProgram.adapt(V,source(
            "float readShadow(sampler2DShadow image,vec3 p){return texture(image,p);}")
            .replace("void main()", "uniform sampler2DShadow shadowtex1;\nfloat separate(){return readShadow(shadowtex1,vec3(.5));}\nvoid main()")
            .replace("vec4(readShadow(shadowtex0,vec3(.5)))", "vec4(readShadow(shadowtex0,vec3(.5))+separate())"),List.of(0));
        assertTrue(p.usesOpaqueShadowDepth());assertTrue(p.usesShadowDepth());
        assertTrue(p.fragmentSource().contains("oculusNativeShadowHelper0(OculusOpaqueShadowDepth,"));
        assertTrue(p.fragmentSource().contains("oculusNativeShadowHelper0(OculusShadowDepth,"));
        assertEquals(1,p.fragmentSource().split("float oculusNativeShadowHelper0\\(",-1).length-1);
    }
    @Test void helperCountIsBoundedAndUnclosedBodiesReject() {
        StringBuilder f=new StringBuilder("uniform sampler2DShadow shadowtex0;\n");
        StringBuilder sum=new StringBuilder();
        for(int i=0;i<17;i++){
            f.append("float h").append(i).append("(sampler2DShadow s,vec3 p){return texture(s,p);}\n");
            if(i>0)sum.append("+");
            sum.append("h").append(i).append("(shadowtex0,vec3(.5))");
        }
        f.append("void main(){gl_FragColor=vec4(").append(sum).append(");}");
        var error=assertThrows(ShaderCompatibilityException.class,()->NativeTerrainProgram.adapt(V,f.toString(),List.of(0)));
        assertTrue(error.getMessage().contains("excessive"));
        assertThrows(ShaderCompatibilityException.class,()->NativeTerrainProgram.adapt(V,source(
            "float readShadow(sampler2DShadow image,vec3 p){return texture(image,p);"),List.of(0)));
    }
}
