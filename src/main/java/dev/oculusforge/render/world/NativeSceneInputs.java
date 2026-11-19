package dev.oculusforge.render.world;

/** Real frame values supplied by the renderer; convenience constructors use DEFAULT only in CPU fixtures. */
public record NativeSceneInputs(float screenBrightness, float sunAngle) {
    public static final NativeSceneInputs DEFAULT = new NativeSceneInputs(0, 0);

    public NativeSceneInputs {
        if (!Float.isFinite(screenBrightness) || !Float.isFinite(sunAngle) || sunAngle < 0 || sunAngle >= 1)
            throw new IllegalArgumentException("Invalid native scene inputs");
    }

    public static NativeSceneInputs fromSkyAngle(float screenBrightness, float radians) {
        return new NativeSceneInputs(screenBrightness, sunAngleTurns(radians));
    }

    /** Legacy shader convention: sunrise=0, noon=.25, sunset=.5, midnight=.75. */
    public static float sunAngleTurns(float radians) {
        if (!Float.isFinite(radians)) throw new IllegalArgumentException("Non-finite celestial angle");
        double turns = (radians + Math.PI / 2) / (Math.PI * 2);
        float wrapped = (float) (turns - Math.floor(turns));
        return wrapped >= 1 ? 0 : wrapped;
    }
}
