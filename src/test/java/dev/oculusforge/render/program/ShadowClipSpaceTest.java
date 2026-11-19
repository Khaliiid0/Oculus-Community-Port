package dev.oculusforge.render.program;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShadowClipSpaceTest {
    private static final String V = "void main() { gl_Position = ftransform(); return; }";
    private static final String F = "void main() { gl_FragColor = vec4(1); }";

    @Test void shadowConversionRunsAfterPackMainButTerrainIsUnchanged() throws Exception {
        var shadow = NativeTerrainProgram.adaptShadow(V, F, List.of(0)).vertexSource();
        assertTrue(shadow.contains(ShadowClipSpace.CONVERSION));
        assertTrue(shadow.indexOf("oculusNativeMain();") < shadow.indexOf("#ifdef OCULUS_SHADOW_ZERO_TO_ONE"));
        var terrain = NativeTerrainProgram.adapt(V, F, List.of(0)).vertexSource();
        assertFalse(terrain.contains(ShadowClipSpace.ZERO_TO_ONE_DEFINE));
    }

    @Test void conversionPreservesLegacySampleDepthAndNearWinsOnBothDevices() {
        for (boolean zeroToOne : new boolean[]{false, true}) {
            float previous = Float.POSITIVE_INFINITY;
            for (float w : new float[]{1, 2, 7}) {
                previous = Float.POSITIVE_INFINITY;
                for (float ndc : new float[]{-1, -0.5f, 0, 0.5f, 1}) {
                    float z = ndc * w;
                    float converted = zeroToOne ? (w - z) * 0.5f : -z;
                    float stored = zeroToOne ? converted / w : converted / w * 0.5f + 0.5f;
                    assertEquals(ndc * 0.5f + 0.5f, 1 - stored, 0.000001f);
                    assertTrue(stored < previous, "Nearer fragment must win GREATER depth test");
                    assertTrue(stored >= 0 && stored <= 1);
                    previous = stored;
                }
            }
        }
    }

    @Test void packsCannotOverrideDeviceDepthDefine() {
        assertThrows(ShaderCompatibilityException.class, () -> NativeTerrainProgram.adaptShadow(
            "#define OCULUS_SHADOW_ZERO_TO_ONE\n" + V, F, List.of(0)));
    }
}
