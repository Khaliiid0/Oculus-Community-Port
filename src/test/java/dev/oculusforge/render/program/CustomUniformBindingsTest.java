package dev.oculusforge.render.program;

import static org.junit.jupiter.api.Assertions.*;
import dev.oculusforge.render.world.*;
import java.nio.*;
import java.util.*;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

final class CustomUniformBindingsTest {
    @Test void completePackBindsSameSlotsInTerrainShadowAndPost() throws Exception {
        var root=java.nio.file.Path.of(getClass().getClassLoader().getResource("custom-shared-smoothing").toURI());
        var prepared=new PackProgramCompiler(dev.oculusforge.shaderpack.ShaderPackLimits.DEFAULT).prepare(
            new dev.oculusforge.shaderpack.ShaderPackDescriptor("shared","Shared",root,
                dev.oculusforge.shaderpack.ShaderPackDescriptor.Kind.DIRECTORY));
        var bindings=prepared.variants().get("shaders").customUniforms();
        assertEquals(Map.of("heightProbe",0,"isDesert",1),bindings.slots());
        var world=prepared.world().variant("shaders");
        assertNotNull(world.nativeTerrain(),world.nativeTerrainIssue());
        assertNotNull(world.nativeShadow(),world.nativeShadowIssue());
        for(var nativeProgram:List.of(world.nativeTerrain(),world.nativeShadow()))
            for(String source:List.of(nativeProgram.vertexSource(),nativeProgram.fragmentSource())) {
                assertTrue(source.contains("vec2(oculusNative_oculusCustomValue0,oculusNative_oculusCustomValue1)"));
                assertTrue(source.contains(NativeWorldBindings.block()));
            }
        var post=prepared.variants().get("shaders").programs().getFirst().fragment();
        assertEquals(2,post.uniforms().size());
        assertTrue(post.uniforms().stream().allMatch(u->u.semantic()==LegacyFinalShaderAdapter.UniformSemantic.CUSTOM_FLOAT));
        assertTrue(post.source().contains("vec2(oculusCustomValue0,oculusCustomValue1)"));
    }
    private static CustomUniformBindings bindings() throws Exception {
        return CustomUniformBindings.prepare(NativeCustomUniforms.parse(
            "uniform.float.heightProbe=smooth(11,if(cameraPosition.y>=113,1,0),10,20)\n"
            +"uniform.float.isDesert=smooth(2,if(in(biome,BIOME_DESERT),1,0),10,20)"),
            List.of("uniform float heightProbe, isDesert;\nfloat result=heightProbe+isDesert;"));
    }
    @Test void nativeAndPostConsumersShareOneImmutableUpdateAndReset() throws Exception {
        var binding=bindings();var state=binding.newState();var world=new Object();
        state.update(world,AnimationClock.ZERO,0,0,112,0,NativeBiomeSymbols.fromKey("minecraft:plains"));
        var frame=new AnimationClock.Snapshot(1,1f,1f);
        var nativeValues=state.update(world,frame,0,0,113,0,NativeBiomeSymbols.fromKey("minecraft:desert"));
        assertEquals(.5f,nativeValues.boundSlot(0));assertEquals(.5f,nativeValues.boundSlot(1));
        assertSame(nativeValues,state.update(world,frame,0,0,200,0,0));
        assertEquals(.25f,state.update(world,new AnimationClock.Snapshot(2,2,3),0,0,112,0,0).boundSlot(0));
        assertEquals(.5f,nativeValues.boundSlot(0));
        state.reset();assertEquals(1,state.update(world,frame,0,0,200,0,0).boundSlot(0));
        assertEquals(0,binding.newState().update(world,frame,0,0,0,0,0).boundSlot(0));
        assertEquals(0,state.update(new Object(),frame,0,0,0,0,0).boundSlot(0));
    }
    @Test void nativeWriterPreservesOffsetsAndZerosAllUnusedSlots() throws Exception {
        var values=bindings().newState().update(new Object(),AnimationClock.ZERO,0,0,113,0,0);
        var identity=new Matrix4f();
        for(var bytes:List.of(ByteBuffer.allocate(NativeWorldBindings.BYTE_SIZE + 16),ByteBuffer.allocateDirect(NativeWorldBindings.BYTE_SIZE + 16))) {
            bytes.order(ByteOrder.nativeOrder());
            for(int i=0;i<bytes.capacity();i++) bytes.put(i,(byte)0x5a);
            bytes.position(8).limit(8 + NativeWorldBindings.BYTE_SIZE);
            new WorldMatrixData(identity,identity,identity,identity,1,2,3,854,480,24001,
                AnimationClock.ZERO,RelativeEyePosition.ZERO,HeldLight.NONE,WorldEnvironment.CLEAR,
                new EyeBrightnessSmoother.Value(0,0),values).write(bytes);
            assertEquals(1,bytes.getInt(8+540));assertEquals(0,bytes.getInt(8+620));
            assertEquals(1f,bytes.getFloat(8+624));assertEquals(0f,bytes.getFloat(8+628));
            for(int slot=2;slot<64;slot++) assertEquals(0f,bytes.getFloat(8+624+slot*4));
            assertEquals(8,bytes.position());assertEquals(0x5a5a5a5a,bytes.getInt(0));
            assertEquals(0x5a5a5a5a,bytes.duplicate().clear().getInt(8 + NativeWorldBindings.BYTE_SIZE));
        }
    }
    @Test void crossDimensionOutputsWithReusedIdsCanCoexist() throws Exception {
        var custom=NativeCustomUniforms.parse("uniform.float.isCold=smooth(11,cameraPosition.y,10,10)\n"
            +"uniform.float.isBasalt=smooth(11,cameraPosition.x,10,10)");
        assertEquals(Map.of("isCold",0),CustomUniformBindings.prepare(custom,List.of("uniform float isCold; float x=isCold;")).slots());
        assertEquals(Map.of("isBasalt",0),CustomUniformBindings.prepare(custom,List.of("uniform float isBasalt; float x=isBasalt;")).slots());
        var binding=CustomUniformBindings.prepare(custom,
            List.of("uniform float isCold; float x=isCold;","uniform float isBasalt; float x=isBasalt;"));
        assertEquals(Map.of("isBasalt",0,"isCold",1),binding.slots());
        var state=binding.newState();var world=new Object();
        state.update(world,AnimationClock.ZERO,0,0,0,0,0);
        var values=state.update(world,new AnimationClock.Snapshot(1,1,1),0,4,1,0,0);
        assertEquals(2f,values.boundSlot(0));assertEquals(.5f,values.boundSlot(1));
    }
    @Test void typeAliasesUnknownBranchesAndReservedNamesRejectInsteadOfUsingHeuristics() {
        for(String properties:List.of("uniform.float.isDesert=unsupported(1)",
            "uniform.float.isDesert=1\nuniform.float.\\u0069sDesert=2",
            "#ifdef UNKNOWN\nuniform.float.isDesert=1\n#endif",
            "uniform.float.cameraPosition=2\nuniform.float.isDesert=cameraPosition.y")) {
            var custom=NativeCustomUniforms.parse(properties,Map.of("MC_VERSION","260200"));
            assertThrows(ShaderCompatibilityException.class,()->CustomUniformBindings.prepare(custom,List.of("uniform float isDesert;float x=isDesert;")));
        }
        var custom=NativeCustomUniforms.parse("uniform.float.isDesert=1");
        for(String source:List.of("uniform int isDesert;","uniform float isDesert;uniform float isDesert;",
            "float oculusCustomValue0;"))
            assertThrows(ShaderCompatibilityException.class,()->CustomUniformBindings.prepare(custom,List.of(source)));
    }
    @Test void adaptationPreservesMetadataAndOnlyRewritesDeclaredTokenNames() throws Exception {
        var binding=bindings();
        String source="/* DRAWBUFFERS:0; isDesert */\nuniform float isDesert;\nfloat isDesertScale=isDesert; // isDesert\n";
        String adapted=binding.adapt(source);
        assertTrue(adapted.contains("/* DRAWBUFFERS:0; isDesert */"));
        assertTrue(adapted.contains("float isDesertScale=oculusCustomValue1; // isDesert"));
        assertEquals("float isDesert=2;",binding.adapt("float isDesert=2;"));
        assertThrows(ShaderCompatibilityException.class,()->binding.adapt("float oculusCustomValue1=0;"));
    }
    @Test void slotNamesAndMissingGpuOutputsAreBounded() {
        for(int slot=0;slot<64;slot++) assertEquals(slot,CustomUniformBindings.index(CustomUniformBindings.name(slot)));
        for(String name:List.of("oculusCustomValue64","oculusCustomValue00","oculusCustomValue-1","oculusCustomValueX"))
            assertEquals(-1,CustomUniformBindings.index(name));
        assertThrows(IllegalArgumentException.class,()->CustomUniformBindings.name(64));
        assertThrows(IllegalStateException.class,()->CustomUniformBindings.Values.EMPTY.boundSlot(0));
    }
    @Test void unusedDefinitionsDoNotCreateChannelsOrInvokeUnsupportedCode() throws Exception {
        var custom=NativeCustomUniforms.parse("uniform.float.good=smooth(11,cameraPosition.y,10,10)\n"
            +"uniform.float.unused=smooth(11,unsupported,10,10)");
        var binding=CustomUniformBindings.prepare(custom,List.of("uniform float good, unused;float x=good;"));
        assertEquals(Map.of("good",0),binding.slots());
        assertEquals("uniform float oculusCustomValue0;float x=oculusCustomValue0;",
            binding.adapt("uniform float good, unused;float x=good;"));
    }
    @Test void customEffectExpressionUsesValidatedSnapshotInputs() throws Exception {
        var binding=CustomUniformBindings.prepare(NativeCustomUniforms.parse("uniform.float.blindFactor=max(blindness,darknessFactor)"),
            List.of("uniform float blindFactor;float x=blindFactor;"));
        var state=binding.newState();var world=new Object();
        assertEquals(.75f,state.update(world,AnimationClock.ZERO,0,0,0,0,0,.25f,.75f).boundSlot(0));
        assertThrows(IllegalArgumentException.class,()->state.update(world,new AnimationClock.Snapshot(1,0,0),0,0,0,0,0,Float.NaN,0));
        assertEquals(.5f,state.update(world,new AnimationClock.Snapshot(1,0,0),0,0,0,0,0,.5f,0).boundSlot(0));
        assertThrows(IllegalArgumentException.class,()->new WorldEnvironment(0,0,0,EyeBrightness.DARK,0,0,2));
    }
    @Test void dimensionPostAndFallbackNativeSourcesShareRemappedSlots(
        @org.junit.jupiter.api.io.TempDir java.nio.file.Path root) throws Exception {
        var shaders=java.nio.file.Files.createDirectories(root.resolve("shaders"));
        var dimension=java.nio.file.Files.createDirectories(shaders.resolve("world-1"));
        java.nio.file.Files.writeString(shaders.resolve("shaders.properties"),"uniform.float.nativeProbe=1\nuniform.float.aPost=2");
        java.nio.file.Files.writeString(shaders.resolve("gbuffers_terrain.vsh"),"#version 120\nvoid main(){gl_Position=ftransform();}");
        java.nio.file.Files.writeString(shaders.resolve("gbuffers_terrain.fsh"),
            "#version 120\nuniform float nativeProbe;\nvoid main(){gl_FragColor=vec4(nativeProbe);}");
        java.nio.file.Files.writeString(shaders.resolve("final.fsh"),"#version 120\nvoid main(){gl_FragColor=vec4(1);}");
        java.nio.file.Files.writeString(dimension.resolve("final.fsh"),
            "#version 120\nuniform float aPost;\nvoid main(){gl_FragColor=vec4(aPost);}");
        var prepared=new PackProgramCompiler(dev.oculusforge.shaderpack.ShaderPackLimits.DEFAULT).prepare(
            new dev.oculusforge.shaderpack.ShaderPackDescriptor("fallback","Fallback",root,
                dev.oculusforge.shaderpack.ShaderPackDescriptor.Kind.DIRECTORY));
        assertEquals(Map.of("aPost",0,"nativeProbe",1),prepared.variants().get("shaders/world-1").customUniforms().slots());
        assertTrue(prepared.world().variant("shaders/world-1").nativeTerrain().fragmentSource().contains("vec4(oculusNative_oculusCustomValue1)"));
        assertTrue(prepared.world().variant("shaders").nativeTerrain().fragmentSource().contains("vec4(oculusNative_oculusCustomValue0)"));
    }
    @Test void integerModuloDoesNotRoundCountersBeforeTakingRemainder() {
        var runtime=CustomExpressionRuntime.compile(Map.of("a","frameCounter % 8","b","worldTime % 7"),Set.of("a","b")).newRuntime();
        var result=runtime.update(new Object(),new Object(),new CustomExpressionRuntime.Sample(23999,16777217,0,0,0,0),0);
        assertEquals(1,result.value("a"));assertEquals(23999%7,result.value("b"));
        for(String expression:List.of("frameCounter%0","worldTime%1000001","worldTime%-2","cameraPosition.y%2"))
            assertThrows(RuntimeException.class,()->CustomExpressionRuntime.compile(Map.of("a",expression),Set.of("a")));
    }
}
