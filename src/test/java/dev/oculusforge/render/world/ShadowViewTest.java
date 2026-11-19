package dev.oculusforge.render.world;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShadowViewTest {
    private static final float HALF_PI = (float) (Math.PI / 2.0);

    private static Vector3f celestial(float angle) {
        // Independent reproduction of the game's actual sky draw pose.
        return new Matrix4f().rotationY(-HALF_PI).rotateX(angle)
            .transformDirection(new Vector3f(0, 1, 0));
    }

    @Test void daylightViewFacesTheVisibleSunThroughoutItsArc() {
        for (float angle : new float[]{-1.4f, -0.8f, 0, 0.8f, 1.4f}) {
            var view = ShadowView.fromCelestialAngles(angle, angle + (float) Math.PI);
            assertTrue(view.transformDirection(celestial(angle)).equals(new Vector3f(0, 0, 1), 0.000001f));
            assertEquals(1.0f, view.determinant(), 0.000001f);
            assertTrue(new Matrix4f(view).mul(new Matrix4f(view).invert()).equals(new Matrix4f(), 0.000001f));
        }
    }

    @Test void nighttimeViewUsesTheActualMoonAngle() {
        for (float moonAngle : new float[]{-0.9f, 0, 0.9f}) {
            var view = ShadowView.fromCelestialAngles((float) Math.PI, moonAngle);
            assertTrue(view.transformDirection(celestial(moonAngle)).equals(new Vector3f(0, 0, 1), 0.000001f));
        }
    }

    @Test void noonCasterAboveReceiverWinsConventionalAndReversedDepth() {
        var view = ShadowView.fromCelestialAngles(0, (float) Math.PI);
        var projection = new Matrix4f().ortho(-64, 64, -64, 64, -256, 256);
        var matrix = new Matrix4f(projection).mul(view);
        var receiver = matrix.transform(new Vector4f(0, 0, 0, 1));
        var caster = matrix.transform(new Vector4f(0, 8, 0, 1));
        assertEquals(receiver.x, caster.x, 0.000001f);
        assertEquals(receiver.y, caster.y, 0.000001f);
        assertTrue(caster.z < receiver.z, "Caster must be closer along the noon light ray");
        assertTrue((1 - caster.z) * 0.5f > (1 - receiver.z) * 0.5f,
            "The same caster must win the native reverse-depth test");
        // Previous orientation looked sideways at noon and separated these surfaces in XY.
        var oldView = new Matrix4f().rotateY((float) Math.toRadians(45));
        assertTrue(oldView.transformDirection(new Vector3f(0, 1, 0)).distance(new Vector3f(0, 0, 1)) > 1);
    }

    @Test void nonFiniteAnglesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> ShadowView.fromCelestialAngles(Float.NaN, 0));
        assertThrows(IllegalArgumentException.class, () -> ShadowView.fromCelestialAngles(0, Float.POSITIVE_INFINITY));
    }
}
