package dev.oculusforge.render.program;

import static org.junit.jupiter.api.Assertions.*;
import dev.oculusforge.render.world.*;
import java.nio.*;
import java.util.*;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

class NativeFogRangeTest {
    @Test void rangesAndRealColorAlphaKeepTheirOffsetsWhenSkyExtendsTheBlock() {
        assertEquals(948,NativeWorldBindings.FOG_RANGE_OFFSET);assertEquals(976,NativeWorldBindings.BYTE_SIZE);
        assertEquals(944,NativeWorldBindings.DARKNESS_LIGHT_OFFSET);assertEquals(928,NativeWorldBindings.ATLAS_SIZE_OFFSET);
        var m=new Matrix4f();var fog=new NativeFogData(1,.1f,.2f,.3f,.37f,-12.25f,84.5f);
        var environment=new WorldEnvironment(0,0,0,EyeBrightness.DARK,0,0,.6f,192.33f,0,
            new AtlasSize(2048,1024),HeldItemKeys.NONE,.125f).withFog(fog);
        for(var bytes:List.of(ByteBuffer.allocate(NativeWorldBindings.BYTE_SIZE+16),ByteBuffer.allocateDirect(NativeWorldBindings.BYTE_SIZE+16))) {
            bytes.order(ByteOrder.nativeOrder());for(int i=0;i<bytes.capacity();i++)bytes.put(i,(byte)0x5a);
            bytes.position(8).limit(8+NativeWorldBindings.BYTE_SIZE);
            new WorldMatrixData(m,m,m,m,1,2,3,854,480,6000,AnimationClock.ZERO,RelativeEyePosition.ZERO,HeldLight.NONE,
                environment,new EyeBrightnessSmoother.Value(0,0),CustomUniformBindings.Values.EMPTY,0,128,fog,
                new NativeSceneInputs(.4f,.25f),new CameraHistory.Position(-12,100,30),new HeldItemIds(44002,40008)).write(bytes);
            assertEquals(.1f,bytes.getFloat(888));assertEquals(.25f,bytes.getFloat(900));
            assertEquals(2048,bytes.getInt(936));assertEquals(44002,bytes.getInt(944));
            assertEquals(.125f,bytes.getFloat(952));assertEquals(-12.25f,bytes.getFloat(956));
            assertEquals(84.5f,bytes.getFloat(960));assertEquals(.37f,bytes.getFloat(964));
            assertEquals(8,bytes.position());assertEquals(0x5a5a5a5a,bytes.getInt(0));
            assertEquals(0x5a5a5a5a,bytes.duplicate().clear().getInt(8+NativeWorldBindings.BYTE_SIZE));
        }
    }
    @Test void postFogWriterReadsTheImmutableSnapshotWithoutAMutableCameraState() throws Exception {
        var writer=PackFinalProgram.class.getDeclaredMethod("writeUniform",com.mojang.blaze3d.buffers.Std140Builder.class,
            LegacyFinalShaderAdapter.UniformSemantic.class,net.minecraft.client.renderer.state.level.LevelRenderState.class,
            int.class,int.class,WorldEnvironment.class,WorldClockUniforms.class,AnimationClock.Snapshot.class);writer.setAccessible(true);
        var fog=new NativeFogData(1,.1f,.2f,.3f,.37f,-12,84);var env=WorldEnvironment.CLEAR.withFog(fog);
        try(var program=new PackFinalProgram("fog","final",null,List.of(),List.of(),null)) {
            var bytes=ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder());var builder=com.mojang.blaze3d.buffers.Std140Builder.intoBuffer(bytes);
            for(var semantic:List.of(LegacyFinalShaderAdapter.UniformSemantic.LEGACY_FOG_COLOR,
                LegacyFinalShaderAdapter.UniformSemantic.LEGACY_FOG_START,LegacyFinalShaderAdapter.UniformSemantic.LEGACY_FOG_END))
                writer.invoke(program,builder,semantic,null,854,480,env,null,null);
            assertEquals(.1f,bytes.getFloat(0));assertEquals(.37f,bytes.getFloat(12));
            assertEquals(-12,bytes.getFloat(16));assertEquals(84,bytes.getFloat(20));
        }
    }
    @Test void nativeFogFieldsCannotBeReplacedByCustomProperties() {
        for(String name:List.of("fogStart","fogEnd"))
            assertThrows(ShaderCompatibilityException.class,()->CustomUniformBindings.prepare(NativeCustomUniforms.parse(
                "uniform.float."+name+"=0\nuniform.float.probe="+name),List.of("uniform float probe;float value=probe;")));
    }
    @Test void diagnosticPreparesNativeTerrainTwoShadowColorsAndBothPostStages() throws Exception {
        var root=java.nio.file.Path.of(getClass().getClassLoader().getResource("native-fog-range").toURI());
        var prepared=new PackProgramCompiler(dev.oculusforge.shaderpack.ShaderPackLimits.DEFAULT).prepare(
            new dev.oculusforge.shaderpack.ShaderPackDescriptor("fog-range","Fog ranges",root,dev.oculusforge.shaderpack.ShaderPackDescriptor.Kind.DIRECTORY));
        var world=prepared.world().variant("shaders");
        assertNotNull(world.nativeTerrain(),world.nativeTerrainIssue());assertNotNull(world.nativeShadow(),world.nativeShadowIssue());
        assertEquals(List.of(0,4,6),world.terrainTargets());assertEquals(List.of(0,1),world.shadowTargets());
        var program=prepared.programs().getFirst();assertTrue(program.vertexSource().contains("oculusLegacyFogStart"));
        assertEquals(3,program.fragment().uniforms().size());
    }
}
