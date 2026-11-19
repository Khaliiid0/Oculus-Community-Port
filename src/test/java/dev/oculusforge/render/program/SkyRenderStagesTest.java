package dev.oculusforge.render.program;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SkyRenderStagesTest {
    private static final String V = "void main() { gl_Position = ftransform(); }";
    private static final String F = "void main() { gl_FragData[0] = vec4(1); }";
    @Test void skyStageConstantsAreDistinctAndStable() {
        assertEquals("1", SkyRenderStages.macros().get("MC_RENDER_STAGE_SKY"));
        assertEquals("4", SkyRenderStages.macros().get("MC_RENDER_STAGE_SUN"));
        assertEquals("5", SkyRenderStages.macros().get("MC_RENDER_STAGE_MOON"));
        assertEquals("6", SkyRenderStages.macros().get("MC_RENDER_STAGE_STARS"));
        assertEquals(8, SkyRenderStages.macros().values().stream().distinct().count());
    }
    @Test void specializationRemovesStageBufferFromBothSources() throws Exception {
        var p = NativeSkyProgram.adapt(V, F, List.of(0));
        for (String source : List.of(p.vertex().source(), p.fragmentSource())) {
            String specialized = SkyRenderStages.specializeEndSky(source);
            assertFalse(specialized.contains("uniform OculusSkyStage"));
            assertTrue(specialized.contains("const int oculusSkyRenderStage = 1;"));
        }
        assertThrows(IllegalArgumentException.class, () -> SkyRenderStages.specializeEndSky(V));
    }
    @Test void endSkyRejectsAuxiliaryAndNoiseResources() throws Exception {
        assertFalse(SkyRenderStages.endSkyEligible(null));
        assertTrue(SkyRenderStages.endSkyEligible(NativeSkyProgram.adapt(V,F,List.of(0))));
        for (String sampler : List.of("texture", "gaux1", "noisetex")) {
            var p = NativeSkyProgram.adapt(V, "uniform sampler2D " + sampler + ";\n" +
                F.replace("vec4(1)", "texture2D(" + sampler + ", vec2(0))"), List.of(0));
            assertEquals(sampler.equals("texture"), SkyRenderStages.endSkyEligible(p));
        }
    }
}
