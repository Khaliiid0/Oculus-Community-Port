package dev.oculusforge.render.program;

import static org.junit.jupiter.api.Assertions.*;
import dev.oculusforge.render.world.*;
import java.nio.*;
import java.util.*;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

final class NativeFarTest {
    private static WorldMatrixData snapshot(float distance) {
        var m=new Matrix4f();
        return new WorldMatrixData(m,m,m,m,1,2,3,854,480,6000,AnimationClock.ZERO,
            RelativeEyePosition.ZERO,HeldLight.NONE,WorldEnvironment.CLEAR,
            new EyeBrightnessSmoother.Value(16,32),CustomUniformBindings.Values.EMPTY,.75f,distance);
    }
    @Test void terrainAndShadowReadFarInBothStages() throws Exception {
        for(boolean shadow:new boolean[]{false,true}) {
            var p=NativeTerrainProgram.adaptWithMesh(
                "uniform float far;\nvoid main(){gl_Position=ftransform()+vec4(far);}",
                "uniform float far;\nvoid main(){gl_FragColor=vec4(far);}",List.of(0),shadow,false);
            assertTrue(p.usesWorldMatrices());
            assertFalse(p.needsShadowMatrices());
            for(String source:List.of(p.vertexSource(),p.fragmentSource()))
                assertTrue(source.contains("vec4(oculusNative_far)"));
        }
    }
    @Test void rejectsIncorrectDeclarationsAndCustomOverride() {
        for(String d:List.of("uniform int far;","uniform float far,far;","uniform float far[2];"))
            assertThrows(ShaderCompatibilityException.class,()->NativeTerrainProgram.adapt(
                "void main(){gl_Position=ftransform();}",d+"\nvoid main(){gl_FragColor=vec4(1);}",List.of(0)));
        assertThrows(IllegalArgumentException.class,()->CustomExpressionRuntime.compile(Map.of("far","1"),Set.of("far")));
    }
    @Test void snapshotPreservesAdjacentFieldsAndUpdatesDistanceWithoutStaleValues() {
        assertEquals(976, NativeWorldBindings.BYTE_SIZE);
        for(ByteBuffer b:List.of(ByteBuffer.allocate(NativeWorldBindings.BYTE_SIZE + 16),ByteBuffer.allocateDirect(NativeWorldBindings.BYTE_SIZE + 16))) {
            b.order(ByteOrder.nativeOrder());
            for(int i=0;i<b.capacity();i++)b.put(i,(byte)0x5a);
            b.position(8).limit(8 + NativeWorldBindings.BYTE_SIZE);
            for(float distance:new float[]{128,256,64}) {
                snapshot(distance).write(b);
                assertEquals(distance,b.getFloat(628));
                assertEquals(.75f,b.getFloat(604));
                assertEquals(16,b.getInt(616));assertEquals(32,b.getInt(620));
                assertEquals(0,b.getInt(624));
                for(int slot=0;slot<64;slot++)assertEquals(0f,b.getFloat(632+slot*4));
                assertEquals(8,b.position());assertEquals(0x5a5a5a5a,b.getInt(0));
                assertEquals(0x5a5a5a5a,b.duplicate().clear().getInt(8 + NativeWorldBindings.BYTE_SIZE));
            }
        }
    }
    @Test void invalidDistancesRejectBeforeUpload() {
        for(float distance:new float[]{-1,Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY})
            assertThrows(IllegalArgumentException.class,()->snapshot(distance));
    }
}
