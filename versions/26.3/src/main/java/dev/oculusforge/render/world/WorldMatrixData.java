package dev.oculusforge.render.world;

import dev.oculusforge.render.program.NativeWorldBindings;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

/** Immutable frame snapshot in the exact coordinates used for the current chunk draws. */
public final class WorldMatrixData {
    private final float[] matrices = new float[128];
    private final float x, y, z;
    private final float endFlashIntensity;
    private final float wetness;
    private final float renderDistanceBlocks;
    private final NativeFogData fog;
    private final NativeSceneInputs sceneInputs;
    private final dev.oculusforge.render.program.CameraHistory.Position previousCamera;
    private final RelativeEyePosition relativeEye;
    private final HeldLight heldLight;
    private final HeldItemIds heldItems;
    private final WorldEnvironment environment;
    private final dev.oculusforge.render.program.EyeBrightnessSmoother.Value smoothedEyeBrightness;
    private final dev.oculusforge.render.program.CustomUniformBindings.Values customUniforms;
    private final int width, height;
    private final int worldTime, worldDay;
    private final dev.oculusforge.render.program.AnimationClock.Snapshot animation;

    /** The camera's FOV interpolation starts at zero before its first tick. */
    public static boolean cameraReady(Matrix4fc projection, Matrix4fc view, double x, double y, double z) {
        return projection.isFinite() && projection.determinant() != 0.0F
            && view.isFinite() && view.determinant() != 0.0F
            && Float.isFinite((float) x) && Float.isFinite((float) y) && Float.isFinite((float) z);
    }

    public WorldMatrixData(Matrix4fc projection, Matrix4fc view, Matrix4fc shadowProjection,
        Matrix4fc shadowView, double cameraX, double cameraY, double cameraZ, int width, int height) {
        this(projection, view, shadowProjection, shadowView, cameraX, cameraY, cameraZ, width, height, 0L);
    }

    public WorldMatrixData(Matrix4fc projection, Matrix4fc view, Matrix4fc shadowProjection,
        Matrix4fc shadowView, double cameraX, double cameraY, double cameraZ, int width, int height, long clockTicks) {
        this(projection, view, shadowProjection, shadowView, cameraX, cameraY, cameraZ, width, height,
            clockTicks, dev.oculusforge.render.program.AnimationClock.ZERO);
    }

    public WorldMatrixData(Matrix4fc projection, Matrix4fc view, Matrix4fc shadowProjection,
        Matrix4fc shadowView, double cameraX, double cameraY, double cameraZ, int width, int height, long clockTicks,
        dev.oculusforge.render.program.AnimationClock.Snapshot animation) {
        this(projection, view, shadowProjection, shadowView, cameraX, cameraY, cameraZ, width, height,
            clockTicks, animation, RelativeEyePosition.ZERO);
    }

    public WorldMatrixData(Matrix4fc projection, Matrix4fc view, Matrix4fc shadowProjection,
        Matrix4fc shadowView, double cameraX, double cameraY, double cameraZ, int width, int height, long clockTicks,
        dev.oculusforge.render.program.AnimationClock.Snapshot animation, RelativeEyePosition relativeEye) {
        this(projection, view, shadowProjection, shadowView, cameraX, cameraY, cameraZ,
            width, height, clockTicks, animation, relativeEye, HeldLight.NONE);
    }

    public WorldMatrixData(Matrix4fc projection, Matrix4fc view, Matrix4fc shadowProjection,
        Matrix4fc shadowView, double cameraX, double cameraY, double cameraZ, int width, int height, long clockTicks,
        dev.oculusforge.render.program.AnimationClock.Snapshot animation, RelativeEyePosition relativeEye, HeldLight heldLight) {
        this(projection, view, shadowProjection, shadowView, cameraX, cameraY, cameraZ,
            width, height, clockTicks, animation, relativeEye, heldLight, WorldEnvironment.CLEAR);
    }

