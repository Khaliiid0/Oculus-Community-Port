package dev.oculusforge.render.program;

import static org.junit.jupiter.api.Assertions.*;
import dev.oculusforge.render.world.*;
import java.nio.*;
import java.util.*;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

class AtlasHeldInputsTest {
    private static final String DECL="uniform ivec2 atlasSize;\nuniform int heldItemId,heldItemId2;\n";
    private static final String VALUE="vec4(vec2(atlasSize),float(heldItemId),float(heldItemId2))";
    private static final String V="void main(){gl_Position=ftransform();}",F="void main(){gl_FragColor=vec4(1);}";
    private static WorldEnvironment environment(AtlasSize atlas,HeldItemKeys keys) {
        return new WorldEnvironment(0,0,0,EyeBrightness.DARK,0,0,0,192.33f,0,atlas,keys);
    }
    @Test void nativeTerrainWaterAndShadowBindTypedValuesInBothMeshModes() throws Exception {
        for(boolean shadow:List.of(false,true))for(boolean extended:List.of(false,true)) {
            var program=NativeTerrainProgram.adaptWithMesh(DECL+V.replace("ftransform()","ftransform()+"+VALUE),
                DECL+F.replace("vec4(1)",VALUE),shadow?List.of(0):List.of(0,4),shadow,extended);
            assertTrue(program.usesWorldMatrices());
            for(String source:List.of(program.vertexSource(),program.fragmentSource())) {
                assertTrue(source.contains("vec2(oculusNative_atlasSize)"));
                assertTrue(source.contains("float(oculusNative_heldItemId)"));
                assertTrue(source.contains("float(oculusNative_heldItemId2)"));
                assertTrue(source.contains(NativeWorldBindings.block()));
            }
        }
    }
    @Test void appendOnlyStd140LayoutKeepsPreviousOffsetsAndIntegerPrecision() {
        assertEquals(912,NativeWorldBindings.PREVIOUS_CAMERA_OFFSET);assertEquals(924,NativeWorldBindings.CLOUD_HEIGHT_OFFSET);
        assertEquals(928,NativeWorldBindings.ATLAS_SIZE_OFFSET);assertEquals(936,NativeWorldBindings.HELD_ITEM_IDS_OFFSET);
        assertEquals(976, NativeWorldBindings.BYTE_SIZE);
        var m=new Matrix4f();
        for(var bytes:List.of(ByteBuffer.allocate(NativeWorldBindings.BYTE_SIZE+16),ByteBuffer.allocateDirect(NativeWorldBindings.BYTE_SIZE+16))) {
            bytes.order(ByteOrder.nativeOrder());for(int i=0;i<bytes.capacity();i++)bytes.put(i,(byte)0x5a);
            bytes.position(8).limit(8+NativeWorldBindings.BYTE_SIZE);
            new WorldMatrixData(m,m,m,m,1,2,3,854,480,6000,AnimationClock.ZERO,RelativeEyePosition.ZERO,
                new HeldLight(14,7),environment(new AtlasSize(2048,1024),HeldItemKeys.NONE),new EyeBrightnessSmoother.Value(0,0),
                CustomUniformBindings.Values.EMPTY,0,128,NativeFogData.CLEAR,NativeSceneInputs.DEFAULT,
                new CameraHistory.Position(-12.25,100.5,30_000_000.125),new HeldItemIds(Integer.MIN_VALUE,Integer.MAX_VALUE)).write(bytes);
            assertEquals(-12.25f,bytes.getFloat(920));assertEquals(192.33f,bytes.getFloat(932));
            assertEquals(2048,bytes.getInt(936));assertEquals(1024,bytes.getInt(940));
            assertEquals(Integer.MIN_VALUE,bytes.getInt(944));assertEquals(Integer.MAX_VALUE,bytes.getInt(948));
            assertEquals(14,bytes.getInt(584));assertEquals(7,bytes.getInt(588));
            assertEquals(8,bytes.position());assertEquals(0x5a5a5a5a,bytes.getInt(0));
            assertEquals(0x5a5a5a5a,bytes.duplicate().clear().getInt(8+NativeWorldBindings.BYTE_SIZE));
        }
    }
    @Test void invalidDeclarationsRejectBeforeUnusedPruningOrConstantReplacement() {
        for(String declaration:List.of("uniform vec2 atlasSize;","uniform int atlasSize[2];","uniform ivec2 atlasSize[2];",
            "uniform int heldItemId=1;","uniform float heldItemId2;","uniform int heldItemId,heldItemId;",
            "uniform ivec2 atlasSize=ivec2(1024);","uniform ivec2 atlasSize; uniform ivec2 atlasSize;")) {
            assertThrows(ShaderCompatibilityException.class,()->NativeTerrainProgram.adapt(declaration+"\n"+V,F,List.of(0)),declaration);
            assertThrows(ShaderCompatibilityException.class,()->new LegacyFinalShaderAdapter().adapt(declaration+"\n"+F),declaration);
            assertThrows(ShaderCompatibilityException.class,()->new LegacyFinalVertexAdapter().adapt(declaration+"\n"+V),declaration);
        }
    }
    @Test void postBothStagesUseLiveSemantics() throws Exception {
        var fragment=new LegacyFinalShaderAdapter().adapt(DECL+F.replace("vec4(1)",VALUE));
        var vertex=new LegacyFinalVertexAdapter().adapt(DECL+V.replace("ftransform()","ftransform()+"+VALUE));
        for(var uniforms:List.of(fragment.uniforms(),vertex.uniforms())) {
            assertEquals(List.of(LegacyFinalShaderAdapter.UniformSemantic.ATLAS_SIZE,
                LegacyFinalShaderAdapter.UniformSemantic.HELD_ITEM_ID,LegacyFinalShaderAdapter.UniformSemantic.HELD_ITEM_ID_2),
                uniforms.stream().map(LegacyFinalShaderAdapter.UniformBinding::semantic).toList());
        }
    }
    @Test void postWriterUsesSameSnapshotAndPackMappingWithoutRenderStateOrLightSubstitutes() throws Exception {
        var writer=PackFinalProgram.class.getDeclaredMethod("writeUniform",com.mojang.blaze3d.buffers.Std140Builder.class,
            LegacyFinalShaderAdapter.UniformSemantic.class,net.minecraft.client.renderer.state.level.LevelRenderState.class,
            int.class,int.class,WorldEnvironment.class,WorldClockUniforms.class,AnimationClock.Snapshot.class);
        writer.setAccessible(true);
        var map=ItemMaterialMap.parse("item.-2147483648=torch\nitem.2147483647=custom:trim",Map.of());
        var env=environment(new AtlasSize(2048,1024),new HeldItemKeys("minecraft:torch","custom:trim"));
        try(var program=new PackFinalProgram("test","final",null,List.of(),List.of(),null,false,map)) {
            var bytes=ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder());
            var builder=com.mojang.blaze3d.buffers.Std140Builder.intoBuffer(bytes);
            for(var semantic:List.of(LegacyFinalShaderAdapter.UniformSemantic.ATLAS_SIZE,
                LegacyFinalShaderAdapter.UniformSemantic.HELD_ITEM_ID,LegacyFinalShaderAdapter.UniformSemantic.HELD_ITEM_ID_2))
                writer.invoke(program,builder,semantic,null,854,480,env,null,null);
            assertEquals(2048,bytes.getInt(0));assertEquals(1024,bytes.getInt(4));
            assertEquals(Integer.MIN_VALUE,bytes.getInt(8));assertEquals(Integer.MAX_VALUE,bytes.getInt(12));
        }
    }
    @Test void snapshotDimensionsAreValidatedAndImmutableAcrossFrames() {
        var first=environment(new AtlasSize(2048,1024),new HeldItemKeys("minecraft:torch","minecraft:air"));
        var next=environment(new AtlasSize(4096,2048),new HeldItemKeys("minecraft:air","minecraft:torch"));
        assertEquals(2048,first.atlasSize().width());assertEquals(4096,next.atlasSize().width());
        assertEquals("minecraft:torch",first.heldItems().mainHand());assertEquals("minecraft:air",next.heldItems().mainHand());
        for(int[] pair:List.of(new int[]{-1,1},new int[]{0,1},new int[]{1,0}))
            assertThrows(IllegalArgumentException.class,()->new AtlasSize(pair[0],pair[1]));
        assertEquals(new AtlasSize(0,0),AtlasSize.ABSENT);
        assertThrows(IllegalArgumentException.class,()->new HeldItemKeys("minecraft:Bad",null));
    }
    @Test void fixturePreparesActualMapNativeTerrainShadowAndIndependentPost() throws Exception {
        var root=java.nio.file.Path.of(getClass().getClassLoader().getResource("native-atlas-items").toURI());
        var prepared=new PackProgramCompiler(dev.oculusforge.shaderpack.ShaderPackLimits.DEFAULT).prepare(
            new dev.oculusforge.shaderpack.ShaderPackDescriptor("inputs","Inputs",root,dev.oculusforge.shaderpack.ShaderPackDescriptor.Kind.DIRECTORY));
        var world=prepared.world().variant("shaders");
        assertNotNull(world.nativeTerrain(),world.nativeTerrainIssue());assertNotNull(world.nativeShadow(),world.nativeShadowIssue());
        assertEquals(44002,prepared.items().id("minecraft:torch"));assertEquals(40008,prepared.items().id("minecraft:air"));
        assertEquals(List.of(0,4),world.terrainTargets());
        assertEquals(3,prepared.programs().getFirst().fragment().uniforms().size());
    }
}
