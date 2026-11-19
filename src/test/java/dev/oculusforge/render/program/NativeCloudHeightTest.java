package dev.oculusforge.render.program;

import static org.junit.jupiter.api.Assertions.*;
import dev.oculusforge.render.world.*;
import java.nio.*;
import java.util.List;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

class NativeCloudHeightTest {
    @Test void fullscreenVertexCloudFallbackKeepsTheLiveUniformInsteadOfAConstant() throws Exception {
        for(String literal:List.of("192.33","-64.0",".5","1e3")) {
            var vertex=new LegacyFinalVertexAdapter().adapt("uniform float cloudHeight="+literal+";\n"
                +"void main(){gl_Position=ftransform()+vec4(cloudHeight);}");
            assertEquals(List.of(new LegacyFinalShaderAdapter.UniformBinding("cloudHeight",
                LegacyFinalShaderAdapter.UniformSemantic.CLOUD_HEIGHT)),vertex.uniforms());
            assertFalse(vertex.source().contains("const float cloudHeight"));
            assertFalse(vertex.source().contains(literal));
        }
    }
    @Test void invalidFullscreenVertexCloudDeclarationsRejectBeforePruningOrConstantRewriting() {
        for(String declaration:List.of("uniform int cloudHeight;","uniform float cloudHeight[2];",
            "uniform float cloudHeight=compute();","uniform float cloudHeight=1e999;",
            "uniform float cloudHeight=0.;\nuniform float cloudHeight;"))
            assertThrows(ShaderCompatibilityException.class,()->new LegacyFinalVertexAdapter().adapt(declaration
                +"\nvoid main(){gl_Position=ftransform();}"),declaration);
    }
    @Test void mergedFullscreenCloudFallbackHasExactlyOneHostDeclarationInEachStage() throws Exception {
        var root=java.nio.file.Path.of(getClass().getClassLoader().getResource("post-cloud-default").toURI());
        var prepared=new PackProgramCompiler(dev.oculusforge.shaderpack.ShaderPackLimits.DEFAULT).prepare(
            new dev.oculusforge.shaderpack.ShaderPackDescriptor("cloud-default","Cloud default",root,
                dev.oculusforge.shaderpack.ShaderPackDescriptor.Kind.DIRECTORY));
        var program=prepared.programs().getFirst();
        assertEquals(List.of(new LegacyFinalShaderAdapter.UniformBinding("cloudHeight",
            LegacyFinalShaderAdapter.UniformSemantic.CLOUD_HEIGHT)),program.fragment().uniforms());
        for(String source:List.of(program.vertexSource(),program.fragment().source())) {
            assertFalse(source.contains("const float cloudHeight"));
            assertEquals(1,java.util.regex.Pattern.compile("\\bfloat\\s+cloudHeight\\b").matcher(source).results().count());
        }
    }
    @Test void gpuFixturePreparesNativeTerrainShadowAndIndependentPostCloudUniform() throws Exception {
        var root=java.nio.file.Path.of(getClass().getClassLoader().getResource("native-cloud-height").toURI());
        var compiler=new PackProgramCompiler(dev.oculusforge.shaderpack.ShaderPackLimits.DEFAULT);
        var prepared=compiler.prepare(new dev.oculusforge.shaderpack.ShaderPackDescriptor("cloud","Cloud",root,
            dev.oculusforge.shaderpack.ShaderPackDescriptor.Kind.DIRECTORY));
        var world=prepared.world().variant("shaders");
        assertNotNull(world.nativeTerrain(),world.nativeTerrainIssue());
        assertNotNull(world.nativeShadow(),world.nativeShadowIssue());
        assertEquals(List.of(0,4),world.terrainTargets());
        assertTrue(prepared.programs().getFirst().fragment().uniforms().stream()
            .anyMatch(u->u.semantic()==LegacyFinalShaderAdapter.UniformSemantic.CLOUD_HEIGHT));
        assertEquals(0,compiler.cachedPipelineCount());
    }
    @Test void nativeVertexFragmentAndShadowShareTheCloudLaneWithoutChangingBlockSize() throws Exception {
        for(boolean shadow:List.of(false,true))for(boolean extended:List.of(false,true)) {
            var program=NativeTerrainProgram.adaptWithMesh("uniform float cloudHeight;\n"
                +"void main(){gl_Position=ftransform()+vec4(cloudHeight);}",
                "uniform float cloudHeight;\nvoid main(){gl_FragColor=vec4(cloudHeight);}",List.of(0),shadow,extended);
            assertTrue(program.usesWorldMatrices());
            for(String source:List.of(program.vertexSource(),program.fragmentSource()))
                assertTrue(source.contains("oculusNative_previousCameraPosition.w"));
        }
        assertEquals(924,NativeWorldBindings.CLOUD_HEIGHT_OFFSET);
        assertEquals(976, NativeWorldBindings.BYTE_SIZE);
        assertEquals(912,NativeWorldBindings.PREVIOUS_CAMERA_OFFSET);
    }
    @Test void invalidCloudDeclarationsRejectEvenWhenUnused() {
        for(String declaration:List.of("uniform int cloudHeight;","uniform float cloudHeight[2];",
            "uniform float cloudHeight=someFunction();","uniform float cloudHeight;\nuniform float cloudHeight;"))
            assertThrows(ShaderCompatibilityException.class,()->NativeTerrainProgram.adapt(declaration
                +"\nvoid main(){gl_Position=ftransform();}","void main(){gl_FragColor=vec4(1);}",List.of(0)),declaration);
    }
    @Test void cloudSerializationPreservesCameraCoordinatesAndHeapDirectBufferGuards() {
        var m=new Matrix4f();
        for(var bytes:List.of(ByteBuffer.allocate(NativeWorldBindings.BYTE_SIZE + 16),ByteBuffer.allocateDirect(NativeWorldBindings.BYTE_SIZE + 16))) {
            bytes.order(ByteOrder.nativeOrder());for(int i=0;i<bytes.capacity();i++)bytes.put(i,(byte)0x5a);
            bytes.position(8).limit(8 + NativeWorldBindings.BYTE_SIZE);
            var env=new WorldEnvironment(3,.2f,.4f,EyeBrightness.DARK,0,.7f,.3f,231.75f);
            new WorldMatrixData(m,m,m,m,1,2,3,854,480,6000,AnimationClock.ZERO,RelativeEyePosition.ZERO,
                HeldLight.NONE,env,new EyeBrightnessSmoother.Value(0,0),CustomUniformBindings.Values.EMPTY,0,128,
                NativeFogData.CLEAR,NativeSceneInputs.DEFAULT,new CameraHistory.Position(-12.25,100.5,30_000_000.125)).write(bytes);
            assertEquals(-12.25f,bytes.getFloat(920));assertEquals(100.5f,bytes.getFloat(924));
            assertEquals((float)30_000_000.125,bytes.getFloat(928));assertEquals(231.75f,bytes.getFloat(932));
            assertEquals(.7f,bytes.getFloat(912));assertEquals(.3f,bytes.getFloat(908));
            assertEquals(8,bytes.position());assertEquals(0x5a5a5a5a,bytes.getInt(0));
            assertEquals(0x5a5a5a5a,bytes.duplicate().clear().getInt(8 + NativeWorldBindings.BYTE_SIZE));
        }
    }
    @Test void finiteModdedCloudHeightsArePreservedAndInvalidSnapshotsReject() {
        for(float value:new float[]{-64,0,192.33f,4096})
            assertEquals(value,new WorldEnvironment(0,0,0,EyeBrightness.DARK,0,0,0,value).cloudHeight());
        for(float value:new float[]{Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY})
            assertThrows(IllegalArgumentException.class,()->new WorldEnvironment(0,0,0,EyeBrightness.DARK,0,0,0,value));
        assertEquals(0,WorldEnvironment.CLEAR.cloudHeight());
    }
    @Test void postCloudDeclarationUsesAnActualSnapshotSemantic() throws Exception {
        var post=new LegacyFinalShaderAdapter().adapt("uniform float cloudHeight;\nvoid main(){gl_FragColor=vec4(cloudHeight);}");
        assertEquals(List.of(new LegacyFinalShaderAdapter.UniformBinding("cloudHeight",
            LegacyFinalShaderAdapter.UniformSemantic.CLOUD_HEIGHT)),post.uniforms());
        var method=PackFinalProgram.class.getDeclaredMethod("writeUniform",com.mojang.blaze3d.buffers.Std140Builder.class,
            LegacyFinalShaderAdapter.UniformSemantic.class,net.minecraft.client.renderer.state.level.LevelRenderState.class,
            int.class,int.class,WorldEnvironment.class,WorldClockUniforms.class,AnimationClock.Snapshot.class);
        method.setAccessible(true);
        var program=new PackFinalProgram("cloud","final",null,List.of(),List.of(),null);
        var bytes=ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder());
        method.invoke(program,com.mojang.blaze3d.buffers.Std140Builder.intoBuffer(bytes),
            LegacyFinalShaderAdapter.UniformSemantic.CLOUD_HEIGHT,null,854,480,
            new WorldEnvironment(0,0,0,EyeBrightness.DARK,0,0,0,231.75f),WorldClockUniforms.fromTicks(0),AnimationClock.ZERO);
        assertEquals(231.75f,bytes.getFloat(0));
    }
}