    public WorldMatrixData(Matrix4fc projection, Matrix4fc view, Matrix4fc shadowProjection,
        Matrix4fc shadowView, double cameraX, double cameraY, double cameraZ, int width, int height, long clockTicks,
        dev.oculusforge.render.program.AnimationClock.Snapshot animation, RelativeEyePosition relativeEye,
        HeldLight heldLight, WorldEnvironment environment) {
        this(projection, view, shadowProjection, shadowView, cameraX, cameraY, cameraZ, width, height,
            clockTicks, animation, relativeEye, heldLight, environment,
            new dev.oculusforge.render.program.EyeBrightnessSmoother.Value(
                environment.eyeBrightness().blockLightmap(), environment.eyeBrightness().skyLightmap()));
    }

    public WorldMatrixData(Matrix4fc projection, Matrix4fc view, Matrix4fc shadowProjection,
        Matrix4fc shadowView, double cameraX, double cameraY, double cameraZ, int width, int height, long clockTicks,
        dev.oculusforge.render.program.AnimationClock.Snapshot animation, RelativeEyePosition relativeEye,
        HeldLight heldLight, WorldEnvironment environment,
        dev.oculusforge.render.program.EyeBrightnessSmoother.Value smoothedEyeBrightness) {
        this(projection,view,shadowProjection,shadowView,cameraX,cameraY,cameraZ,width,height,clockTicks,
            animation,relativeEye,heldLight,environment,smoothedEyeBrightness,
            dev.oculusforge.render.program.CustomUniformBindings.Values.EMPTY);
    }

    public WorldMatrixData(Matrix4fc projection, Matrix4fc view, Matrix4fc shadowProjection,
        Matrix4fc shadowView, double cameraX, double cameraY, double cameraZ, int width, int height, long clockTicks,
        dev.oculusforge.render.program.AnimationClock.Snapshot animation, RelativeEyePosition relativeEye,
        HeldLight heldLight, WorldEnvironment environment,
        dev.oculusforge.render.program.EyeBrightnessSmoother.Value smoothedEyeBrightness,
        dev.oculusforge.render.program.CustomUniformBindings.Values customUniforms) {
        this(projection,view,shadowProjection,shadowView,cameraX,cameraY,cameraZ,width,height,clockTicks,
            animation,relativeEye,heldLight,environment,smoothedEyeBrightness,customUniforms,0);
    }

    public WorldMatrixData(Matrix4fc projection, Matrix4fc view, Matrix4fc shadowProjection,
        Matrix4fc shadowView, double cameraX, double cameraY, double cameraZ, int width, int height, long clockTicks,
        dev.oculusforge.render.program.AnimationClock.Snapshot animation, RelativeEyePosition relativeEye,
        HeldLight heldLight, WorldEnvironment environment,
        dev.oculusforge.render.program.EyeBrightnessSmoother.Value smoothedEyeBrightness,
        dev.oculusforge.render.program.CustomUniformBindings.Values customUniforms, float endFlashIntensity) {
        this(projection,view,shadowProjection,shadowView,cameraX,cameraY,cameraZ,width,height,clockTicks,
            animation,relativeEye,heldLight,environment,smoothedEyeBrightness,customUniforms,endFlashIntensity,0);
    }

    public WorldMatrixData(Matrix4fc projection, Matrix4fc view, Matrix4fc shadowProjection,
        Matrix4fc shadowView, double cameraX, double cameraY, double cameraZ, int width, int height, long clockTicks,
        dev.oculusforge.render.program.AnimationClock.Snapshot animation, RelativeEyePosition relativeEye,
        HeldLight heldLight, WorldEnvironment environment,
        dev.oculusforge.render.program.EyeBrightnessSmoother.Value smoothedEyeBrightness,
        dev.oculusforge.render.program.CustomUniformBindings.Values customUniforms, float endFlashIntensity,
        float renderDistanceBlocks) {
        this(projection,view,shadowProjection,shadowView,cameraX,cameraY,cameraZ,width,height,clockTicks,
            animation,relativeEye,heldLight,environment,smoothedEyeBrightness,customUniforms,endFlashIntensity,
            renderDistanceBlocks,NativeFogData.CLEAR);
    }

