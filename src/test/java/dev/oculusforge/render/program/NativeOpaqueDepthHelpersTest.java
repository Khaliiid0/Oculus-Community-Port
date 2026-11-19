package dev.oculusforge.render.program;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

final class NativeOpaqueDepthHelpersTest {
    private static final String V="void main(){gl_Position=ftransform();}";
    private static final String HELPER="vec4 ray(sampler2D image, vec2 uv, out float border, int steps) { border=0.0; for(int i=0;i<steps;i++){border+=texture2D(image,uv).r;} return vec4(border); }\n";
    private static String shader(String helper) {
        return "uniform sampler2D depthtex1;\n"+helper+"void main(){float b;gl_FragColor=ray(depthtex1,vec2(.5),b,2);}";
    }
    @Test void specializesRayStyleLoopsAndOutParameters() throws Exception {
        var p=NativeTerrainProgram.adapt(V,shader(HELPER),List.of(0));
        assertTrue(p.usesOpaqueWorldDepth());
        assertTrue(p.fragmentSource().contains("oculusNativeWorldDepthHelper0(OculusOpaqueWorldDepth,"));
        assertTrue(p.fragmentSource().contains("border+=oculusNativeWorldDepthSample(image,uv).r"));
        assertEquals(1,p.fragmentSource().split("1.0-value.r",-1).length-1);
    }
    @Test void preservesOtherTextureCallersAndOriginalFunction() throws Exception {
        String source=shader(HELPER).replace("uniform sampler2D depthtex1;", "uniform sampler2D depthtex1;\nuniform sampler2D gaux2;")
            .replace("gl_FragColor=ray(depthtex1,vec2(.5),b,2);","gl_FragColor=ray(depthtex1,vec2(.5),b,2)+ray(gaux2,vec2(.5),b,2);");
        var p=NativeTerrainProgram.adapt(V,source,List.of(0));
        assertTrue(p.fragmentSource().contains("ray(OculusColor5,"));
        assertTrue(p.fragmentSource().contains("border+=texture(image,uv).r") || p.fragmentSource().contains("border+=texture2D(image,uv).r"));
        assertEquals(List.of(5),p.colorInputs());
    }
    @Test void supportsAllDirectLeafReadForms() throws Exception {
        for(String call:List.of("texture(image,uv)","texture2DLod(image,uv,0.0)","textureLod(image,uv,0.0)","texelFetch(image,ivec2(uv),0)")) {
            String adapted=NativeOpaqueDepth.adapt(shader(HELPER.replace("texture2D(image,uv)",call)),false);
            assertTrue(adapted.contains("oculusNativeWorldDepthHelper0(OculusOpaqueWorldDepth,"));
        }
    }
    @Test void rejectsNestedForwardingUnsupportedReadsAndAmbiguity() {
        for(String helper:List.of(HELPER.replace("texture2D(image,uv)","other(image,uv)"),
            HELPER.replace("texture2D(image,uv)","vec4(textureSize(image,0),0,1)"),
            HELPER+HELPER, HELPER+"vec4 ray(float x){return vec4(x);}",
            HELPER.replace("out float border","out float border[2]"),
            HELPER.replace("texture2D(image,uv)","ray(image,uv,border,steps)")))
            assertThrows(ShaderCompatibilityException.class,()->NativeOpaqueDepth.adapt(shader(helper),false));
    }
    @Test void leavesUnrelatedHelpersAloneAndRejectsShadowUse() throws Exception {
        assertEquals(HELPER,NativeOpaqueDepthHelpers.adapt(HELPER).source());
        assertThrows(ShaderCompatibilityException.class,()->NativeOpaqueDepth.adapt(shader(HELPER),true));
    }
}
