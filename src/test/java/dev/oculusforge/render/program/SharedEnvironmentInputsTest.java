package dev.oculusforge.render.program;

import static org.junit.jupiter.api.Assertions.*;
import dev.oculusforge.render.world.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class SharedEnvironmentInputsTest {
    private static final String ALIAS="uniform.float.maxBlindnessDarkness=max(blindness,darknessFactor)";
    private static final String V="void main(){gl_Position=ftransform();}";
    private static final String F="uniform float maxBlindnessDarkness;\nvoid main(){gl_FragColor=vec4(maxBlindnessDarkness);}";
    private static CustomUniformBindings binding(String properties,String declarations) throws Exception {
        return CustomUniformBindings.prepare(NativeCustomUniforms.parse(properties),List.of(declarations));
    }
    @Test void gpuFixtureSharesFiveCustomSlotsAndKeepsTheNativeEffectIdentityIndependent() throws Exception {
        var root=java.nio.file.Path.of(getClass().getClassLoader().getResource("shared-environment-inputs").toURI());
        var compiler=new PackProgramCompiler(dev.oculusforge.shaderpack.ShaderPackLimits.DEFAULT);
        var prepared=compiler.prepare(new dev.oculusforge.shaderpack.ShaderPackDescriptor("shared","Shared",root,
            dev.oculusforge.shaderpack.ShaderPackDescriptor.Kind.DIRECTORY));
        var bindings=prepared.variants().get("shaders").customUniforms();
        assertEquals(Set.of("rainProbe","skyProbe","frameProbe","effectProbe","snowProbe"),bindings.slots().keySet());
        var world=prepared.world().variant("shaders");
        assertNotNull(world.nativeTerrain(),world.nativeTerrainIssue());assertNotNull(world.nativeShadow(),world.nativeShadowIssue());
        assertEquals(List.of(0,4,6),world.terrainTargets());assertEquals(List.of(0,1),world.shadowTargets());
        var uniforms=prepared.programs().getFirst().fragment().uniforms();
        assertEquals(5,uniforms.stream().filter(u->u.semantic()==LegacyFinalShaderAdapter.UniformSemantic.CUSTOM_FLOAT).count());
        assertTrue(uniforms.stream().anyMatch(u->u.semantic()==LegacyFinalShaderAdapter.UniformSemantic.MAX_BLINDNESS_DARKNESS));
        assertEquals(0,compiler.cachedPipelineCount());
    }

    @Test void exactEquivalentAliasesKeepNativeAndPostBindingsAndConsumeNoCustomSlots() throws Exception {
        for(String expression:List.of("max(blindness,darknessFactor)","max ( darknessFactor , blindness )")) {
            var custom=NativeCustomUniforms.parse("uniform.float.maxBlindnessDarkness="+expression);
            assertFalse(custom.declaresUniform("maxBlindnessDarkness"));
            var bindings=CustomUniformBindings.prepare(custom,List.of(F));
            assertTrue(bindings.slots().isEmpty());
            assertEquals(F,bindings.adapt(F));
            for(boolean shadow:List.of(false,true))for(boolean extended:List.of(false,true)) {
                var program=NativeTerrainProgram.adaptWithCustomUniforms(V,F,List.of(0),shadow,extended,custom.withBindings(bindings));
                assertTrue(program.fragmentSource().contains("max(oculusNative_blindness, oculusNative_darknessFactor)"));
            }
            var post=new LegacyFinalShaderAdapter().adapt(bindings.adapt(F));
            assertEquals(LegacyFinalShaderAdapter.UniformSemantic.MAX_BLINDNESS_DARKNESS,post.uniforms().getFirst().semantic());
        }
    }

    @Test void otherExpressionsTypesVariablesUnknownBranchesAndEscapedAliasesRemainBlocked() {
        for(String definition:List.of("uniform.float.maxBlindnessDarkness=1",
            "uniform.float.maxBlindnessDarkness=max(blindness,darknessFactor)+0",
            "uniform.float.maxBlindnessDarkness=min(blindness,darknessFactor)",
            "uniform.int.maxBlindnessDarkness=max(blindness,darknessFactor)",
            "variable.float.maxBlindnessDarkness=max(blindness,darknessFactor)",
            "#ifdef UNKNOWN\n"+ALIAS+"\n#endif",ALIAS+"\n"+ALIAS,
            ALIAS+"\nuniform.float.maxBlindness\\u0044arkness=0",
            "uniform.float.maxBlindness\\u0044arkness=max(blindness,darknessFactor)",
            "uniform.float.maxBlindnessDarkness:max(blindness,darknessFactor)",
            "prefix=some\\\n"+ALIAS))
            assertThrows(ShaderCompatibilityException.class,()->binding(definition,F),definition);
    }

    @Test void invalidShaderDeclarationsCannotHideBehindTheEquivalentAlias() throws Exception {
        var custom=NativeCustomUniforms.parse(ALIAS);
        var bindings=CustomUniformBindings.prepare(custom,List.of(F));
        for(String declaration:List.of("uniform int maxBlindnessDarkness;","uniform float maxBlindnessDarkness[2];",
            "uniform float maxBlindnessDarkness=0.;","uniform float maxBlindnessDarkness;\nuniform float maxBlindnessDarkness;"))
            assertThrows(ShaderCompatibilityException.class,()->NativeTerrainProgram.adaptWithCustomUniforms(V,
                declaration+"\nvoid main(){gl_FragColor=vec4(maxBlindnessDarkness);}",List.of(0),false,false,custom.withBindings(bindings)));
    }

    @Test void aliasesAndDependentExpressionsCannotIgnoreAttemptedEffectOverrides() {
        for(String override:List.of("uniform.float.blindness=1","uniform.float.darknessFactor=0",
            "uniform.float.blind\\u006eess=1")) {
            assertThrows(ShaderCompatibilityException.class,()->binding(ALIAS+"\n"+override,F));
            assertThrows(ShaderCompatibilityException.class,()->binding(ALIAS+"\n"+override
                +"\nuniform.float.probe=maxBlindnessDarkness","uniform float probe;float x=probe;"));
        }
    }

    @Test void aliasDependenciesEvaluateWithRealFiniteEffectSamples() throws Exception {
        var bindings=binding(ALIAS+"\nuniform.float.probe=maxBlindnessDarkness*.5",
            "uniform float maxBlindnessDarkness;\nuniform float probe;float value=probe+maxBlindnessDarkness;");
        assertEquals(Map.of("probe",0),bindings.slots());
        var state=bindings.newState();var world=new Object();
        assertEquals(.375f,state.update(world,AnimationClock.ZERO,0,0,0,0,0,.25f,.75f).boundSlot(0));
        assertEquals(.45f,state.update(world,new AnimationClock.Snapshot(1,.1f,.1f),0,0,0,0,0,.9f,.2f).boundSlot(0));
    }

    @Test void realRainUsesTheConfiguredSmoothingHalfLifeAndSharedFrameIdentity() throws Exception {
        var binding=binding("uniform.float.rainFactor=smooth(1,rainStrength,3,3)",
            "uniform float rainFactor;float value=rainFactor;");
        var state=binding.newState();var world=new Object();
        state.update(world,AnimationClock.ZERO,0,0,0,0,0,0,0,0,EyeBrightness.DARK);
        var frame=new AnimationClock.Snapshot(1,.3f,.3f);
        var nativeValue=state.update(world,frame,0,0,0,0,0,0,0,1,EyeBrightness.DARK);
        assertEquals(.5f,nativeValue.boundSlot(0),.000001f);
        assertSame(nativeValue,state.update(world,frame,0,0,0,0,0,0,0,0,EyeBrightness.DARK));
        var next=state.update(world,new AnimationClock.Snapshot(2,.3f,.6f),0,0,0,0,0,0,0,0,EyeBrightness.DARK);
        assertEquals(.25f,next.boundSlot(0),.000001f);
        assertEquals(.5f,nativeValue.boundSlot(0),.000001f);
    }

    @Test void eyeComponentsUseRawLightmapUnitsNotSmoothedOrNormalizedStandIns() throws Exception {
        var binding=binding("uniform.float.block=eyeBrightness.x\nuniform.float.sky=eyeBrightness.y",
            "uniform float block,sky;float value=block+sky;");
        var values=binding.newState().update(new Object(),AnimationClock.ZERO,0,0,0,0,0,0,0,.2f,new EyeBrightness(7,15));
        assertEquals(112,values.boundSlot(binding.slots().get("block")));
        assertEquals(240,values.boundSlot(binding.slots().get("sky")));
    }

    @Test void actualEyeAndFrameTimeSmoothingDefinitionsRetainIndependentHistories() throws Exception {
        var binding=binding("uniform.float.eyeBrightnessM=smooth(4,eyeBrightness.y/240.0,5,5)\n"
            +"uniform.float.frameTimeSmooth=smooth(5,frameTime,5,5)",
            "uniform float eyeBrightnessM,frameTimeSmooth;float value=eyeBrightnessM+frameTimeSmooth;");
        var state=binding.newState();var world=new Object();
        state.update(world,AnimationClock.ZERO,0,0,0,0,0,0,0,0,EyeBrightness.DARK);
        var values=state.update(world,new AnimationClock.Snapshot(1,.5f,.5f),0,0,0,0,0,0,0,0,new EyeBrightness(0,15));
        assertEquals(.5f,values.boundSlot(binding.slots().get("eyeBrightnessM")),.000001f);
        assertEquals(.25f,values.boundSlot(binding.slots().get("frameTimeSmooth")),.000001f);
    }

    @Test void invalidRainOrEyeInputDoesNotPoisonTheNextFrame() throws Exception {
        var binding=binding("uniform.float.probe=rainStrength+eyeBrightness.x/240.0",
            "uniform float probe;float value=probe;");
        var state=binding.newState();var world=new Object();var frame=new AnimationClock.Snapshot(1,.1f,.1f);
        for(float bad:new float[]{Float.NaN,Float.POSITIVE_INFINITY,-.1f,1.1f})
            assertThrows(IllegalArgumentException.class,()->state.update(world,frame,0,0,0,0,0,0,0,bad,EyeBrightness.DARK));
        assertThrows(NullPointerException.class,()->state.update(world,frame,0,0,0,0,0,0,0,0,null));
        assertEquals(.75f,state.update(world,frame,0,0,0,0,0,0,0,.75f,EyeBrightness.DARK).boundSlot(0));
    }

    @Test void worldAndExplicitResetSeedNewRainAndEyeValuesRatherThanCarryOldHistory() throws Exception {
        var binding=binding("uniform.float.probe=smooth(1,rainStrength+eyeBrightness.y/240.0,3,3)",
            "uniform float probe;float value=probe;");
        var state=binding.newState();var frame=AnimationClock.ZERO;
        assertEquals(2,state.update(new Object(),frame,0,0,0,0,0,0,0,1,new EyeBrightness(0,15)).boundSlot(0));
        var world=new Object();
        assertEquals(0,state.update(world,frame,0,0,0,0,0,0,0,0,EyeBrightness.DARK).boundSlot(0));
        state.reset();
        assertEquals(1,state.update(world,frame,0,0,0,0,0,0,0,1,EyeBrightness.DARK).boundSlot(0));
    }

    @Test void invalidVectorComponentsAndOverriddenInputNamesStillReject() {
        for(String expression:List.of("eyeBrightness.z","eyeBrightness.xy","eyeBrightness[0]","eyeBrightness.y.extra",
            "other.y","eyeBrightness","eyeBrightness.x2"))
            assertThrows(ShaderCompatibilityException.class,()->binding("uniform.float.probe="+expression,
                "uniform float probe;float value=probe;"),expression);
        for(String name:List.of("rainStrength","frameTime","eyeBrightness"))
            assertThrows(ShaderCompatibilityException.class,()->binding("uniform.float."+name+"=1\nuniform.float.probe="
                +(name.equals("eyeBrightness")?"eyeBrightness.y":name),"uniform float probe;float value=probe;"),name);
    }

    @Test void unrepresentableFrameTimeCannotCommitPartialSmoothingHistory() {
        var runtime=CustomExpressionRuntime.compile(Map.of("probe","smooth(1,frameTime,3,3)"),Set.of("probe")).newRuntime();
        var world=new Object();var sample=new CustomExpressionRuntime.Sample(0,0,0,0,0,0);
        runtime.update(world,new Object(),sample,0);
        assertThrows(IllegalArgumentException.class,()->runtime.update(world,new Object(),sample,Double.MAX_VALUE));
        assertEquals(.15f,runtime.update(world,new Object(),sample,.3).value("probe"),.000001f);
    }

    @Test void precipitationMembershipReadsTheCapturedCodeForDryRainAndSnow() throws Exception {
        var binding=binding("uniform.float.inDry=if(in(biome_precipitation,0),1,0)\n"
            +"uniform.float.inRainy=if(in(biome_precipitation,1),1,0)\n"
            +"uniform.float.inSnowy=if(in(biome_precipitation,2),1,0)",
            "uniform float inDry,inRainy,inSnowy;float v=inDry+inRainy+inSnowy;");
        for(int code=0;code<=2;code++) {
            var values=binding.newState().update(new Object(),AnimationClock.ZERO,0,0,0,0,0,0,0,0,EyeBrightness.DARK,code);
            assertEquals(code==0?1:0,values.boundSlot(binding.slots().get("inDry")));
            assertEquals(code==1?1:0,values.boundSlot(binding.slots().get("inRainy")));
            assertEquals(code==2?1:0,values.boundSlot(binding.slots().get("inSnowy")));
        }
    }

    @Test void actualPrecipitationDefinitionsRespectAsymmetricSmoothingRatherThanBiomeNameHeuristics() throws Exception {
        var binding=binding("uniform.float.inSnowy=smooth(103,if(in(biome_precipitation,2),1,0),20,10)",
            "uniform float inSnowy;float value=inSnowy;");
        var state=binding.newState();var world=new Object();
        state.update(world,AnimationClock.ZERO,0,0,0,0,0,0,0,0,EyeBrightness.DARK,1);
        assertEquals(.5f,state.update(world,new AnimationClock.Snapshot(1,2,2),0,0,0,0,0,0,0,0,EyeBrightness.DARK,2).boundSlot(0),.000001f);
        assertEquals(.25f,state.update(world,new AnimationClock.Snapshot(2,1,3),0,0,0,0,0,0,0,0,EyeBrightness.DARK,0).boundSlot(0),.000001f);
    }

    @Test void precipitationListsAreBoundedAndCannotAcceptExpressionsSymbolsOrInvalidCodes() throws Exception {
        var binding=binding("uniform.float.probe=if(in(biome_precipitation,0,2),1,0)",
            "uniform float probe;float value=probe;");
        for(int code=0;code<=2;code++)
            assertEquals(code==1?0:1,binding.newState().update(new Object(),AnimationClock.ZERO,0,0,0,0,0,0,0,0,EyeBrightness.DARK,code).boundSlot(0));
        for(String members:List.of("3","-1","1.0","01","BIOME_DESERT","0+1","","1,,2","1,"))
            assertThrows(ShaderCompatibilityException.class,()->binding("uniform.float.probe=if(in(biome_precipitation,"+members+"),1,0)",
                "uniform float probe;float value=probe;"),members);
        assertThrows(ShaderCompatibilityException.class,()->binding("uniform.float.probe=if(in(biome_precipitation,"+"0,".repeat(64)+"0),1,0)",
            "uniform float probe;float value=probe;"));
    }

    @Test void precipitationOverridesAndInvalidSamplesRemainTransactional() throws Exception {
        String definition="uniform.float.probe=if(in(biome_precipitation,2),1,0)";
        for(String override:List.of("uniform.float.biome_precipitation=2","uniform.int.biome_precipitation=2",
            "uniform.float.biome_precip\\u0069tation=2"))
            assertThrows(ShaderCompatibilityException.class,()->binding(override+"\n"+definition,
                "uniform float probe;float value=probe;"));
        var state=binding(definition,"uniform float probe;float value=probe;").newState();
        var world=new Object();var frame=AnimationClock.ZERO;
        for(int bad:new int[]{-1,3}) {
            assertThrows(IllegalArgumentException.class,()->state.update(world,frame,0,0,0,0,0,0,0,0,EyeBrightness.DARK,bad));
            assertThrows(IllegalArgumentException.class,()->new WorldEnvironment(0,0,0,EyeBrightness.DARK,0,0,0,192.33f,bad));
        }
        assertEquals(1,state.update(world,frame,0,0,0,0,0,0,0,0,EyeBrightness.DARK,2).boundSlot(0));
    }

    @Test void allNetherBiomeDefinitionsSmoothCapturedSymbolsAndDoNotShareReusedHistoryIds() throws Exception {
        var definitions=new StringBuilder();var declarations=new StringBuilder();var references=new StringBuilder("0.");
        var names=List.of("inNetherWastes","inCrimsonForest","inWarpedForest","inBasaltDeltas","inSoulValley");
        var symbols=List.of("BIOME_NETHER_WASTES","BIOME_CRIMSON_FOREST","BIOME_WARPED_FOREST","BIOME_BASALT_DELTAS","BIOME_SOUL_SAND_VALLEY");
        for(int i=0;i<names.size();i++) {
            definitions.append("uniform.float.").append(names.get(i)).append("=smooth(50,if(in(biome,").append(symbols.get(i)).append("),1,0),15,15)\n");
            declarations.append("uniform float ").append(names.get(i)).append(";\n");references.append('+').append(names.get(i));
        }
        var binding=binding(definitions.toString(),declarations+"float value="+references+";");
        var state=binding.newState();var world=new Object();
        state.update(world,AnimationClock.ZERO,0,0,0,0,0,0,0,0,EyeBrightness.DARK,0);
        for(int i=0;i<names.size();i++) {
            state.reset();state.update(world,AnimationClock.ZERO,0,0,0,0,0,0,0,0,EyeBrightness.DARK,0);
            var values=state.update(world,new AnimationClock.Snapshot(1,1.5f,1.5f),0,0,0,0,NativeBiomeSymbols.fromSymbol(symbols.get(i)),0,0,0,EyeBrightness.DARK,0);
            for(int j=0;j<names.size();j++)assertEquals(i==j?.5f:0,values.boundSlot(binding.slots().get(names.get(j))),.000001f);
        }
    }

    @Test void invalidMembershipDiagnosticsNameTheActualCustomDefinition() {
        var error=assertThrows(ShaderCompatibilityException.class,()->binding("uniform.float.inSnowy=if(in(other_precipitation,2),1,0)",
            "uniform float inSnowy;float value=inSnowy;"));
        assertTrue(error.getMessage().contains("Custom definition inSnowy"));
        assertTrue(error.getMessage().contains("Unsupported membership input"));
    }
}
