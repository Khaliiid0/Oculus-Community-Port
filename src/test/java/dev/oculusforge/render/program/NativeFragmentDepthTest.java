package dev.oculusforge.render.program;

import java.util.List;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeFragmentDepthTest {
    private static final String V = "uniform mat4 gbufferProjection; void main(){gl_Position=gbufferProjection*gl_Vertex;}";
    private static final String F = "uniform mat4 gbufferProjection; uniform mat4 gbufferProjectionInverse; "
        + "void main(){gl_FragColor=gbufferProjection*gbufferProjectionInverse*gl_FragCoord;}";

    @Test void bothStagesUseLegacyExpressionsWithoutChangingUniformLayout() throws Exception {
        var p = NativeTerrainProgram.adapt(V, F, List.of(0));
        assertFalse(p.vertexSource().contains("oculusNativeFragment"));
        assertTrue(p.vertexSource().contains("gl_Position=oculusNativeVertexProjection(oculusNative_gbufferProjection)*oculusNativeVertex"));
        assertTrue(p.vertexSource().contains(NativeProjection.CLIP_CONVERSION));
        assertTrue(p.fragmentSource().contains("mat4 oculusNative_gbufferProjection;"));
        assertTrue(p.fragmentSource().contains("oculusNativeFragmentProjection(oculusNative_gbufferProjection)"));
        assertTrue(p.fragmentSource().contains("oculusNativeFragmentProjectionInverse(oculusNative_gbufferProjectionInverse)"));
        assertTrue(p.fragmentSource().contains("vec4(gl_FragCoord.xy, 1.0 - gl_FragCoord.z, gl_FragCoord.w)"));
    }

    @Test void convertedFragmentDepthReconstructsNativeViewPosition() {
        for (boolean zeroToOne : new boolean[]{false,true}) {
            var raw = new Matrix4f().perspective(1.2f,1.7f,256f,0.05f,zeroToOne);
            var inverseConversion = new Matrix4f().m22(zeroToOne ? -0.5f : -1f).m32(zeroToOne ? 0.5f : 0f);
            var inverse = new Matrix4f(raw).invert().mul(inverseConversion);
            assertTrue(inverse.equals(LegacyProjection.fromReverseDepth(raw,zeroToOne).invert(),0.0001f));
            for (float distance : new float[]{0.05f,1f,16f,128f,256f}) {
                var point = new Vector4f(0.01f,0.02f,-distance,1);
                var clip = raw.transform(new Vector4f(point));
                float depth = zeroToOne ? clip.z/clip.w : clip.z/clip.w*0.5f+0.5f;
                var restored = inverse.transform(new Vector4f(clip.x/clip.w,clip.y/clip.w,2*(1-depth)-1,1));
                restored.div(restored.w);
                assertEquals(point.z,restored.z,0.15f);
                assertEquals(point.x,restored.x,0.0001f);
                assertEquals(point.y,restored.y,0.0001f);
            }
        }
    }

    @Test void noDepthInputsMeansNoHelpers() throws Exception {
        String source="#version 330 core\nvoid main(){}";
        assertEquals(source,NativeFragmentDepth.adapt(source));
    }

    @Test void deviceDefineIsReservedAndShadowFragmentGetsLegacyDepth() throws Exception {
        assertThrows(ShaderCompatibilityException.class,()->NativeTerrainProgram.adapt(V,
            "#define OCULUS_FRAGMENT_ZERO_TO_ONE\n"+F,List.of(0)));
        var shadow=NativeTerrainProgram.adaptShadow(V,F,List.of(0));
        assertTrue(shadow.fragmentSource().contains("oculusNativeFragmentCoord()"));
        assertTrue(shadow.vertexSource().contains(ShadowClipSpace.CONVERSION));
    }
}