    public WorldMatrixData(Matrix4fc projection, Matrix4fc view, Matrix4fc shadowProjection,
        Matrix4fc shadowView, double cameraX, double cameraY, double cameraZ, int width, int height, long clockTicks,
        dev.oculusforge.render.program.AnimationClock.Snapshot animation, RelativeEyePosition relativeEye,
        HeldLight heldLight, WorldEnvironment environment,
        dev.oculusforge.render.program.EyeBrightnessSmoother.Value smoothedEyeBrightness,
        dev.oculusforge.render.program.CustomUniformBindings.Values customUniforms, float endFlashIntensity,
        float renderDistanceBlocks, NativeFogData fog) {
        this(projection,view,shadowProjection,shadowView,cameraX,cameraY,cameraZ,width,height,clockTicks,
            animation,relativeEye,heldLight,environment,smoothedEyeBrightness,customUniforms,endFlashIntensity,
            renderDistanceBlocks,fog,NativeSceneInputs.DEFAULT);
    }

    public WorldMatrixData(Matrix4fc projection, Matrix4fc view, Matrix4fc shadowProjection,
        Matrix4fc shadowView, double cameraX, double cameraY, double cameraZ, int width, int height, long clockTicks,
        dev.oculusforge.render.program.AnimationClock.Snapshot animation, RelativeEyePosition relativeEye,
        HeldLight heldLight, WorldEnvironment environment,
        dev.oculusforge.render.program.EyeBrightnessSmoother.Value smoothedEyeBrightness,
        dev.oculusforge.render.program.CustomUniformBindings.Values customUniforms, float endFlashIntensity,
        float renderDistanceBlocks, NativeFogData fog, NativeSceneInputs sceneInputs) {
        this(projection,view,shadowProjection,shadowView,cameraX,cameraY,cameraZ,width,height,clockTicks,
            animation,relativeEye,heldLight,environment,smoothedEyeBrightness,customUniforms,endFlashIntensity,
            renderDistanceBlocks,fog,sceneInputs,new dev.oculusforge.render.program.CameraHistory.Position(cameraX,cameraY,cameraZ));
    }

    public WorldMatrixData(Matrix4fc projection, Matrix4fc view, Matrix4fc shadowProjection,
        Matrix4fc shadowView, double cameraX, double cameraY, double cameraZ, int width, int height, long clockTicks,
        dev.oculusforge.render.program.AnimationClock.Snapshot animation, RelativeEyePosition relativeEye,
        HeldLight heldLight, WorldEnvironment environment,
        dev.oculusforge.render.program.EyeBrightnessSmoother.Value smoothedEyeBrightness,
        dev.oculusforge.render.program.CustomUniformBindings.Values customUniforms, float endFlashIntensity,
        float renderDistanceBlocks, NativeFogData fog, NativeSceneInputs sceneInputs,
        dev.oculusforge.render.program.CameraHistory.Position previousCamera) {
        this(projection,view,shadowProjection,shadowView,cameraX,cameraY,cameraZ,width,height,clockTicks,
            animation,relativeEye,heldLight,environment,smoothedEyeBrightness,customUniforms,endFlashIntensity,
            renderDistanceBlocks,fog,sceneInputs,previousCamera,HeldItemIds.NONE);
    }

