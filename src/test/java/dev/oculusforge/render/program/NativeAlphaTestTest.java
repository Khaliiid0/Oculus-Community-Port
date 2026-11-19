package dev.oculusforge.render.program;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeAlphaTestTest {
    private static final String VERTEX = "void main() { gl_Position = ftransform(); }";

    @Test void terrainAndShadowApplyPipelineThresholdAfterPackColor() throws Exception {
        for (boolean shadow : new boolean[]{false, true}) {
            var program = NativeTerrainProgram.adaptWithMesh(VERTEX,
                "void main() { gl_FragColor = vec4(1.0, 1.0, 1.0, 0.0); }",
                List.of(0), shadow, false);
            String source = program.fragmentSource();
            assertTrue(source.contains("void oculusNativeFragmentMain()"));
            assertTrue(source.contains("#ifdef ALPHA_CUTOUT"));
            assertTrue(source.contains("if (oculus_FragColor0.a < ALPHA_CUTOUT) discard;"));
            assertTrue(source.indexOf("oculusNativeFragmentMain();") < source.indexOf("if (oculus_FragColor0.a"));
        }
    }

    @Test void earlyReturnsAndExplicitDiscardStayInsidePackFunction() throws Exception {
        String fragment = "void main(void) { if (gl_FragCoord.x < 1.0) discard; "
            + "gl_FragData[0] = vec4(0.25); return; }";
        String source = NativeTerrainProgram.adaptWithMesh(VERTEX, fragment, List.of(0), false, false).fragmentSource();
        assertTrue(source.contains("if (oculusNativeFragmentCoord().x < 1.0) discard;"));
        assertTrue(source.indexOf("return;") < source.indexOf("void main()"));
        assertEquals(1, source.split("void main\\(\\)", -1).length - 1);
    }

    @Test void mrtUsesLocationZeroEvenWhenItRoutesToAuxiliaryBuffer() throws Exception {
        String source = NativeTerrainProgram.adaptWithMesh(VERTEX,
            "void main() { gl_FragData[0] = vec4(0.25); gl_FragData[1] = vec4(1.0); }",
            List.of(7, 0), false, false).fragmentSource();
        assertTrue(source.contains("if (oculus_FragColor0.a < ALPHA_CUTOUT)"));
        assertFalse(source.contains("if (oculus_FragColor1.a"));
    }

    @Test void noColorZeroDoesNotInventAnAlphaRead() throws Exception {
        String source = "#version 330 core\nvoid main() { }";
        assertEquals(source, NativeAlphaTest.adapt(source));
        String auxiliary = "layout(location=1) out vec4 oculus_FragColor1;\nvoid main() { oculus_FragColor1=vec4(1); }";
        assertEquals(auxiliary, NativeAlphaTest.adapt(auxiliary));
    }
}
