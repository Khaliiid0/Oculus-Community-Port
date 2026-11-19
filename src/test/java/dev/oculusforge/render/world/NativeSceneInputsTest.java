package dev.oculusforge.render.world;

import static org.junit.jupiter.api.Assertions.*;
import dev.oculusforge.render.program.NativeWorldBindings;
import org.junit.jupiter.api.Test;

class NativeSceneInputsTest {
    @Test void realFrameFieldsFillPaddingWithoutMovingAnyOtherUniforms() {
        var matrix=new org.joml.Matrix4f();
        var env=new WorldEnvironment(3,.2f,.4f,EyeBrightness.DARK,0,.7f,.3f);
        for(var bytes:java.util.List.of(java.nio.ByteBuffer.allocate(NativeWorldBindings.BYTE_SIZE + 16),java.nio.ByteBuffer.allocateDirect(NativeWorldBindings.BYTE_SIZE + 16))) {
            bytes.order(java.nio.ByteOrder.nativeOrder());
            for(int i=0;i<bytes.capacity();i++)bytes.put(i,(byte)0x5a);
            bytes.position(8).limit(8 + NativeWorldBindings.BYTE_SIZE);
            new WorldMatrixData(matrix,matrix,matrix,matrix,1,2,3,854,480,6000,
                dev.oculusforge.render.program.AnimationClock.ZERO,RelativeEyePosition.ZERO,HeldLight.NONE,env,
                new dev.oculusforge.render.program.EyeBrightnessSmoother.Value(0,0),
                dev.oculusforge.render.program.CustomUniformBindings.Values.EMPTY,0,128,
                new NativeFogData(1,.1f,.2f,.3f),new NativeSceneInputs(.37f,.25f)).write(bytes);
            assertEquals(.1f,bytes.getFloat(888));assertEquals(.2f,bytes.getFloat(892));assertEquals(.3f,bytes.getFloat(896));
            assertEquals(.25f,bytes.getFloat(900));assertEquals(1,bytes.getInt(904));assertEquals(.3f,bytes.getFloat(908));
            assertEquals(.7f,bytes.getFloat(912));assertEquals(.37f,bytes.getFloat(916));
            assertEquals(6000,bytes.getInt(548));assertEquals(128,bytes.getFloat(628));
            assertEquals(8,bytes.position());assertEquals(0x5a5a5a5a,bytes.getInt(0));
            assertEquals(0x5a5a5a5a,bytes.duplicate().clear().getInt(8 + NativeWorldBindings.BYTE_SIZE));
        }
    }
    @Test void skyAnglesUseLegacySunriseNoonSunsetMidnightConvention() {
        assertEquals(0,NativeSceneInputs.sunAngleTurns(-(float)Math.PI/2),.000001f);
        assertEquals(.25f,NativeSceneInputs.sunAngleTurns(0),.000001f);
        assertEquals(.5f,NativeSceneInputs.sunAngleTurns((float)Math.PI/2),.000001f);
        assertEquals(.75f,NativeSceneInputs.sunAngleTurns((float)Math.PI),.000001f);
        for(float angle:new float[]{-100,-7,0,8,100}) {
            float turns=NativeSceneInputs.sunAngleTurns(angle);
            assertTrue(turns>=0&&turns<1);
        }
    }

    @Test void brightnessPreservesFiniteModdedValuesAndInvalidSnapshotsFail() {
        assertEquals(2.5f,NativeSceneInputs.fromSkyAngle(2.5f,0).screenBrightness());
        for(float bad:new float[]{Float.NaN,Float.NEGATIVE_INFINITY,Float.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class,()->NativeSceneInputs.fromSkyAngle(bad,0));
            assertThrows(IllegalArgumentException.class,()->NativeSceneInputs.fromSkyAngle(1,bad));
        }
        assertThrows(IllegalArgumentException.class,()->new NativeSceneInputs(1,-.1f));
        assertThrows(IllegalArgumentException.class,()->new NativeSceneInputs(1,1));
    }
}
