package dev.oculusforge.render.program;

import static org.junit.jupiter.api.Assertions.*;
import dev.oculusforge.render.world.*;
import java.nio.*;
import java.util.*;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

class DarknessLightTest {
    private static final String DECL="uniform float darknessFactor,darknessLightFactor;\n";
    private static final String V="void main(){gl_Position=ftransform();}",F="void main(){gl_FragColor=vec4(1);}";
    private static WorldEnvironment environment(float blend,float light) {
        return new WorldEnvironment(0,0,0,EyeBrightness.DARK,0,0,blend,192.33f,0,
            new AtlasSize(2048,1024),HeldItemKeys.NONE,light);
    }
    @Test void readsTheAlreadyExtractedLightmapWithoutRecalculatingItsPulseOrOptions() {
        var state=new net.minecraft.client.renderer.state.LightmapRenderState();
        for(boolean update:List.of(false,true))for(float value:new float[]{0,.03125f,.1125f,.45f,1.5f}) {
            state.needsUpdate=update;state.darknessEffectScale=value;state.brightness=.8f;
            assertEquals(value,DarknessLight.capture(state));
        }
        assertThrows(NullPointerException.class,()->DarknessLight.capture(null));
    }
    @Test void frameSnapshotCannotFollowLaterMutableLightmapUpdates() {
        var state=new net.minecraft.client.renderer.state.LightmapRenderState();state.darknessEffectScale=.125f;
        var first=environment(.9f,DarknessLight.capture(state));state.darknessEffectScale=0;
        var next=environment(.9f,DarknessLight.capture(state));
        assertEquals(.125f,first.darknessLightFactor());assertEquals(0,next.darknessLightFactor());
        assertEquals(.9f,first.darknessFactor());assertEquals(.9f,next.darknessFactor());
        assertEquals(0,WorldEnvironment.CLEAR.darknessLightFactor());
    }
    @Test void invalidLightmapOrEnvironmentValuesRejectAndFiniteModdedAmplitudesAreNotClamped() {
        for(float bad:new float[]{Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY,-.1f}) {
            var state=new net.minecraft.client.renderer.state.LightmapRenderState();state.darknessEffectScale=bad;
            assertThrows(IllegalArgumentException.class,()->DarknessLight.capture(state));
            assertThrows(IllegalArgumentException.class,()->environment(1,bad));
        }
        assertEquals(1.5f,environment(1,1.5f).darknessLightFactor());
    }
    @Test void nativeStagesAndBothMeshModesUseDistinctRawAndPulsingBindings() throws Exception {
        for(boolean shadow:List.of(false,true))for(boolean extended:List.of(false,true)) {
            var program=NativeTerrainProgram.adaptWithMesh(DECL+V.replace("ftransform()","ftransform()+vec4(darknessLightFactor+darknessFactor)"),
                DECL+F.replace("vec4(1)","vec4(darknessLightFactor,darknessFactor,0,1)"),List.of(0),shadow,extended);
            assertTrue(program.usesWorldMatrices());
            for(String source:List.of(program.vertexSource(),program.fragmentSource())) {
                assertTrue(source.contains("oculusNative_darknessLightFactor"));
                assertTrue(source.contains("oculusNative_darknessFactor"));
                assertTrue(source.contains(NativeWorldBindings.block()));
            }
        }
        var names=NativeWorldBindings.adapt("uniform float darknessLightFactor; float darknessLightFactorGain=darknessLightFactor;");
        assertTrue(names.source().contains("float darknessLightFactorGain=oculusNative_darknessLightFactor;"));
    }
    @Test void invalidLiveDeclarationsRejectEvenIfUnusedOrDefaultedInAnyStage() {
        for(String decl:List.of("uniform int darknessLightFactor;","uniform float darknessLightFactor[2];",
            "uniform float darknessLightFactor=0.;","uniform float darknessLightFactor=computePulse();",
            "uniform float darknessLightFactor,darknessLightFactor;")) {
            assertThrows(ShaderCompatibilityException.class,()->NativeTerrainProgram.adapt(decl+"\n"+V,F,List.of(0)),decl);
            assertThrows(ShaderCompatibilityException.class,()->new LegacyFinalShaderAdapter().adapt(decl+"\n"+F),decl);
            assertThrows(ShaderCompatibilityException.class,()->new LegacyFinalVertexAdapter().adapt(decl+"\n"+V),decl);
        }
    }
    @Test void appendedFloatAndPaddingPreserveAtlasItemAndPreviousCameraOffsets() {
        assertEquals(944,NativeWorldBindings.DARKNESS_LIGHT_OFFSET);assertEquals(976,NativeWorldBindings.BYTE_SIZE);
        assertEquals(928,NativeWorldBindings.ATLAS_SIZE_OFFSET);assertEquals(936,NativeWorldBindings.HELD_ITEM_IDS_OFFSET);
        var m=new Matrix4f();
        for(var bytes:List.of(ByteBuffer.allocate(NativeWorldBindings.BYTE_SIZE+16),ByteBuffer.allocateDirect(NativeWorldBindings.BYTE_SIZE+16))) {
            bytes.order(ByteOrder.nativeOrder());for(int i=0;i<bytes.capacity();i++)bytes.put(i,(byte)0x5a);
            bytes.position(8).limit(8+NativeWorldBindings.BYTE_SIZE);
            new WorldMatrixData(m,m,m,m,1,2,3,854,480,6000,AnimationClock.ZERO,RelativeEyePosition.ZERO,HeldLight.NONE,
                environment(.75f,.125f),new EyeBrightnessSmoother.Value(0,0),CustomUniformBindings.Values.EMPTY,0,128,
                NativeFogData.CLEAR,NativeSceneInputs.DEFAULT,new CameraHistory.Position(-12,100,30),
                new HeldItemIds(Integer.MIN_VALUE,Integer.MAX_VALUE)).write(bytes);
            assertEquals(.75f,bytes.getFloat(908));assertEquals(-12,bytes.getFloat(920));
            assertEquals(2048,bytes.getInt(936));assertEquals(1024,bytes.getInt(940));
            assertEquals(Integer.MIN_VALUE,bytes.getInt(944));assertEquals(Integer.MAX_VALUE,bytes.getInt(948));
            assertEquals(.125f,bytes.getFloat(952));
            assertEquals(0,bytes.getFloat(956));assertEquals(1,bytes.getFloat(960));assertEquals(0,bytes.getFloat(964));
            assertEquals(8,bytes.position());assertEquals(0x5a5a5a5a,bytes.getInt(0));
            assertEquals(0x5a5a5a5a,bytes.duplicate().clear().getInt(8+NativeWorldBindings.BYTE_SIZE));
        }
    }
    @Test void oldSizedOrReadOnlyDestinationsRejectBeforeWriting() {
        var m=new Matrix4f();var data=new WorldMatrixData(m,m,m,m,0,0,0,854,480);
        assertThrows(IllegalArgumentException.class,()->data.write(ByteBuffer.allocate(944)));
        assertThrows(IllegalArgumentException.class,()->data.write(ByteBuffer.allocate(960).asReadOnlyBuffer()));
    }
    @Test void postStagesHaveSeparateSemanticsRatherThanTheOldAlias() throws Exception {
        var fragment=new LegacyFinalShaderAdapter().adapt(DECL+F.replace("vec4(1)","vec4(darknessLightFactor+darknessFactor)"));
        var vertex=new LegacyFinalVertexAdapter().adapt(DECL+V.replace("ftransform()","ftransform()+vec4(darknessLightFactor+darknessFactor)"));
        for(var uniforms:List.of(fragment.uniforms(),vertex.uniforms()))
            assertEquals(List.of(LegacyFinalShaderAdapter.UniformSemantic.DARKNESS_FACTOR,
                LegacyFinalShaderAdapter.UniformSemantic.DARKNESS_LIGHT_FACTOR),uniforms.stream().map(LegacyFinalShaderAdapter.UniformBinding::semantic).toList());
    }
    @Test void postWriterUsesIndependentSnapshotValuesWithoutLevelOrCameraLookups() throws Exception {
        var writer=PackFinalProgram.class.getDeclaredMethod("writeUniform",com.mojang.blaze3d.buffers.Std140Builder.class,
            LegacyFinalShaderAdapter.UniformSemantic.class,net.minecraft.client.renderer.state.level.LevelRenderState.class,
            int.class,int.class,WorldEnvironment.class,WorldClockUniforms.class,AnimationClock.Snapshot.class);
        writer.setAccessible(true);
        try(var program=new PackFinalProgram("test","final",null,List.of(),List.of(),null)) {
            var bytes=ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder());
            var builder=com.mojang.blaze3d.buffers.Std140Builder.intoBuffer(bytes);
            for(var semantic:List.of(LegacyFinalShaderAdapter.UniformSemantic.DARKNESS_FACTOR,
                LegacyFinalShaderAdapter.UniformSemantic.DARKNESS_LIGHT_FACTOR))
                writer.invoke(program,builder,semantic,null,854,480,environment(.75f,.125f),null,null);
            assertEquals(.75f,bytes.getFloat(0));assertEquals(.125f,bytes.getFloat(4));
        }
    }
    @Test void fixturePreparesTerrainShadowAndAnIndependentPostRead() throws Exception {
        var root=java.nio.file.Path.of(getClass().getClassLoader().getResource("native-darkness-light").toURI());
        var prepared=new PackProgramCompiler(dev.oculusforge.shaderpack.ShaderPackLimits.DEFAULT).prepare(
            new dev.oculusforge.shaderpack.ShaderPackDescriptor("darkness","Darkness",root,dev.oculusforge.shaderpack.ShaderPackDescriptor.Kind.DIRECTORY));
        var world=prepared.world().variant("shaders");
        assertNotNull(world.nativeTerrain(),world.nativeTerrainIssue());assertNotNull(world.nativeShadow(),world.nativeShadowIssue());
        assertEquals(List.of(0,4),world.terrainTargets());
        assertTrue(prepared.programs().getFirst().fragment().uniforms().stream()
            .anyMatch(u->u.semantic()==LegacyFinalShaderAdapter.UniformSemantic.DARKNESS_LIGHT_FACTOR));
    }
    @Test void customPropertiesCannotReplaceTheLiveHostValue() {
        for(String property:List.of("uniform.float.darknessLightFactor=0","variable.float.darknessLightFactor=1"))
            assertThrows(ShaderCompatibilityException.class,()->CustomUniformBindings.prepare(NativeCustomUniforms.parse(property
                +"\nuniform.float.probe=darknessLightFactor"),List.of("uniform float probe; float value=probe;")));
    }
    @Test void quantizedDiagnosticMarkersRecognizeBothGoodAndBadNativeSamples() {
        float alpha=Math.round(.375f*255)/255f;
        assertTrue(Math.abs(alpha-.375f)<.01);assertFalse(Math.abs(1f-.375f)<.01);
        for(float marker:new float[]{.25f,.75f}) {
            float quantized=Math.round(marker*255)/255f;
            assertTrue(Math.abs(quantized-marker)<.01);assertEquals(marker>.5f,quantized>.5f);
        }
    }
}
