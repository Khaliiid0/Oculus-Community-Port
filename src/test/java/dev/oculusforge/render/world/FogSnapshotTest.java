package dev.oculusforge.render.world;

import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.junit.jupiter.api.Test;

class FogSnapshotTest {
    private static final class Target implements WorldEnvironmentState {
        WorldEnvironment value;
        public WorldEnvironment oculus$getEnvironment(){return value;}
        public void oculus$setEnvironment(WorldEnvironment value){this.value=value;}
    }
    @Test void captureCopiesCompletedEnvironmentalValuesNotRenderDistanceFogOrSkyAlpha() {
        var camera=new CameraRenderState();camera.fogType=net.minecraft.world.level.material.FogType.WATER;
        camera.fogData.color.set(.1f,.2f,.3f,.37f);camera.fogData.environmentalStart=-8;camera.fogData.environmentalEnd=96;
        camera.fogData.renderDistanceStart=128;camera.fogData.renderDistanceEnd=256;
        var value=NativeFogData.capture(camera);camera.fogData.color.set(1);camera.fogData.environmentalEnd=5;
        assertEquals(new NativeFogData(1,.1f,.2f,.3f,.37f,-8,96),value);
    }
    @Test void finishingFogReplacesOnlyFogAndLeavesPreviousFrameImmutable() {
        var target=new Target();var original=new WorldEnvironment(3,.2f,.4f,EyeBrightness.DARK,0,.1f,.3f,192.33f,2,
            new AtlasSize(2048,1024),new HeldItemKeys("minecraft:torch","minecraft:air"),.125f);
        target.value=original;var camera=new CameraRenderState();camera.fogData.color.set(.2f,.3f,.4f,.5f);
        camera.fogData.environmentalStart=-12;camera.fogData.environmentalEnd=84;
        NativeFogData.finishFrame(target,camera);
        assertNotSame(original,target.value);assertEquals(new NativeFogData(0,.2f,.3f,.4f,.5f,-12,84),target.value.fog());
        assertEquals(original,target.value.withFog(original.fog()));assertSame(NativeFogData.CLEAR,original.fog());
        assertEquals(.125f,target.value.darknessLightFactor());assertEquals("minecraft:torch",target.value.heldItems().mainHand());
    }
    @Test void invalidCompletedFogInvalidatesTheEntireEnvironmentInsteadOfKeepingOldFog() {
        var target=new Target();target.value=WorldEnvironment.CLEAR;var camera=new CameraRenderState();
        camera.fogData.environmentalEnd=Float.NaN;NativeFogData.finishFrame(target,camera);
        assertNull(target.value);assertThrows(IllegalStateException.class,target::oculus$requireEnvironment);
    }
    @Test void absentCameraEnvironmentCannotBeInventedByFogFinalization() {
        var target=new Target();NativeFogData.finishFrame(target,new CameraRenderState());assertNull(target.value);
    }
    @Test void completedWorldSkyAndFogCommitTogetherWithoutReusingTheCameraExtractionDefault() {
        var target=new Target();var original=WorldEnvironment.CLEAR.withSkyColor(SkyColor.fromArgb(0x123456));
        target.value=original;var camera=new CameraRenderState();camera.fogData.color.set(.2f,.3f,.4f,.5f);
        camera.fogData.environmentalStart=12;camera.fogData.environmentalEnd=84;
        var currentSky=SkyColor.fromArgb(0x2080e0);
        NativeFogData.finishFrame(target,camera,currentSky);
        assertSame(currentSky,target.value.skyColor());assertEquals(12,target.value.fog().start());
        assertEquals(84,target.value.fog().end());assertEquals(SkyColor.fromArgb(0x123456),original.skyColor());
        camera.fogData.environmentalEnd=Float.NaN;NativeFogData.finishFrame(target,camera,currentSky);
        assertNull(target.value);
        NativeFogData.finishFrame(target,new CameraRenderState(),currentSky);assertNull(target.value);
    }
    @Test void finiteReversedEqualTinyAndModdedRangesAreNotClampedOrReordered() {
        assertEquals(-.125f,new NativeFogData(0,0,0,0,1,8,0).scale());
        assertEquals(Float.POSITIVE_INFINITY,new NativeFogData(0,0,0,0,1,4,4).scale());
        var tiny=new NativeFogData(0,0,0,0,1,0,.00001f);assertEquals(1f/.00001f,tiny.scale());
        assertEquals(2,new NativeFogData(0,-.1f,1.1f,3,2,-1000,2000).alpha());
    }
    @Test void invalidRawComponentsRejectAndNullFogCannotEnterAnImmutableSnapshot() {
        for(float bad:new float[]{Float.NaN,Float.NEGATIVE_INFINITY,Float.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class,()->new NativeFogData(0,0,0,0,bad,0,1));
            assertThrows(IllegalArgumentException.class,()->new NativeFogData(0,0,0,0,1,bad,1));
            assertThrows(IllegalArgumentException.class,()->new NativeFogData(0,0,0,0,1,0,bad));
        }
        assertThrows(NullPointerException.class,()->WorldEnvironment.CLEAR.withFog(null));
    }
}
