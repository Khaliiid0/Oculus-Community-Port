package dev.oculusforge.render.program;

import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LegacyProjectionTest {
    @Test void nearAndFarMatchLegacyDepthOnBothDeviceConventions() {
        for (boolean zeroToOne : new boolean[]{false, true}) {
            var nativeMatrix = new Matrix4f().perspective(1.2f, 1.7f, 256f, 0.05f, zeroToOne);
            var before = new Matrix4f(nativeMatrix);
            var legacy = LegacyProjection.fromReverseDepth(nativeMatrix, zeroToOne);
            for (float distance : new float[]{0.05f, 1f, 12f, 256f}) {
                var point = new Vector4f(0.01f, -0.02f, -distance, 1);
                var raw = nativeMatrix.transform(new Vector4f(point));
                var clip = legacy.transform(new Vector4f(point));
                float depth = zeroToOne ? raw.z / raw.w : raw.z / raw.w * 0.5f + 0.5f;
                assertEquals(1f - depth, clip.z / clip.w * 0.5f + 0.5f, 0.00001f);
                assertEquals(raw.x, clip.x, 0f);
                assertEquals(raw.y, clip.y, 0f);
                assertEquals(raw.w, clip.w, 0f);
                var restored = legacy.invert(new Matrix4f()).transform(new Vector4f(clip).div(clip.w));
                restored.div(restored.w);
                assertEquals(point.z, restored.z, 0.1f);
            }
            assertEquals(before, nativeMatrix);
        }
    }

    @Test void convertedInverseReconstructsFarSkyRatherThanNearPlane() {
        var nativeMatrix = new Matrix4f().perspective(1.2f, 1.7f, 256f, 0.05f, true);
        var sky = LegacyProjection.fromReverseDepth(nativeMatrix, true).invert()
            .transform(new Vector4f(0, 0, 1, 1));
        assertEquals(-256f, sky.z / sky.w, 0.1f);
    }
}
