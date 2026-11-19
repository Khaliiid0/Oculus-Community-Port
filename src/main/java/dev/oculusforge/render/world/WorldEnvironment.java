package dev.oculusforge.render.world;

/** Immutable camera-extraction values, independent of whether a sky is drawn. */
public record WorldEnvironment(int moonPhase, float rainStrength, float nightVision, EyeBrightness eyeBrightness,
                               int biomeCode,float blindness,float darknessFactor,float cloudHeight,int precipitation,
                               AtlasSize atlasSize,HeldItemKeys heldItems,float darknessLightFactor,NativeFogData fog,SkyColor skyColor) {
    public WorldEnvironment(int moonPhase,float rainStrength,float nightVision,EyeBrightness eyeBrightness,
                            int biomeCode,float blindness,float darknessFactor,float cloudHeight,int precipitation,
                            AtlasSize atlasSize,HeldItemKeys heldItems,float darknessLightFactor,NativeFogData fog) {
        this(moonPhase,rainStrength,nightVision,eyeBrightness,biomeCode,blindness,darknessFactor,cloudHeight,precipitation,
            atlasSize,heldItems,darknessLightFactor,fog,SkyColor.BLACK);
    }
    public WorldEnvironment(int moonPhase,float rainStrength,float nightVision,EyeBrightness eyeBrightness,
                            int biomeCode,float blindness,float darknessFactor,float cloudHeight,int precipitation,
                            AtlasSize atlasSize,HeldItemKeys heldItems,float darknessLightFactor) {
        this(moonPhase,rainStrength,nightVision,eyeBrightness,biomeCode,blindness,darknessFactor,cloudHeight,precipitation,
            atlasSize,heldItems,darknessLightFactor,NativeFogData.CLEAR);
    }
    public WorldEnvironment withFog(NativeFogData value) {
        return withFogAndSkyColor(value,skyColor);
    }
    public WorldEnvironment withSkyColor(SkyColor value) {
        return withFogAndSkyColor(fog,value);
    }
    public WorldEnvironment withFogAndSkyColor(NativeFogData newFog,SkyColor newSky) {
        return new WorldEnvironment(moonPhase,rainStrength,nightVision,eyeBrightness,biomeCode,blindness,darknessFactor,
            cloudHeight,precipitation,atlasSize,heldItems,darknessLightFactor,newFog,newSky);
    }
    public WorldEnvironment(int moonPhase,float rainStrength,float nightVision,EyeBrightness eyeBrightness,
                            int biomeCode,float blindness,float darknessFactor,float cloudHeight,int precipitation,
                            AtlasSize atlasSize,HeldItemKeys heldItems) {
        this(moonPhase,rainStrength,nightVision,eyeBrightness,biomeCode,blindness,darknessFactor,cloudHeight,precipitation,
            atlasSize,heldItems,0);
    }
    public WorldEnvironment(int moonPhase,float rainStrength,float nightVision,EyeBrightness eyeBrightness,
                            int biomeCode,float blindness,float darknessFactor,float cloudHeight,int precipitation) {
        this(moonPhase,rainStrength,nightVision,eyeBrightness,biomeCode,blindness,darknessFactor,cloudHeight,precipitation,
            AtlasSize.ABSENT,HeldItemKeys.NONE);
    }
    public WorldEnvironment(int moonPhase,float rainStrength,float nightVision,EyeBrightness eyeBrightness,
                            int biomeCode,float blindness,float darknessFactor,float cloudHeight) {
        this(moonPhase,rainStrength,nightVision,eyeBrightness,biomeCode,blindness,darknessFactor,cloudHeight,0);
    }
    public WorldEnvironment(int moonPhase,float rainStrength,float nightVision,EyeBrightness eyeBrightness,
                            int biomeCode,float blindness,float darknessFactor) {
        this(moonPhase,rainStrength,nightVision,eyeBrightness,biomeCode,blindness,darknessFactor,0);
    }
    public WorldEnvironment(int moonPhase,float rainStrength,float nightVision,EyeBrightness eyeBrightness,int biomeCode) {
        this(moonPhase,rainStrength,nightVision,eyeBrightness,biomeCode,0,0);
    }
    public static final WorldEnvironment CLEAR = new WorldEnvironment(0, 0);
    public WorldEnvironment(int moonPhase, float rainStrength) {
        this(moonPhase, rainStrength, 0);
    }
    public WorldEnvironment(int moonPhase, float rainStrength, float nightVision) {
        this(moonPhase, rainStrength, nightVision, EyeBrightness.DARK);
    }
    public WorldEnvironment(int moonPhase, float rainStrength, float nightVision, EyeBrightness eyeBrightness) {
        this(moonPhase,rainStrength,nightVision,eyeBrightness,0);
    }
    public WorldEnvironment {
        java.util.Objects.requireNonNull(eyeBrightness, "eyeBrightness");
        java.util.Objects.requireNonNull(atlasSize, "atlasSize");
        java.util.Objects.requireNonNull(heldItems, "heldItems");
        java.util.Objects.requireNonNull(fog, "fog");
        java.util.Objects.requireNonNull(skyColor, "skyColor");
        DarknessLight.checked(darknessLightFactor);
        if (!NativeBiomeSymbols.valid(biomeCode) || moonPhase < 0 || moonPhase > 7 || !Float.isFinite(rainStrength)
            || rainStrength < 0 || rainStrength > 1
            || !Float.isFinite(nightVision) || nightVision < 0 || nightVision > 1
            || !Float.isFinite(blindness)||blindness<0||blindness>1
            || !Float.isFinite(darknessFactor)||darknessFactor<0||darknessFactor>1 || !Float.isFinite(cloudHeight)
            || precipitation<0||precipitation>2)
            throw new IllegalArgumentException("Invalid world environment snapshot");
    }
}