    public WorldMatrixData(Matrix4fc projection, Matrix4fc view, Matrix4fc shadowProjection,
        Matrix4fc shadowView, double cameraX, double cameraY, double cameraZ, int width, int height, long clockTicks,
        dev.oculusforge.render.program.AnimationClock.Snapshot animation, RelativeEyePosition relativeEye,
        HeldLight heldLight, WorldEnvironment environment,
        dev.oculusforge.render.program.EyeBrightnessSmoother.Value smoothedEyeBrightness,
        dev.oculusforge.render.program.CustomUniformBindings.Values customUniforms, float endFlashIntensity,
        float renderDistanceBlocks, NativeFogData fog, NativeSceneInputs sceneInputs,
        dev.oculusforge.render.program.CameraHistory.Position previousCamera,HeldItemIds heldItems) {
        this(projection,view,shadowProjection,shadowView,cameraX,cameraY,cameraZ,width,height,clockTicks,
            animation,relativeEye,heldLight,environment,smoothedEyeBrightness,customUniforms,endFlashIntensity,
            renderDistanceBlocks,fog,sceneInputs,previousCamera,heldItems,environment.rainStrength());
    }

    /** Production supplies the shared filtered sample; convenience constructors model a first sample. */
    public WorldMatrixData(Matrix4fc projection, Matrix4fc view, Matrix4fc shadowProjection,
        Matrix4fc shadowView, double cameraX, double cameraY, double cameraZ, int width, int height, long clockTicks,
        dev.oculusforge.render.program.AnimationClock.Snapshot animation, RelativeEyePosition relativeEye,
        HeldLight heldLight, WorldEnvironment environment,
        dev.oculusforge.render.program.EyeBrightnessSmoother.Value smoothedEyeBrightness,
        dev.oculusforge.render.program.CustomUniformBindings.Values customUniforms, float endFlashIntensity,
        float renderDistanceBlocks, NativeFogData fog, NativeSceneInputs sceneInputs,
        dev.oculusforge.render.program.CameraHistory.Position previousCamera,HeldItemIds heldItems,float wetness) {
        this.wetness=new dev.oculusforge.render.program.WetnessSmoother.Value(wetness).value();
        this.heldItems=java.util.Objects.requireNonNull(heldItems);
        this.previousCamera=java.util.Objects.requireNonNull(previousCamera);
        this.sceneInputs=java.util.Objects.requireNonNull(sceneInputs);
        this.fog=java.util.Objects.requireNonNull(fog);
        if (!Float.isFinite(renderDistanceBlocks) || renderDistanceBlocks < 0)
            throw new IllegalArgumentException("Invalid native render distance");
        this.renderDistanceBlocks=renderDistanceBlocks;
        if (!Float.isFinite(endFlashIntensity))
            throw new IllegalArgumentException("Non-finite End flash intensity");
        this.endFlashIntensity=endFlashIntensity;
        this.customUniforms=java.util.Objects.requireNonNull(customUniforms);
        this.smoothedEyeBrightness = java.util.Objects.requireNonNull(smoothedEyeBrightness);
        this.environment = java.util.Objects.requireNonNull(environment);
        this.heldLight = java.util.Objects.requireNonNull(heldLight);
        this.relativeEye = java.util.Objects.requireNonNull(relativeEye);
        this.animation = java.util.Objects.requireNonNull(animation);
        var clock = dev.oculusforge.render.program.WorldClockUniforms.fromTicks(clockTicks);
        this.worldTime = clock.time();
        this.worldDay = clock.day();
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("Invalid native world viewport");
        this.width = width;
        this.height = height;
        Matrix4fc[] values = {projection, view, shadowProjection, shadowView};
        for (int i = 0; i < values.length; i++) {
            Matrix4f value = new Matrix4f(values[i]);
            if (!value.isFinite() || value.determinant() == 0.0F)
                throw new IllegalArgumentException("Non-invertible or non-finite world matrix " + i + ": " + value);
            Matrix4f inverse = new Matrix4f(value).invert();
            if (!inverse.isFinite()) throw new IllegalArgumentException("Non-finite world matrix inverse");
            value.get(matrices, i * 32);
            inverse.get(matrices, i * 32 + 16);
        }
        x = (float) cameraX; y = (float) cameraY; z = (float) cameraZ;
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z))
            throw new IllegalArgumentException("Non-finite world camera position");
    }

    public void write(ByteBuffer target) {
        if (target.isReadOnly() || target.remaining() < NativeWorldBindings.BYTE_SIZE)
            throw new IllegalArgumentException("Invalid world uniform destination");
        var data = target.duplicate().order(ByteOrder.nativeOrder());
        int base = data.position();
        // JOML's native ByteBuffer fast path assumes direct storage. Explicit bounded
        // writes support heap tests and mapped GPU memory without unsafe raw addresses.
        for (int i = 0; i < matrices.length; i++) data.putFloat(base + i * 4, matrices[i]);
        data.putFloat(base + 512, x).putFloat(base + 516, y).putFloat(base + 520, z).putFloat(base + 524, 1);
        data.putFloat(base + 528, width).putFloat(base + 532, height)
            .putFloat(base + 536, (float) width / height).putInt(base + 540, worldTime);
        data.putInt(base + 544, worldDay);
        data.putInt(base + 548, animation.frameCounter()).putFloat(base + 552, animation.frameTime())
            .putFloat(base + 556, animation.frameTimeCounter());
        data.putFloat(base + 560, relativeEye.x()).putFloat(base + 564, relativeEye.y())
            .putFloat(base + 568, relativeEye.z()).putFloat(base + 572, 0);
        data.putInt(base + 576, heldLight.mainHand()).putInt(base + 580, heldLight.offHand())
            .putInt(base + 584, environment.moonPhase()).putFloat(base + 588, environment.rainStrength());
        data.putFloat(base + 592, environment.nightVision())
            .putFloat(base + 596, endFlashIntensity).putInt(base + 600, environment.eyeBrightness().blockLightmap())
            .putInt(base + 604, environment.eyeBrightness().skyLightmap());
        data.putInt(base + 608, smoothedEyeBrightness.block()).putInt(base + 612, smoothedEyeBrightness.sky())
            .putInt(base + 616, environment.biomeCode()).putFloat(base + 620, renderDistanceBlocks);
        for(int slot=0;slot<dev.oculusforge.render.program.CustomUniformBindings.MAX_VALUES;slot++)
            data.putFloat(base+NativeWorldBindings.CUSTOM_VALUES_OFFSET+slot*4,customUniforms.slot(slot));
        int fogOffset=base+NativeWorldBindings.FOG_VALUES_OFFSET;
        data.putFloat(fogOffset,fog.red()).putFloat(fogOffset+4,fog.green()).putFloat(fogOffset+8,fog.blue())
            .putFloat(fogOffset+12,sceneInputs.sunAngle()).putInt(fogOffset+16,fog.medium()).putFloat(fogOffset+20,environment.darknessFactor())
            .putFloat(fogOffset+24,environment.blindness()).putFloat(fogOffset+28,sceneInputs.screenBrightness());
        int previousOffset=base+NativeWorldBindings.PREVIOUS_CAMERA_OFFSET;
        data.putFloat(previousOffset,(float)previousCamera.x()).putFloat(previousOffset+4,(float)previousCamera.y())
            .putFloat(previousOffset+8,(float)previousCamera.z()).putFloat(previousOffset+12,environment.cloudHeight());
        int atlasOffset=base+NativeWorldBindings.ATLAS_SIZE_OFFSET;
        data.putInt(atlasOffset,environment.atlasSize().width()).putInt(atlasOffset+4,environment.atlasSize().height())
            .putInt(atlasOffset+8,heldItems.mainHand()).putInt(atlasOffset+12,heldItems.offHand());
        int darknessOffset=base+NativeWorldBindings.DARKNESS_LIGHT_OFFSET;
        data.putFloat(darknessOffset,environment.darknessLightFactor());
        data.putFloat(darknessOffset+4,fog.start()).putFloat(darknessOffset+8,fog.end()).putFloat(darknessOffset+12,fog.alpha());
        int skyOffset=base+NativeWorldBindings.SKY_COLOR_OFFSET;
        var sky=environment.skyColor();
        data.putFloat(skyOffset,sky.red()).putFloat(skyOffset+4,sky.green()).putFloat(skyOffset+8,sky.blue()).putFloat(skyOffset+12,wetness);
    }
}
