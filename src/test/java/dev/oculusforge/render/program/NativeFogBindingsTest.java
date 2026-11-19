package dev.oculusforge.render.program;

import static org.junit.jupiter.api.Assertions.*;
import dev.oculusforge.render.world.*;
import java.nio.*;
import java.util.*;
import net.minecraft.world.level.material.FogType;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

final class NativeFogBindingsTest {
    @Test void bindsAllThreeInputsInBothStagesAndShadow() throws Exception {
        String declarations="uniform vec3 fogColor;\nuniform int isEyeInWater;\nuniform float darknessFactor;\n";
        for(boolean shadow:new boolean[]{false,true}) {
            var p=NativeTerrainProgram.adaptWithMesh(declarations+"void main(){gl_Position=ftransform()+vec4(fogColor,float(isEyeInWater)+darknessFactor);}",
                declarations+"void main(){gl_FragColor=vec4(fogColor,float(isEyeInWater)+darknessFactor);}",List.of(0),shadow,false);
            assertTrue(p.usesWorldMatrices());
            for(String source:List.of(p.vertexSource(),p.fragmentSource())) {
                assertTrue(source.contains("oculusNative_fogColor.xyz"));
                assertTrue(source.contains("oculusNative_isEyeInWater"));
                assertTrue(source.contains("oculusNative_darknessFactor"));
            }
        }
    }
    @Test void validatesTypesDuplicatesAndInternalSpoofing() {
        for(String d:List.of("uniform float isEyeInWater;","uniform vec4 fogColor;","uniform int darknessFactor;",
            "uniform vec3 fogColor;\nuniform vec3 fogColor;","uniform vec3 oculusNative_fogColor;"))
            assertThrows(ShaderCompatibilityException.class,()->NativeTerrainProgram.adapt("void main(){gl_Position=ftransform();}",d+"\nvoid main(){gl_FragColor=vec4(1);}",List.of(0)));
    }
    @Test void mapsEveryCameraMediumConsistently() {
        assertEquals(1,NativeFogData.eyeMedium(FogType.WATER));
        assertEquals(2,NativeFogData.eyeMedium(FogType.LAVA));
        assertEquals(3,NativeFogData.eyeMedium(FogType.POWDER_SNOW));
        assertEquals(0,NativeFogData.eyeMedium(FogType.ATMOSPHERIC));
        assertEquals(0,NativeFogData.eyeMedium(FogType.NONE));
    }
    @Test void writesAppendedFieldsWithoutMovingCustomSlotsOrLeakingStaleBytes() {
        assertEquals(624,NativeWorldBindings.CUSTOM_VALUES_OFFSET);
        assertEquals(880,NativeWorldBindings.FOG_VALUES_OFFSET);
        assertEquals(976, NativeWorldBindings.BYTE_SIZE);
        var m=new Matrix4f();
        for(var bytes:List.of(ByteBuffer.allocate(NativeWorldBindings.BYTE_SIZE + 16),ByteBuffer.allocateDirect(NativeWorldBindings.BYTE_SIZE + 16))) {
            bytes.order(ByteOrder.nativeOrder());
            for(int i=0;i<bytes.capacity();i++)bytes.put(i,(byte)0x5a);
            bytes.position(8).limit(8 + NativeWorldBindings.BYTE_SIZE);
            for(int medium:new int[]{1,2,3,0}) {
                var environment=new WorldEnvironment(0,0,0,EyeBrightness.DARK,0,0,.6f);
                new WorldMatrixData(m,m,m,m,0,0,0,854,480,0,AnimationClock.ZERO,RelativeEyePosition.ZERO,
                    HeldLight.NONE,environment,new EyeBrightnessSmoother.Value(0,0),CustomUniformBindings.Values.EMPTY,
                    0,128,new NativeFogData(medium,.1f,.2f,.3f)).write(bytes);
                assertEquals(.1f,bytes.getFloat(888));assertEquals(.2f,bytes.getFloat(892));assertEquals(.3f,bytes.getFloat(896));
                assertEquals(0,bytes.getInt(900));assertEquals(medium,bytes.getInt(904));assertEquals(.6f,bytes.getFloat(908));
                assertEquals(0,bytes.getInt(912));assertEquals(0,bytes.getInt(916));
                for(int slot=0;slot<64;slot++)assertEquals(0f,bytes.getFloat(632+slot*4));
                assertEquals(128f,bytes.getFloat(628));
                assertEquals(8,bytes.position());assertEquals(0x5a5a5a5a,bytes.getInt(0));
                assertEquals(0x5a5a5a5a,bytes.duplicate().clear().getInt(8 + NativeWorldBindings.BYTE_SIZE));
            }
        }
    }
    @Test void rejectsInvalidSnapshotInputs() {
        assertThrows(IllegalArgumentException.class,()->new NativeFogData(4,0,0,0));
        assertThrows(IllegalArgumentException.class,()->new NativeFogData(-1,0,0,0));
        for(float f:new float[]{Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class,()->new NativeFogData(0,f,0,0));
            assertThrows(IllegalArgumentException.class,()->new NativeFogData(0,0,f,0));
            assertThrows(IllegalArgumentException.class,()->new NativeFogData(0,0,0,f));
        }
    }
}
