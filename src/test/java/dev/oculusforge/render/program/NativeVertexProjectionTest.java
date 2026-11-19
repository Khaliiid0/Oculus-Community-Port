package dev.oculusforge.render.program;

import dev.oculusforge.shaderpack.ShaderPackDescriptor;
import dev.oculusforge.shaderpack.ShaderPackLimits;
import java.nio.file.Path;
import java.util.List;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeVertexProjectionTest {
    private static final String F = "void main(){gl_FragColor=vec4(1);}";

    @Test void terrainBuiltinAndNamedProjectionReadsConvertBeforePackMain() throws Exception {
        var p = NativeTerrainProgram.adapt("uniform mat4 gbufferProjection; uniform mat4 gbufferProjectionInverse; "
            + "void main(){gl_Position=ftransform()+gl_ProjectionMatrix*gbufferProjectionInverse*gbufferProjection*gl_Vertex; return;}",
            F, List.of(0));
        String v = p.vertexSource();
        assertTrue(v.contains("oculusNativeVertexProjection(ProjMat) * ModelViewMat"));
        assertTrue(v.contains("oculusNativeVertexProjection(oculusNative_gbufferProjection)"));
        assertTrue(v.contains("oculusNativeVertexProjectionInverse(oculusNative_gbufferProjectionInverse)"));
        assertTrue(v.contains("uniform Projection { mat4 ProjMat; };"));
        assertTrue(v.contains("mat4 oculusNative_gbufferProjection;"));
        assertEquals(1, v.split("mat4 oculusNativeVertexProjection\\(", -1).length - 1);
        assertTrue(v.indexOf("oculusNativeMain();") < v.indexOf(NativeProjection.CLIP_CONVERSION));
        assertFalse(p.fragmentSource().contains("oculusNativeVertexProjection"));
    }

    @Test void roundtripKeepsNativeRasterizedPositionAndLegacyVaryingDepth() {
        for (boolean zeroToOne : new boolean[]{false, true}) {
            var raw = new Matrix4f().perspective(1.2f, 1.7f, 256f, 0.05f, zeroToOne);
            var legacy = LegacyProjection.fromReverseDepth(raw, zeroToOne);
            var conversion = new Matrix4f().m22(zeroToOne ? -0.5f : -1f).m32(zeroToOne ? 0.5f : 0f);
            for (float distance : new float[]{0.05f, 1f, 16f, 128f, 256f}) {
                var point = new Vector4f(0.01f, 0.02f, -distance, 1);
                var packClip = legacy.transform(new Vector4f(point));
                var nativeClip = conversion.transform(new Vector4f(packClip));
                assertTrue(nativeClip.equals(raw.transform(new Vector4f(point)), 0.00003f));
                float nativeDepth = zeroToOne ? nativeClip.z / nativeClip.w : nativeClip.z / nativeClip.w * 0.5f + 0.5f;
                assertEquals(packClip.z / packClip.w * 0.5f + 0.5f, 1 - nativeDepth, 0.000001f);
                var biasedClip = conversion.transform(new Vector4f(packClip).add(0, 0, -0.001f, 0));
                assertTrue(biasedClip.z > nativeClip.z, "Legacy towards-camera bias must become a larger native reverse depth");
            }
        }
    }

    @Test void shadowKeepsItsOwnConventionalProjectionAndSingleClipConversion() throws Exception {
        var p = NativeTerrainProgram.adaptShadow("uniform mat4 shadowProjection; uniform mat4 shadowModelView; "
            + "void main(){gl_Position=shadowProjection*shadowModelView*gl_Vertex+ftransform();}", F, List.of(0));
        assertTrue(p.vertexSource().contains(ShadowClipSpace.CONVERSION));
        assertFalse(p.vertexSource().contains(NativeProjection.CLIP_CONVERSION));
        assertFalse(p.vertexSource().contains("oculusNativeVertexProjection("));
        assertTrue(p.vertexSource().contains("gl_Position=oculusNative_shadowProjection*oculusNative_shadowModelView*oculusNativeVertex+(ModelViewMat * oculusNativeVertex)"));
    }

    @Test void projectedVaryingProbePreparesTheProductionTerrainPath() throws Exception {
        var resource = getClass().getClassLoader().getResource("native-projected-varyings");
        assertNotNull(resource);
        var prepared = new PackProgramCompiler(ShaderPackLimits.DEFAULT).prepare(new ShaderPackDescriptor(
            "varying-probe", "Projected varying probe", Path.of(resource.toURI()), ShaderPackDescriptor.Kind.DIRECTORY));
        var world = prepared.world().variant("shaders");
        assertTrue(world.layerRuntimeEligible());
        var p = world.nativeTerrain();
        assertNotNull(p, world.nativeTerrainIssue());
        assertTrue(p.vertexSource().contains("out vec4 namedClip;"));
        assertTrue(p.vertexSource().contains("oculusNativeVertexProjection(oculusNative_gbufferProjection)"));
        assertTrue(p.fragmentSource().contains("oculusNativeFragmentProjectionInverse(oculusNative_gbufferProjectionInverse)"));
        assertTrue(p.fragmentSource().contains("oculusNativeFragmentCoord()"));
    }
}
