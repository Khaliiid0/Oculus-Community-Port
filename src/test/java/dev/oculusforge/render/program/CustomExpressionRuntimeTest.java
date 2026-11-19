package dev.oculusforge.render.program;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import dev.oculusforge.render.world.NativeBiomeSymbols;
import org.junit.jupiter.api.Test;

final class CustomExpressionRuntimeTest {
    @Test void propertySelectionVariablesAndSmoothingCompileThroughSharedDefinitions() {
        var custom=NativeCustomUniforms.parse("#if MC_VERSION>=11800\n"
            +"variable.float.gate=if(in(biome,BIOME_DESERT),if(cameraPosition.y>=113,1,0),0)\n"
            +"uniform.float.result=smooth(2,gate,10,20)\n#else\nuniform.float.result=unsupported\n#endif",
            Map.of("MC_VERSION","260200"));
        var runtime=custom.prepareCpu(Set.of("result")).newRuntime();var world=new Object();
        runtime.update(world,new Object(),sample(0,"minecraft:plains"),0);
        assertEquals(.5f,runtime.update(world,new Object(),sample(113,"minecraft:desert"),1).value("result"));
        assertThrows(IllegalArgumentException.class,()->custom.prepareCpu(Set.of("gate")));
    }
    @Test void blockedPropertyAliasesAndBuiltinOverridesCannotReappearOnCpu() {
        for(String source:List.of("uniform.float.worldTime=2\nuniform.float.result=worldTime",
            "uniform.float.cameraPosition=2\nuniform.float.result=cameraPosition.y",
            "uniform.float.result=1\nuniform.float.\\u0072esult=2",
            "#ifdef UNKNOWN\nuniform.float.result=1\n#else\nuniform.float.result=2\n#endif",
            "uniform.float.BIOME_DESERT=2\nuniform.float.result=if(in(biome,BIOME_DESERT),1,0)")) {
            var custom=NativeCustomUniforms.parse(source,Map.of("MC_VERSION","260200"));
            assertThrows(IllegalArgumentException.class,()->custom.prepareCpu(Set.of("result")),source);
        }
    }
    private static CustomExpressionRuntime.Sample sample(float y,String biome) {
        return new CustomExpressionRuntime.Sample(6000,12,10,y,-20,NativeBiomeSymbols.fromKey(biome));
    }
    private static CustomExpressionRuntime runtime(String expression) {
        return CustomExpressionRuntime.compile(Map.of("result",expression),Set.of("result")).newRuntime();
    }
    @Test void biomeAndHeightExpressionsDriveAsymmetricSmoothing() {
        var runtime=runtime("smooth(2,if(in(biome,BIOME_DESERT),if(cameraPosition.y>=113,1,0),0),10,20)");
        var world=new Object();
        assertEquals(0,runtime.update(world,new Object(),sample(112,"minecraft:desert"),0).value("result"));
        assertEquals(.5f,runtime.update(world,new Object(),sample(113,"minecraft:desert"),1).value("result"),1e-6);
        assertEquals(.25f,runtime.update(world,new Object(),sample(113,"minecraft:plains"),2).value("result"),1e-6);
    }
    @Test void namedReferencesShareHistoryButDistinctCallsDoNot() {
        var program=CustomExpressionRuntime.compile(Map.of("a","smooth(1,cameraPosition.y,10,10)",
            "b","smooth(1,cameraPosition.y,10,10)+a"),Set.of("a","b"));
        assertEquals(2,program.channels().size());
        var runtime=program.newRuntime(); var world=new Object();
        runtime.update(world,new Object(),sample(0,"minecraft:plains"),0);
        var frame=new Object(); var result=runtime.update(world,frame,sample(1,"minecraft:plains"),1);
        assertEquals(.5f,result.value("a")); assertEquals(1f,result.value("b"));
        assertSame(result,runtime.update(world,frame,sample(100,"minecraft:desert"),2));
        assertThrows(UnsupportedOperationException.class,()->result.values().put("a",4f));
    }
    @Test void reusedCompatibilityIdsAllowIndependentInputsAndFades() {
        var source=Map.of("a","smooth(11,cameraPosition.y,10,10)","b","smooth(11,cameraPosition.x,10,10)");
        var program=CustomExpressionRuntime.compile(source,Set.of("a","b"));
        assertEquals(2,program.channels().size());
        assertEquals(1,CustomExpressionRuntime.compile(source,Set.of("a")).channels().size());
        var runtime=program.newRuntime();var world=new Object();
        runtime.update(world,new Object(),new CustomExpressionRuntime.Sample(0,0,0,0,0,0),0);
        var result=runtime.update(world,new Object(),new CustomExpressionRuntime.Sample(0,1,4,1,0,0),1);
        assertEquals(.5f,result.value("a"));assertEquals(2f,result.value("b"));
        var asymmetric=CustomExpressionRuntime.compile(Map.of(
            "a","smooth(1,cameraPosition.y,10,10)","b","smooth(1,cameraPosition.y,10,20)"),Set.of("a","b")).newRuntime();
        asymmetric.update(world,new Object(),sample(1,"minecraft:plains"),0);
        var falling=asymmetric.update(world,new Object(),sample(0,"minecraft:plains"),1);
        assertEquals(.5f,falling.value("a"));
        assertEquals((float)Math.pow(2,-.5),falling.value("b"),1e-6);
    }
    @Test void nestedSmoothingEvaluatesDependenciesBeforeConsumers() {
        var runtime=runtime("smooth(2,smooth(1,cameraPosition.y,10,10),10,10)"); var world=new Object();
        runtime.update(world,new Object(),sample(0,"minecraft:plains"),0);
        assertEquals(.25f,runtime.update(world,new Object(),sample(1,"minecraft:plains"),1).value("result"));
    }
    @Test void irisFadeScaleMatchesIndependentExponentialReference() {
        var runtime=runtime("smooth(11,cameraPosition.y,10,20)");var world=new Object();
        runtime.update(world,new Object(),sample(0,"minecraft:plains"),0);
        assertEquals((float)(1-Math.pow(2,-.5)),
            runtime.update(world,new Object(),sample(1,"minecraft:plains"),.5).value("result"),1e-6);
        assertEquals(.5f,runtime.update(world,new Object(),sample(1,"minecraft:plains"),.5).value("result"),1e-6);
        assertEquals(.25f,runtime.update(world,new Object(),sample(0,"minecraft:plains"),2).value("result"),1e-6);
    }
    @Test void identicalCallsAreDistinctButAliasesReuseTheResolvedCall() {
        var program=CustomExpressionRuntime.compile(Map.of("a","smooth(1,eyeAltitude,10,10)",
            "b","a+a","c","smooth(1,eyeAltitude,10,10)+smooth(1,eyeAltitude,10,10)"),Set.of("a","b","c"));
        assertEquals(3,program.channels().size());
    }
    @Test void reusedIdsCannotBypassChannelBudget() {
        var definitions=new HashMap<String,String>();var roots=new HashSet<String>();
        for(int i=0;i<64;i++) {definitions.put("v"+i,"smooth(11,eyeAltitude,10,10)");roots.add("v"+i);}
        assertEquals(64,CustomExpressionRuntime.compile(definitions,roots).channels().size());
        definitions.put("v0","smooth(11,smooth(11,eyeAltitude,10,10),10,10)");
        var error=assertThrows(IllegalArgumentException.class,()->CustomExpressionRuntime.compile(definitions,roots));
        assertTrue(error.getMessage().contains("channel limit"));
    }
    @Test void finiteMaximumFadeDoesNotOverflowDuringUnitConversion() {
        var runtime=runtime("smooth(1,eyeAltitude,3.4028235e38,3.4028235e38)");var world=new Object();
        runtime.update(world,new Object(),sample(0,"minecraft:plains"),0);
        float value=runtime.update(world,new Object(),sample(1,"minecraft:plains"),1).value("result");
        assertTrue(Float.isFinite(value));assertTrue(value>0 && value<1e-30f);
    }
    @Test void worldsResetAndVariantsOwnIndependentHistory() {
        var program=CustomExpressionRuntime.compile(Map.of("a","smooth(1,cameraPosition.y,10,10)"),Set.of("a"));
        var a=program.newRuntime(); var b=program.newRuntime(); var world=new Object();var frame=new Object();
        a.update(world,frame,sample(0,"minecraft:plains"),0);
        assertEquals(20,b.update(world,frame,sample(20,"minecraft:plains"),0).value("a"));
        assertEquals(10,a.update(new Object(),frame,sample(10,"minecraft:plains"),0).value("a"));
        a.reset(); assertEquals(30,a.update(world,frame,sample(30,"minecraft:plains"),0).value("a"));
    }
    @Test void overflowRollsBackAllHistoryAndFrameIdentity() {
        var program=CustomExpressionRuntime.compile(Map.of("a","smooth(1,cameraPosition.y,10,10)",
            "z","cameraPosition.y*3e38"),Set.of("a","z"));
        var runtime=program.newRuntime(); var world=new Object();
        runtime.update(world,new Object(),sample(0,"minecraft:plains"),0);
        var frame=new Object();
        assertThrows(IllegalArgumentException.class,()->runtime.update(world,frame,sample(2,"minecraft:plains"),.5));
        assertEquals(.5f,runtime.update(world,frame,sample(1,"minecraft:plains"),1).value("a"));
    }
    @Test void unusedBranchChannelsStillAdvanceOncePerFrame() {
        var runtime=runtime("if(cameraPosition.x>0,0,smooth(1,cameraPosition.y,10,10))");var world=new Object();
        runtime.update(world,new Object(),sample(0,"minecraft:plains"),0);
        runtime.update(world,new Object(),sample(1,"minecraft:plains"),1);
        var changed=new CustomExpressionRuntime.Sample(6000,12,-1,1,0,0);
        assertEquals(.75f,runtime.update(world,new Object(),changed,1).value("result"));
    }
    @Test void cpuArithmeticAndMembershipMatchReference() {
        var runtime=runtime("max(sin(worldTime/24000*6.2831853),0)+abs(cameraPosition.z)/20+min(eyeAltitude,2)");
        assertEquals(4f,runtime.update(new Object(),new Object(),sample(3,"minecraft:plains"),0).value("result"),1e-6);
        var member=runtime("if(in(biome,BIOME_DESERT,BIOME_BADLANDS),1,0)");
        for(String key:List.of("minecraft:desert","minecraft:badlands","mod:desert","minecraft:plains"))
            assertEquals(key.equals("minecraft:desert")||key.equals("minecraft:badlands")?1:0,
                member.update(new Object(),new Object(),sample(0,key),0).value("result"));
    }
    @Test void unsupportedSignaturesCyclesAndLimitsFailClosed() {
        for(String expression:List.of("smooth(1,2)","smooth(1,2,-1,1)","smooth(.5,2,1,1)",
            "smooth(65536,2,1,1)","smooth(frameCounter,2,1,1)","smooth(1,2,eyeAltitude,1)",
            "smooth(1,2,1e999,1)","missing"))
            assertThrows(RuntimeException.class,()->runtime(expression),expression);
        assertThrows(IllegalArgumentException.class,()->CustomExpressionRuntime.compile(Map.of("a","b","b","a"),Set.of("a")));
        var definitions=new HashMap<String,String>();var roots=new HashSet<String>();
        for(int i=0;i<65;i++){definitions.put("v"+i,"1");roots.add("v"+i);}
        assertThrows(IllegalArgumentException.class,()->CustomExpressionRuntime.compile(definitions,roots));
    }
    @Test void invalidFrameDoesNotAdvanceCachedHistory() {
        var runtime=runtime("smooth(1,eyeAltitude,10,10)");var world=new Object();var frame=new Object();
        runtime.update(world,frame,sample(0,"minecraft:plains"),0);
        assertThrows(IllegalArgumentException.class,()->runtime.update(world,frame,sample(1,"minecraft:plains"),Double.NaN));
        assertEquals(.5f,runtime.update(world,new Object(),sample(1,"minecraft:plains"),1).value("result"));
        assertThrows(IllegalArgumentException.class,()->sample(Float.NaN,"minecraft:plains"));
    }
    @Test void frameBudgetBoundsRepeatedDependencyEvaluation() {
        var definitions=new HashMap<String,String>();var roots=new HashSet<String>();
        definitions.put("base",String.join("+",Collections.nCopies(31,"cameraPosition.y")));
        for(int i=0;i<64;i++) { definitions.put("v"+i,"base+base+base+base"); roots.add("v"+i); }
        var runtime=CustomExpressionRuntime.compile(definitions,roots).newRuntime();
        var error=assertThrows(IllegalArgumentException.class,()->runtime.update(new Object(),new Object(),sample(1,"minecraft:plains"),0));
        assertTrue(error.getMessage().contains("budget"));
    }
    @Test void zeroHalfLifeAndFractionalHistoryMatchAcrossFrameRates() {
        for(int steps:new int[]{1,10,60,144,1000}) {
            var runtime=runtime("smooth(1,cameraPosition.y,10,0)");var world=new Object();
            runtime.update(world,new Object(),sample(0,"minecraft:plains"),0);
            float value=0;
            for(int i=0;i<steps;i++) value=runtime.update(world,new Object(),sample(1,"minecraft:plains"),1.0/steps).value("result");
            assertEquals(.5f,value,1e-6);
            assertEquals(0,runtime.update(world,new Object(),sample(0,"minecraft:plains"),0).value("result"));
        }
    }
}
