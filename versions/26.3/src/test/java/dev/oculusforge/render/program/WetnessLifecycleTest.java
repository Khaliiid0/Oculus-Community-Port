package dev.oculusforge.render.program;

import static org.junit.jupiter.api.Assertions.*;
import dev.oculusforge.shaderpack.*;
import dev.oculusforge.render.world.*;
import dev.oculusforge.port.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.lwjgl.util.shaderc.Shaderc;
import org.objectweb.asm.*;

class WetnessLifecycleTest {
    private PreparedPackPipeline prepare(String fixture, int mode) throws Exception {
        var root=Path.of(Objects.requireNonNull(getClass().getClassLoader().getResource(fixture)).toURI());
        return new PackProgramCompiler(ShaderPackLimits.DEFAULT).prepare(
            new ShaderPackDescriptor(fixture,fixture,root,ShaderPackDescriptor.Kind.DIRECTORY),
            MaterialFormat.DEFAULT,Map.of("NEGATIVE_MODE",Integer.toString(mode)));
    }
    private CustomUniformBindings reference() throws Exception {
        return CustomUniformBindings.prepare(NativeCustomUniforms.parse(
            "uniform.float.wetReference=smooth(7001,rainStrength,10,20)"),
            List.of("uniform float wetReference;float result=wetReference;"));
    }
    private float reference(CustomUniformBindings.State state,Object world,AnimationClock.Snapshot frame,float rain) {
        return state.update(world,frame,0,0,0,0,0,0,0,rain,EyeBrightness.DARK).boundSlot(0);
    }

    @Test void postOnlyProbeHasNoNativeWorldOrRetainedBorrowedBuffers() throws Exception {
        var pack=prepare("wetness-post-only",0);
        assertTrue(pack.world().variants().isEmpty());
        assertEquals(Set.of("shaders"),pack.variants().keySet());
        var variant=pack.variants().get("shaders");
        assertEquals(List.of("composite","composite1","final"),variant.programs().stream().map(PreparedFinalProgram::stageName).toList());
        assertEquals(new WetnessSettings(100,200),variant.wetnessSettings());
        assertEquals(Map.of("wetReference",0),variant.customUniforms().slots());
        assertTrue(variant.buffers().borrowedColorFormats().isEmpty());
        assertTrue(variant.buffers().retainedColorSlots().isEmpty());
        assertTrue(variant.buffers().scratchFormats().contains(ColorBufferFormat.RGBA32F));
        for(var program:variant.programs()){
            assertTrue(program.fragment().uniforms().stream().anyMatch(u->u.semantic()==LegacyFinalShaderAdapter.UniformSemantic.WETNESS));
            assertTrue(program.fragment().uniforms().stream().anyMatch(u->u.semantic()==LegacyFinalShaderAdapter.UniformSemantic.CUSTOM_FLOAT));
            assertTrue(program.vertexSource().contains("vertexWetness=wetness"));
            assertTrue(program.fragment().samplers().stream().allMatch(s->s.texture()==LegacyFinalShaderAdapter.TextureSemantic.COLOR));
        }
    }

