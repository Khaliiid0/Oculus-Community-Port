package dev.oculusforge.render.program;

import static org.junit.jupiter.api.Assertions.*;
import dev.oculusforge.render.world.*;
import java.nio.*;
import java.util.List;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

class NativeSkyColorTest {
    private static final String V="void main(){gl_Position=ftransform();}";
    private static final String F="void main(){gl_FragColor=vec4(1);}";

    @Test void packedRgbPreservesByteChannelsAndIgnoresAlphaWithoutGammaConversion() {
        for(int alpha:List.of(0,1,127,255)) {
            var sky=SkyColor.fromArgb(alpha<<24|0x2080e0);
            assertEquals(32/255f,sky.red());assertEquals(128/255f,sky.green());assertEquals(224/255f,sky.blue());
        }
        assertEquals(SkyColor.BLACK,SkyColor.fromArgb(0xff000000));
        assertEquals(new SkyColor(1,1,1),SkyColor.fromArgb(0xffffffff));
    }

    @Test void invalidFloatInputsAndMissingSnapshotRejectInsteadOfClamping() {
        for(float bad:new float[]{Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class,()->new SkyColor(bad,0,0));
            assertThrows(IllegalArgumentException.class,()->new SkyColor(0,bad,0));
            assertThrows(IllegalArgumentException.class,()->new SkyColor(0,0,bad));
        }
        assertThrows(NullPointerException.class,()->WorldEnvironment.CLEAR.withSkyColor(null));
    }

    @Test void floatRgbPreservesPrecisionAndUnboundedFiniteEnvironmentValues() {
        var vector=new org.joml.Vector3f(1.25f,-.01f,.1234567f);
        var color=SkyColor.fromRgb(vector);
        vector.set(0,0,0);
        assertEquals(new SkyColor(1.25f,-.01f,.1234567f),color);
        assertThrows(NullPointerException.class,()->SkyColor.fromRgb(null));
    }

    @Test void fogCompletionAndFreshSnapshotsRetainTheirOwnSkyRatherThanMutableSkyState() {
        var first=WorldEnvironment.CLEAR.withSkyColor(SkyColor.fromArgb(0x123456));
        var fog=new NativeFogData(1,.1f,.2f,.3f,.4f,12,84);
        var completed=first.withFog(fog);
        assertSame(first.skyColor(),completed.skyColor());assertSame(fog,completed.fog());
        var second=completed.withSkyColor(SkyColor.fromArgb(0xabcdef));
        assertEquals(SkyColor.fromArgb(0x123456),first.skyColor());
        assertEquals(SkyColor.fromArgb(0x123456),completed.skyColor());
        assertNotEquals(first.skyColor(),second.skyColor());assertSame(fog,second.fog());
    }

    @Test void terrainAndShadowBindRealVec3InBothStagesAndMeshModes() throws Exception {
        for(boolean shadow:List.of(false,true))for(boolean extended:List.of(false,true)) {
            var program=NativeTerrainProgram.adaptWithMesh("uniform vec3 skyColor;\n"
                +V.replace("ftransform()","ftransform()+vec4(skyColor,0)"),
                "uniform vec3 skyColor;\n"+F.replace("vec4(1)","vec4(skyColor,1)"),List.of(0),shadow,extended);
            assertTrue(program.usesWorldMatrices());assertFalse(program.needsShadowMatrices());
            for(String source:List.of(program.vertexSource(),program.fragmentSource())) {
                assertTrue(source.contains("vec4(oculusNative_skyColor.xyz,"));
                assertTrue(source.contains(NativeWorldBindings.block()));
            }
        }
    }

    @Test void unusedWrongTypesArraysInitializersDuplicatesAndReservedNamesReject() {
        for(String declaration:List.of("uniform float skyColor;","uniform vec4 skyColor;","uniform vec3 skyColor[2];",
            "uniform vec3 skyColor=vec3(0);","uniform vec3 skyColor;\nuniform vec3 skyColor;","uniform vec3 oculusNative_skyColor;")) {
            assertThrows(ShaderCompatibilityException.class,()->NativeTerrainProgram.adapt(declaration+V,F,List.of(0)),declaration);
            assertThrows(ShaderCompatibilityException.class,()->NativeTerrainProgram.adapt(V,declaration+F,List.of(0)),declaration);
        }
    }

    @Test void similarlyNamedLocalsAndMembersAreNotRewritten() throws Exception {
        var adapted=NativeWorldBindings.adapt("uniform vec3 skyColor;\n"
            +"vec3 result=skyColor;struct Probe{vec3 skyColor;};\n"
            +"void main(){Probe p;p.skyColor=vec3(1);vec3 skyColorScale=skyColor;}");
        assertTrue(adapted.source().contains("vec3 result=oculusNative_skyColor.xyz;"));
        assertTrue(adapted.source().contains("p.skyColor=vec3(1)"));
        assertTrue(adapted.source().contains("vec3 skyColorScale=oculusNative_skyColor.xyz;"));
        // Existing conservative scope rule remains: never rewrite a shadowing parameter.
        assertThrows(ShaderCompatibilityException.class,()->NativeWorldBindings.adapt(
            "uniform vec3 skyColor;vec3 helper(vec3 skyColor){return skyColor;}"));
    }

