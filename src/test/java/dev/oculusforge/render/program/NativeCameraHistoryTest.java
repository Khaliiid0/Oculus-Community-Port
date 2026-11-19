package dev.oculusforge.render.program;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.nio.*;
import org.junit.jupiter.api.Test;
import org.joml.Matrix4f;
import dev.oculusforge.render.world.*;

class NativeCameraHistoryTest {
    @Test void gpuProbeRetainsPriorGpuPositionAndChecksThreePostStages() throws Exception {
        var root=java.nio.file.Path.of(getClass().getClassLoader().getResource("native-camera-history").toURI());
        var prepared=new PackProgramCompiler(dev.oculusforge.shaderpack.ShaderPackLimits.DEFAULT).prepare(
            new dev.oculusforge.shaderpack.ShaderPackDescriptor("history","History",root,dev.oculusforge.shaderpack.ShaderPackDescriptor.Kind.DIRECTORY));
        var world=prepared.world().variant("shaders");
        assertNotNull(world.nativeTerrain(),world.nativeTerrainIssue());assertNotNull(world.nativeShadow(),world.nativeShadowIssue());
        assertEquals(List.of(0,4),world.terrainTargets());
        assertTrue(world.initialColorBuffers().containsAll(java.util.Set.of(4,6,7)));
        assertEquals(3,prepared.programs().size());
        assertTrue(prepared.variants().get("shaders").buffers().retainedColorSlots().keySet().containsAll(java.util.Set.of(6,7)));
        for(var program:prepared.programs())assertTrue(program.fragment().uniforms().stream().anyMatch(
            u->u.semantic()==LegacyFinalShaderAdapter.UniformSemantic.PREVIOUS_CAMERA_POSITION));
    }
    @Test void previousCameraBindsInVertexAndFragmentTerrainAndShadow() throws Exception {
        for(boolean shadow:new boolean[]{false,true})for(boolean extended:new boolean[]{false,true}) {
            var p=NativeTerrainProgram.adaptWithMesh("uniform vec3 previousCameraPosition;\n"
                +"void main(){gl_Position=ftransform()+vec4(previousCameraPosition,0);}",
                "uniform vec3 previousCameraPosition;\nvoid main(){gl_FragColor=vec4(previousCameraPosition,1);}",
                List.of(0),shadow,extended);
            assertTrue(p.usesWorldMatrices());
            assertTrue(p.vertexSource().contains("oculusNative_previousCameraPosition.xyz"));
            assertTrue(p.fragmentSource().contains("oculusNative_previousCameraPosition.xyz"));
        }
        for(String invalid:List.of("uniform vec2 previousCameraPosition;","uniform vec3 previousCameraPosition[2];",
            "uniform vec3 previousCameraPosition;\nuniform vec3 previousCameraPosition;"))
            assertThrows(ShaderCompatibilityException.class,()->NativeTerrainProgram.adapt(invalid+"\nvoid main(){gl_Position=ftransform();}",
                "void main(){gl_FragColor=vec4(1);}",List.of(0)));
    }

    @Test void appendedPayloadKeepsEveryPreviousOffsetAndWritesOnlyInsideTheSlice() {
        assertEquals(912,NativeWorldBindings.PREVIOUS_CAMERA_OFFSET);assertEquals(976, NativeWorldBindings.BYTE_SIZE);
        var m=new Matrix4f();
        var data=new WorldMatrixData(m,m,m,m,4,5,6,854,480,6000,AnimationClock.ZERO,RelativeEyePosition.ZERO,HeldLight.NONE,
            WorldEnvironment.CLEAR,new EyeBrightnessSmoother.Value(0,0),CustomUniformBindings.Values.EMPTY,0,128,
            NativeFogData.CLEAR,new NativeSceneInputs(.37f,.25f),new CameraHistory.Position(-12.25,100.5,30_000_000.125));
        for(var bytes:List.of(ByteBuffer.allocate(NativeWorldBindings.BYTE_SIZE + 16),ByteBuffer.allocateDirect(NativeWorldBindings.BYTE_SIZE + 16))) {
            bytes.order(ByteOrder.nativeOrder());for(int i=0;i<bytes.capacity();i++)bytes.put(i,(byte)0x5a);
            bytes.position(8).limit(8 + NativeWorldBindings.BYTE_SIZE);data.write(bytes);
            assertEquals(4,bytes.getFloat(520));assertEquals(5,bytes.getFloat(524));assertEquals(6,bytes.getFloat(528));
            assertEquals(.25f,bytes.getFloat(900));assertEquals(.37f,bytes.getFloat(916));
            assertEquals(-12.25f,bytes.getFloat(920));assertEquals(100.5f,bytes.getFloat(924));
            assertEquals((float)30_000_000.125,bytes.getFloat(928));assertEquals(0,bytes.getInt(932));
            assertEquals(8,bytes.position());assertEquals(0x5a5a5a5a,bytes.getInt(0));
            assertEquals(0x5a5a5a5a,bytes.duplicate().clear().getInt(8 + NativeWorldBindings.BYTE_SIZE));
        }
    }
}
