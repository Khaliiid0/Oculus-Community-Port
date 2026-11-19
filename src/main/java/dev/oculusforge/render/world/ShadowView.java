package dev.oculusforge.render.world;

import org.joml.Matrix4f;

/** Camera-relative light view matching SkyRenderer's Y(-90) * X(celestialAngle) pose. */
public final class ShadowView {
    private static final float HALF_PI = (float) (Math.PI / 2.0);

    private ShadowView() { }

    public static Matrix4f fromCelestialAngles(float sunAngle, float moonAngle) {
        if (!Float.isFinite(sunAngle) || !Float.isFinite(moonAngle))
            throw new IllegalArgumentException("Non-finite celestial shadow angle");
        float angle = Math.cos(sunAngle) >= 0.0 ? sunAngle : moonAngle;
        // The celestial is on local +Y. Undo its sky pose, then map +Y to
        // view +Z so rays travel along -Z and the nearest caster wins.
        return new Matrix4f().rotationX(HALF_PI - angle).rotateY(HALF_PI);
    }
}