    @Test void appendedStd140LanePreservesEarlierOffsetsAndHeapDirectSliceGuards() {
        assertEquals(960,NativeWorldBindings.SKY_COLOR_OFFSET);assertEquals(976,NativeWorldBindings.BYTE_SIZE);
        assertEquals(880,NativeWorldBindings.FOG_VALUES_OFFSET);assertEquals(948,NativeWorldBindings.FOG_RANGE_OFFSET);
        var m=new Matrix4f();var sky=SkyColor.fromArgb(0x2080e0);
        var env=WorldEnvironment.CLEAR.withSkyColor(sky);
        for(var bytes:List.of(ByteBuffer.allocate(992),ByteBuffer.allocateDirect(992))) {
            bytes.order(ByteOrder.nativeOrder());for(int i=0;i<bytes.capacity();i++)bytes.put(i,(byte)0x5a);
            bytes.position(8).limit(984);
            new WorldMatrixData(m,m,m,m,1,2,3,854,480,6000,AnimationClock.ZERO,RelativeEyePosition.ZERO,HeldLight.NONE,
                env,new EyeBrightnessSmoother.Value(0,0)).write(bytes);
            assertEquals(sky.red(),bytes.getFloat(968));assertEquals(sky.green(),bytes.getFloat(972));
            assertEquals(sky.blue(),bytes.getFloat(976));assertEquals(0,bytes.getFloat(980));
            assertEquals(1,bytes.getFloat(520));assertEquals(1,bytes.getFloat(532));
            assertEquals(1,bytes.getFloat(960)); // Existing fog end (952 + slice base) is untouched.
            assertEquals(8,bytes.position());assertEquals(0x5a5a5a5a,bytes.getInt(0));
            assertEquals(0x5a5a5a5a,bytes.duplicate().clear().getInt(984));
        }
    }

    @Test void postSkyWriterReadsOnlyTheImmutableEnvironmentSnapshot() throws Exception {
        var writer=PackFinalProgram.class.getDeclaredMethod("writeUniform",com.mojang.blaze3d.buffers.Std140Builder.class,
            LegacyFinalShaderAdapter.UniformSemantic.class,net.minecraft.client.renderer.state.level.LevelRenderState.class,
            int.class,int.class,WorldEnvironment.class,WorldClockUniforms.class,AnimationClock.Snapshot.class);writer.setAccessible(true);
        try(var program=new PackFinalProgram("sky","final",null,List.of(),List.of(),null)) {
            var bytes=ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder());
            var sky=SkyColor.fromArgb(0x2080e0);var env=WorldEnvironment.CLEAR.withSkyColor(sky);
            writer.invoke(program,com.mojang.blaze3d.buffers.Std140Builder.intoBuffer(bytes),
                LegacyFinalShaderAdapter.UniformSemantic.SKY_COLOR,null,854,480,env,null,null);
            assertEquals(sky.red(),bytes.getFloat(0));assertEquals(sky.green(),bytes.getFloat(4));assertEquals(sky.blue(),bytes.getFloat(8));
        }
    }

    @Test void postStagesValidateSkyTypesBeforeUnusedPruningAndCustomOverridesRemainForbidden() throws Exception {
        for(String declaration:List.of("uniform float skyColor;","uniform vec3 skyColor[2];","uniform vec3 skyColor=vec3(0);",
            "uniform vec3 skyColor;\nuniform vec3 skyColor;")) {
            assertThrows(ShaderCompatibilityException.class,()->new LegacyFinalShaderAdapter().adapt(declaration+F));
            assertThrows(ShaderCompatibilityException.class,()->new LegacyFinalVertexAdapter().adapt(declaration+V));
        }
        assertThrows(ShaderCompatibilityException.class,()->CustomUniformBindings.prepare(
            NativeCustomUniforms.parse("uniform.float.skyColor=0\nuniform.float.probe=skyColor"),
            List.of("uniform float probe;float value=probe;")));
    }

    @Test void diagnosticPreparesTerrainShadowAndIndependentPostSkyBindings() throws Exception {
        var root=java.nio.file.Path.of(getClass().getClassLoader().getResource("native-sky-color").toURI());
        var prepared=new PackProgramCompiler(dev.oculusforge.shaderpack.ShaderPackLimits.DEFAULT).prepare(
            new dev.oculusforge.shaderpack.ShaderPackDescriptor("sky-color","Sky color",root,
                dev.oculusforge.shaderpack.ShaderPackDescriptor.Kind.DIRECTORY));
        var world=prepared.world().variant("shaders");
        assertNotNull(world.nativeTerrain(),world.nativeTerrainIssue());assertNotNull(world.nativeShadow(),world.nativeShadowIssue());
        assertEquals(List.of(0,4),world.terrainTargets());assertTrue(world.nativeTerrain().usesWorldMatrices());
        var program=prepared.programs().getFirst();
        assertTrue(program.vertexSource().contains("skyFromPostVertex=skyColor"));
        assertEquals(1,program.fragment().uniforms().stream()
            .filter(u->u.semantic()==LegacyFinalShaderAdapter.UniformSemantic.SKY_COLOR).count());
    }
}
