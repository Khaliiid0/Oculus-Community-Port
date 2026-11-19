package dev.oculusforge.render.world;

import net.minecraft.world.level.material.FogType;

/** Immutable camera fog values shared with the post-process uniform semantics. */
public record NativeFogData(int medium, float red, float green, float blue, float alpha, float start, float end) {
    /** CPU convenience value; runtime always captures the completed camera fog. */
    public static final NativeFogData CLEAR = new NativeFogData(0,0,0,0,0,0,1);
    public NativeFogData(int medium,float red,float green,float blue) { this(medium,red,green,blue,0,0,1); }
    public NativeFogData {
        if(medium<0 || medium>3 || !Float.isFinite(red) || !Float.isFinite(green) || !Float.isFinite(blue)
            || !Float.isFinite(alpha) || !Float.isFinite(start) || !Float.isFinite(end))
            throw new IllegalArgumentException("Invalid native camera fog");
    }
    /** Iris's linear fog scale; deliberately no epsilon clamp or bound reordering. */
    public float scale() { return 1.0f/(end-start); }
    public static NativeFogData capture(net.minecraft.client.renderer.state.level.CameraRenderState camera) {
        var data=java.util.Objects.requireNonNull(camera.fogData,"camera fog");
        return new NativeFogData(eyeMedium(camera.fogType),data.color.x,data.color.y,data.color.z,data.color.w,
            data.environmentalStart,data.environmentalEnd);
    }
    /** Camera.extractRenderState runs BEFORE GameRenderer finishes fog extraction. */
    public static void finishFrame(WorldEnvironmentState target,
        net.minecraft.client.renderer.state.level.CameraRenderState camera) {
        var environment=target.oculus$getEnvironment();
        finishFrame(target,camera,environment==null?SkyColor.BLACK:environment.skyColor());
    }
    public static void finishFrame(WorldEnvironmentState target,
        net.minecraft.client.renderer.state.level.CameraRenderState camera,SkyColor skyColor) {
        var environment=target.oculus$getEnvironment();
        target.oculus$setEnvironment(null);
        if(environment==null) return;
        try { target.oculus$setEnvironment(environment.withFogAndSkyColor(capture(camera),skyColor)); }
        catch(IllegalArgumentException invalidFog) { /* Native and post defer together, never reuse an old snapshot. */ }
    }
    public static int eyeMedium(FogType type) {
        return switch(type) {
            case WATER -> 1;
            case LAVA -> 2;
            case POWDER_SNOW -> 3;
            case ATMOSPHERIC, NONE -> 0;
        };
    }
}
