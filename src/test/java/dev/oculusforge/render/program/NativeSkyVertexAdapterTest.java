package dev.oculusforge.render.program;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeSkyVertexAdapterTest {
    private static final String VERTEX = """
        #version 120
        varying vec2 uv;
        varying vec4 tint;
        void main() {
            uv = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
            tint = gl_Color;
            gl_Position = ftransform();
        }
        """;
    private final NativeSkyVertexAdapter adapter = new NativeSkyVertexAdapter();

    @Test void bindsSkyMeshWithoutChunkOffsets() throws Exception {
        var p = adapter.adapt(VERTEX);
        assertTrue(p.source().contains("ProjMat * ModelViewMat * vec4(Position, 1.0)"));
        assertTrue(p.source().contains("TextureMat * vec4(UV0, 0.0, 1.0)"));
        assertFalse(p.source().contains("ChunkPosition"));
        assertFalse(p.source().contains("CameraOffset"));
        assertEquals("vec2", p.outputs().get("uv"));
        assertFalse(p.usesWorldMatrices());
    }
    @Test void stageIsBoundNotFabricated() throws Exception {
        var p = adapter.adapt("uniform int renderStage;\n" + VERTEX.replace("tint = gl_Color;", "tint = vec4(float(renderStage));"));
        assertTrue(p.source().contains("float(oculusSkyRenderStage)"));
        assertTrue(p.source().contains("uniform OculusSkyStage"));
    }
    @Test void worldInputsUseSharedLayout() throws Exception {
        var p = adapter.adapt("uniform mat4 gbufferModelView;\n" + VERTEX.replace("tint = gl_Color;", "tint = gbufferModelView[1];"));
        assertTrue(p.usesWorldMatrices());
        assertTrue(p.source().contains(NativeWorldBindings.block()));
        assertTrue(p.source().contains("tint = oculusNative_gbufferModelView[1]"));
    }
    @Test void rejectsTerrainOnlyAttributesAndUnsupportedBuiltins() {
        for (String expression : new String[]{"gl_Normal", "gl_MultiTexCoord1", "gl_TextureMatrix[1]"})
            assertThrows(ShaderCompatibilityException.class, () -> adapter.adapt(VERTEX.replace("gl_Color", expression)));
        assertThrows(ShaderCompatibilityException.class, () -> adapter.adapt("attribute vec4 at_tangent;\n" + VERTEX));
    }
    @Test void rejectsInvalidOrMissingStage() {
        for (String prefix : new String[]{"uniform float renderStage;\n", "uniform int renderStage;\nuniform int renderStage;\n", ""})
            assertThrows(ShaderCompatibilityException.class, () -> adapter.adapt(prefix + VERTEX.replace("gl_Color", "vec4(renderStage)")));
    }
    @Test void rejectsReservedNamesAndInvalidEntryPoints() {
        assertThrows(ShaderCompatibilityException.class, () -> adapter.adapt(VERTEX + "\nfloat oculusSkyRenderStage;"));
        assertThrows(ShaderCompatibilityException.class, () -> adapter.adapt(VERTEX.replace("void main()", "void other()")));
        assertThrows(ShaderCompatibilityException.class, () -> adapter.adapt(VERTEX + "\nvoid main() {}"));
    }
    @Test void validatesVaryings() {
        assertThrows(ShaderCompatibilityException.class, () -> adapter.adapt("varying vec2 uv;\n" + VERTEX));
        assertThrows(ShaderCompatibilityException.class, () -> adapter.adapt(VERTEX.replace("varying vec2 uv;", "varying mat4 uv;")));
    }
}
