package dev.oculusforge.render.program;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import java.util.List;
import org.junit.jupiter.api.Test;

final class CustomUniformPropertiesTest {
    private static final Map<String,String> MODERN=Map.of("MC_VERSION","260200");
    private static String adapted(String properties) throws Exception {
        return NativeCustomUniforms.parse(properties,MODERN).adapt("uniform float result; float x=result;");
    }
    @Test void knownExclusiveOsBranchesSkipOnlyDefinitelyInactiveMacroDirectives() throws Exception {
        String source="#ifdef MC_OS_MAC\n#define COLORED_LIGHTING 0\n#endif\nuniform.float.result=3";
        for(String os:List.of("MC_OS_WINDOWS","MC_OS_LINUX")) {
            var custom=NativeCustomUniforms.parse(source,Map.of("MC_VERSION","260200",os,""));
            assertTrue(custom.adapt("uniform float result;float x=result;").contains("float x=(3.0)"));
        }
        for(Map<String,String> environment:List.of(MODERN,Map.of("MC_OS_MAC",""),
            Map.of("MC_OS_MAC","","MC_OS_WINDOWS","")))
            assertTrue(NativeCustomUniforms.parse(source,environment)
                .adapt("uniform float result;float x=result;").contains("uniform float result;"));
    }
    @Test void missingOsMacrosRemainAbsentInDefinedExpressionsRatherThanBeingDefinedAsZero() {
        var env=Map.of("MC_OS_WINDOWS","","MC_VERSION","260200");
        String source="#if defined(MC_OS_MAC)\nuniform.float.result=1\n#elif defined MC_OS_WINDOWS\n"
            +"uniform.float.result=2\n#else\nuniform.float.result=3\n#endif";
        assertTrue(NativeCustomUniforms.parse(source,env).declaresUniform("result"));
        assertEquals("uniform.float.result=2\n",CustomUniformProperties.select(source,env));
        assertEquals("uniform.float.result=3\n",CustomUniformProperties.select(
            "#ifndef MC_OS_MAC\nuniform.float.result=3\n#endif",env));
    }
    @Test void unknownOptionsAndActiveDirectivesStillCannotBeIgnored() throws Exception {
        for(String guard:List.of("#ifdef UNKNOWN","#ifndef UNKNOWN","#if UNKNOWN","#if 1")) {
            String source=guard+"\n#define OPTION 0\n#endif\nuniform.float.result=3";
            assertTrue(NativeCustomUniforms.parse(source,Map.of("MC_OS_WINDOWS",""))
                .adapt("uniform float result;float x=result;").contains("uniform float result;"));
        }
        assertTrue(adapted("#if 0\n#include never-read\n#error never-executed\n#endif\nuniform.float.result=3")
            .contains("float x=(3.0)"));
    }
    @Test void complementaryStyleHeaderPreservesActualNetherSmoothingDefinitions() throws Exception {
        String properties="#ifdef MC_OS_MAC\n#define COLORED_LIGHTING 0\n#endif\n"
            +"uniform.float.inBasaltDeltas=smooth(53,if(in(biome,BIOME_BASALT_DELTAS),1,0),15,15)";
        var custom=NativeCustomUniforms.parse(properties,Map.of("MC_OS_WINDOWS",""));
        var binding=CustomUniformBindings.prepare(custom,List.of("uniform float inBasaltDeltas;float value=inBasaltDeltas;"));
        assertEquals(Map.of("inBasaltDeltas",0),binding.slots());
        var state=binding.newState();var world=new Object();
        state.update(world,AnimationClock.ZERO,0,0,0,0,0);
        var value=state.update(world,new AnimationClock.Snapshot(1,1.5f,1.5f),0,0,0,0,
            dev.oculusforge.render.world.NativeBiomeSymbols.fromKey("minecraft:basalt_deltas"));
        assertEquals(.5f,value.boundSlot(0),.000001f);
    }
    @Test void versionBranchesSelectOnlyActiveDefinition() throws Exception {
        String source="#if MC_VERSION >= 11800\nuniform.float.result=2\n#else\nuniform.float.result=1\n#endif";
        assertTrue(adapted(source).contains("float x=(2.0)"));
        assertTrue(NativeCustomUniforms.parse(source,Map.of("MC_VERSION","11700"))
            .adapt("uniform float result; float x=result;").contains("float x=(1.0)"));
    }
    @Test void elifNestingAndDefinedEnvironmentWork() throws Exception {
        assertTrue(adapted("#if MC_VERSION<100\nuniform.float.result=1\n#elif defined(MC_VERSION)\n"
            +"#ifdef MC_VERSION\nuniform.float.result=2\n#endif\n#else\nuniform.float.result=3\n#endif").contains("float x=(2.0)"));
    }
    @Test void unknownOptionsAreNotAssumedFalseEvenInElse() throws Exception {
        for(String directive:List.of("#if UNKNOWN", "#ifdef UNKNOWN", "#ifndef UNKNOWN"))
            assertTrue(adapted(directive+"\nuniform.float.result=1\n#else\nuniform.float.result=2\n#endif")
                .contains("uniform float result;"));
        assertTrue(adapted("#if UNKNOWN\nuniform.float.other=1\n#endif\nuniform.float.result=3")
            .contains("float x=(3.0)"));
        assertTrue(adapted("#if UNKNOWN\nuniform.float.result=1\n#endif\nuniform.float.result=3")
            .contains("uniform float result;"));
    }
    @Test void definitelyInactiveParentsDiscardUnknownNestedDefinitions() throws Exception {
        assertTrue(adapted("#if MC_VERSION<100\n#ifdef UNKNOWN\nuniform.float.result=1\n#endif\n"
            +"#else\nuniform.float.result=3\n#endif").contains("float x=(3.0)"));
    }
    @Test void unknownEarlierElifDoesNotAccidentallySelectLaterBranch() throws Exception {
        assertTrue(adapted("#if UNKNOWN\nuniform.float.result=1\n#elif MC_VERSION>100\nuniform.float.result=2\n"
            +"#else\nuniform.float.result=3\n#endif").contains("uniform float result;"));
        assertTrue(adapted("#if MC_VERSION>100\nuniform.float.result=1\n#elif UNKNOWN\nuniform.float.result=2\n#endif")
            .contains("float x=(1.0)"));
    }
    @Test void continuationAndSelectedHeightGateWorkTogether() throws Exception {
        String selected=adapted("#if MC_VERSION>=11800\nvariable.float.gate=if(cameraPosition.y>=113,1,0)\n"
            +"uniform.float.result=gate + \\\n 2\n#else\nuniform.float.result=0\n#endif");
        assertTrue(selected.contains("cameraPosition.y >= 113.0"));
        assertTrue(selected.contains("+ 2.0"));
        assertFalse(selected.contains("uniform float result;"));
    }
    @Test void activeDuplicateEscapedAliasesStillFailClosed() throws Exception {
        for(String duplicate:List.of("uniform.float.result=2", "uniform.float.\\u0072esult=2"))
            assertTrue(adapted("uniform.float.result=1\n#if MC_VERSION>100\n"+duplicate+"\n#endif")
                .contains("uniform float result;"));
    }
    @Test void commentsCannotConsumeFollowingDefinitions() throws Exception {
        assertTrue(adapted("# Comment \\\n! Other \\\nuniform.float.result=3").contains("float x=(3.0)"));
    }
    @Test void malformedOrUnboundedPropertiesDisableCustomDefinitions() throws Exception {
        for(String bad:List.of("#endif", "#if MC_VERSION>1", "#else", "#include secret", "#define MC_VERSION 1",
            "#if 1\n#else\n#else\n#endif", "#if 1\n#else junk\n#endif", "#if 1\n#endif junk",
            "#if 1\n".repeat(33)+"#endif\n".repeat(33), "a".repeat(1024*1024+1)))
            assertTrue(adapted("uniform.float.result=3\n"+bad).contains("uniform float result;"));
    }
}