    @Test void independentCustomReferenceMatchesAnalyticSecondsNotWetnessImplementation() throws Exception {
        var state=reference().newState(); Object world=new Object();
        assertEquals(0,reference(state,world,AnimationClock.ZERO,0));
        assertEquals(.5f,reference(state,world,new AnimationClock.Snapshot(1,1,1),1));
        assertEquals(.75f,reference(state,world,new AnimationClock.Snapshot(2,1,2),1));
        assertEquals(.375f,reference(state,world,new AnimationClock.Snapshot(3,2,4),0));
        assertEquals(.1875f,reference(state,world,new AnimationClock.Snapshot(4,2,6),0));
        var calls=new ArrayList<String>();
        try(var input=Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream(
            "dev/oculusforge/render/program/CustomExpressionRuntime.class"))){
            new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9){
                @Override public MethodVisitor visitMethod(int a,String n,String d,String s,String[] e){
                    return new MethodVisitor(Opcodes.ASM9){
                        @Override public void visitMethodInsn(int o,String owner,String name,String desc,boolean itf){calls.add(owner+"."+name);}
                    };
                }
            },ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
        }
        assertTrue(calls.stream().anyMatch(c->c.endsWith("ScalarSmoother.blend")));
        assertFalse(calls.stream().anyMatch(c->c.contains("WetnessSmoother")));
    }

    @Test void actualPostProbeSettingsAndCustomReferenceUseTenAndTwentySeconds() throws Exception {
        var prepared=prepare("wetness-post-only",0).variants().get("shaders");
        var custom=prepared.customUniforms().newState();
        var wetness=new WetnessSmoother(prepared.wetnessSettings());
        var world=new Object();
        reference(custom,world,AnimationClock.ZERO,0);wetness.update(world,AnimationClock.ZERO,0,0);
        var rise=new AnimationClock.Snapshot(1,10,10);
        assertEquals(.5f,reference(custom,world,rise,1));
        assertEquals(.5f,wetness.update(world,rise,1,10).value());
        var fall=new AnimationClock.Snapshot(2,20,30);
        assertEquals(.25f,reference(custom,world,fall,0));
        assertEquals(.25f,wetness.update(world,fall,0,20).value());
    }

    @Test void referenceAndProductionAgreeAcrossIrregularRainFramesAndEpochs() throws Exception {
        var custom=reference().newState();
        var wetness=new WetnessSmoother(new WetnessSettings(10,20));
        var random=new Random(7711);Object world=new Object();
        for(int i=0;i<1500;i++){
            if(i%100==0)world=new Object();
            float rain=random.nextFloat(),seconds=random.nextFloat()*.25f;
            var frame=new AnimationClock.Snapshot(i%7,seconds,i%3500);
            float expected=reference(custom,world,frame,rain);
            assertEquals(expected,wetness.update(world,frame,rain,seconds).value(),2e-7);
            assertEquals(expected,reference(custom,world,frame,0),2e-7);
            assertEquals(expected,wetness.update(world,frame,0,20).value(),2e-7);
        }
    }

    @Test void postConsumersSerializeReseededValuesOnWorldChangesWithTheSameFrameIdentity() throws Exception {
        var shared=new WetnessSmoother(new WetnessSettings(10,20));
        var oldWorld=new Object();var newWorld=new Object();
        var frame=new AnimationClock.Snapshot(1,1,1);
        shared.update(oldWorld,AnimationClock.ZERO,0,0);
        assertEquals(.5f,shared.update(oldWorld,frame,1,1).value());
        var writer=PackFinalProgram.class.getDeclaredMethod("writeUniform",
            com.mojang.blaze3d.buffers.Std140Builder.class,LegacyFinalShaderAdapter.UniformSemantic.class,
            net.minecraft.client.renderer.state.level.LevelRenderState.class,int.class,int.class,
            WorldEnvironment.class,WorldClockUniforms.class,AnimationClock.Snapshot.class);
        writer.setAccessible(true);
        for(int stage=0;stage<3;stage++){
            try(var program=new PackFinalProgram("post-only","composite"+stage,null,List.of(),List.of(),null)){
                var environment=new WorldEnvironment(0,0);
                program.prepareWetness(newWorld,frame,environment,shared);
                var bytes=ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder());
                writer.invoke(program,com.mojang.blaze3d.buffers.Std140Builder.intoBuffer(bytes),
                    LegacyFinalShaderAdapter.UniformSemantic.WETNESS,null,960,540,environment,null,frame);
                assertEquals(0,bytes.getFloat(0));
                program.resetTemporalHistory();
                assertEquals(0,shared.update(newWorld,frame,1,10).value());
            }
        }
        assertEquals(1,shared.update(oldWorld,frame,1,0).value(),"Returning to an old world is a fresh epoch, not a restored .5 history");
    }

    @Test void logoutAndCloseResetAllDimensionVariantsNotOnlyCurrentlySelected() throws Exception {
        var bindings=reference();
        var first=new PackShaderPipeline.Variant(List.of(),null,new EyeBrightnessSmoother(10),bindings.newState(),
            new CameraHistory(),null,null,new WetnessSmoother(new WetnessSettings(10,20)));
        var second=new PackShaderPipeline.Variant(List.of(),null,new EyeBrightnessSmoother(10),bindings.newState(),
            new CameraHistory(),null,null,new WetnessSmoother(new WetnessSettings(30,40)));
        var world=new Object();var frame=new AnimationClock.Snapshot(1,1,1);
        try(var pack=new PackShaderPipeline("epochs",Map.of("shaders",first,"shaders/world-1",second))){
            assertNotSame(first.wetness(),second.wetness());
            for(var variant:List.of(first,second)){
                variant.wetness().update(world,AnimationClock.ZERO,0,0);
                reference(variant.customUniforms(),world,AnimationClock.ZERO,0);
                variant.wetness().update(world,frame,1,1);
                reference(variant.customUniforms(),world,frame,1);
            }
            pack.resetTemporalHistory();
            for(var variant:List.of(first,second)){
                assertEquals(1,variant.wetness().update(world,frame,1,0).value());
                assertEquals(1,reference(variant.customUniforms(),world,frame,1));
            }
        }
        for(var variant:List.of(first,second)){
            assertEquals(0,variant.wetness().update(world,frame,0,0).value());
            assertEquals(0,reference(variant.customUniforms(),world,frame,0));
        }
    }

    @Test void lifecycleProbeKeepsIndependentHistoryAndLongEnoughEpochLag() throws Exception {
        var pack=prepare("wetness-lifecycle",0);
        assertEquals(new WetnessSettings(100,200),pack.variants().get("shaders").wetnessSettings());
        assertNotNull(pack.world().variant("shaders").nativeTerrain());
        var source=Files.readString(Path.of(Objects.requireNonNull(getClass().getClassLoader().getResource(
            "wetness-lifecycle/shaders/composite.fsh")).toURI()));
        assertTrue(source.contains("frameCounter+720719"));
        assertTrue(source.contains("mix(old.r,rainStrength"));
        assertFalse(source.contains("mix(wetness,rainStrength"));
        var stale=new WetnessSmoother(new WetnessSettings(100,200));
        var world=new Object();stale.update(world,new Object(),1,0);
        assertTrue(stale.update(world,new Object(),0,30).value()>.3,"A leaked epoch must remain detectable after a slow dimension load");
    }

    @Test void lifecycleAndPostOnlyPositiveAndNegativeSourcesCompileWithRealShaderc() throws Exception {
        for(String fixture:List.of("wetness-post-only","wetness-lifecycle"))
            for(int mode:fixture.equals("wetness-lifecycle")?new int[]{0,1,3}:new int[]{0,1}){
            var pack=prepare(fixture,mode);
            for(var variant:pack.world().variants().values())for(var program:variant.nativePrograms())for(boolean indirect:List.of(false,true)){
                var pair=ShadercInterfacePair.adapt(TerrainShaderAbi.vertex(program.vertexSource(),indirect),program.fragmentSource());
                compile(pair.vertex(),Shaderc.shaderc_vertex_shader);compile(pair.fragment(),Shaderc.shaderc_fragment_shader);
            }
            for(var program:pack.programs()){
                var pair=ShadercInterfacePair.adapt(program.vertexSource(),program.fragment().source());
                compile(pair.vertex(),Shaderc.shaderc_vertex_shader);compile(pair.fragment(),Shaderc.shaderc_fragment_shader);
            }
        }
    }

    @Test void actualLogoutAndWorldTargetCallSitesKeepEpochBoundaries() throws Exception {
        var calls=new ArrayList<String>();
        for(String type:List.of("OculusForge","render/world/WorldRenderHooks","render/world/WorldRenderPipeline")){
            try(var input=Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("dev/oculusforge/"+type+".class"))){
                new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9){
                    @Override public MethodVisitor visitMethod(int a,String method,String d,String s,String[] e){
                        return new MethodVisitor(Opcodes.ASM9){
                            @Override public void visitMethodInsn(int o,String owner,String name,String desc,boolean itf){
                                calls.add(type+"."+method+" "+owner+"."+name);
                            }
                            @Override public void visitInvokeDynamicInsn(String n,String d,Handle bootstrap,Object... args){
                                for(Object arg:args)if(arg instanceof Handle h)calls.add(type+"."+method+" "+h.getOwner()+"."+h.getName());
                            }
                        };
                    }
                },ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
            }
        }
        assertTrue(calls.stream().anyMatch(c->c.startsWith("OculusForge.")&&c.endsWith("WorldRenderHooks.resetWorld")));
        assertTrue(calls.stream().anyMatch(c->c.startsWith("render/world/WorldRenderHooks.resetWorld ")&&c.endsWith("PackShaderPipeline.resetTemporalHistory")));
        assertTrue(calls.stream().anyMatch(c->c.startsWith("render/world/WorldRenderPipeline.ensureTargets ")&&c.endsWith("WorldRenderPipeline.resetTargets")));
        assertTrue(calls.stream().anyMatch(c->c.startsWith("render/world/WorldRenderPipeline.ensureTargets ")&&c.endsWith("WorldRenderTargets.create")));
    }

    private void compile(String source,int kind){
        for(boolean zeroToOne:List.of(false,true)){
            long compiler=Shaderc.shaderc_compiler_initialize(),options=0,result=0;assertNotEquals(0,compiler);
            try{
                options=Shaderc.shaderc_compile_options_initialize();assertNotEquals(0,options);
                Shaderc.shaderc_compile_options_set_target_env(options,Shaderc.shaderc_target_env_vulkan,Shaderc.shaderc_env_version_vulkan_1_2);
                Shaderc.shaderc_compile_options_set_auto_bind_uniforms(options,true);
                Shaderc.shaderc_compile_options_set_preserve_bindings(options,false);
                Shaderc.shaderc_compile_options_set_generate_debug_info(options);
                Shaderc.shaderc_compile_options_set_optimization_level(options,Shaderc.shaderc_optimization_level_zero);
                Shaderc.shaderc_compile_options_set_auto_map_locations(options,false);
                if(zeroToOne)for(String macro:List.of("RENDERPEARL_DEPTH_IS_ZERO_TO_ONE","OCULUS_FRAGMENT_ZERO_TO_ONE","OCULUS_SHADOW_ZERO_TO_ONE"))
                    Shaderc.shaderc_compile_options_add_macro_definition(options,macro,"");
                result=Shaderc.shaderc_compile_into_spv(compiler,source,kind,"wetness-lifecycle.glsl","main",options);
                assertNotEquals(0,result);
                assertEquals(Shaderc.shaderc_compilation_status_success,Shaderc.shaderc_result_get_compilation_status(result),
                    Shaderc.shaderc_result_get_error_message(result));
            }finally{
                if(result!=0)Shaderc.shaderc_result_release(result);
                if(options!=0)Shaderc.shaderc_compile_options_release(options);
                Shaderc.shaderc_compiler_release(compiler);
            }
        }
    }
}
