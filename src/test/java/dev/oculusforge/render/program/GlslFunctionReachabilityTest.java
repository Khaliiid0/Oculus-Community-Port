package dev.oculusforge.render.program;

import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GlslFunctionReachabilityTest {
    @Test void transitiveCallsAndReachableCyclesSurviveWhileDeadCyclesDisappear() {
        String source = "#version 330 core\nfloat used(){return leaf();}\nfloat leaf(){return used();}\n"
            + "float dead(){return alsoDead();}\nfloat alsoDead(){return dead();}\nvoid main(){used();}";
        String result = GlslFunctionReachability.prune(source);
        assertTrue(result.startsWith("#version 330 core"));
        assertTrue(result.contains("float used()"));
        assertTrue(result.contains("float leaf()"));
        assertFalse(result.contains("float dead()"));
        assertFalse(result.contains("float alsoDead()"));
        assertEquals(source.length(), result.length());
        assertEquals(source.lines().count(), result.lines().count());
    }

    @Test void overloadsPrototypesAndGlobalInitializersAreConservativeRoots() {
        String source = "float retained();\nfloat retained(){return 1.;}\nfloat init(){return 2.;}\n"
            + "float value=init();\nfloat overloaded(float x){return x;}\nvec3 overloaded(vec3 x){return x;}\n"
            + "void main(){overloaded(1.);}";
        assertEquals(source, GlslFunctionReachability.prune(source));
    }

    @Test void commentsStructsNestedBlocksAndLineDirectivesKeepTheirMeaning() {
        String source = "#version 330 core\nstruct Item{vec3 field;};\n#line 10 2\n"
            + "// fake(){broken;}\nfloat dead(){if(true){return 0.;}return 1.;}\n"
            + "void main(){if(true){Item item;item.field=vec3(1.);}}";
        String result = GlslFunctionReachability.prune(source);
        assertTrue(result.contains("struct Item{vec3 field;};"));
        assertTrue(result.contains("#line 10 2"));
        assertTrue(result.contains("// fake(){broken;}"));
        assertFalse(result.contains("float dead()"));
        assertTrue(result.contains("void main(){if(true)"));
    }

    @Test void unprocessedMacrosSubroutinesMissingMainAndMalformedBodiesAreNotPruned() {
        for (String source : new String[]{"#define CALL dead\nfloat dead(){return 1.;}void main(){CALL();}",
            "#if 1\nvoid main(){}\n#endif\nfloat dead(){return 1.;}",
            "subroutine float Type();float dead(){return 1.;}void main(){}",
            "float dead(){return 1.;}", "void main(){}float broken(){"})
            assertEquals(source, GlslFunctionReachability.prune(source));
    }

    @Test void fragmentBindingListDropsOnlySamplersFromUnreachableFunctions() throws Exception {
        var shader = new LegacyFinalShaderAdapter().adapt("""
            #version 120
            uniform sampler2D colortex0;
            uniform sampler2D colortex7;
            vec4 unused(){return texture2D(colortex7,vec2(.5));}
            vec4 used(){return texture2D(colortex0,vec2(.5));}
            void main(){gl_FragColor=used();}
            """);
        assertEquals(1, shader.samplers().size());
        assertEquals(0, shader.samplers().getFirst().colorIndex());
        assertFalse(shader.source().contains("uniform sampler2D colortex7;"));
        assertTrue(shader.source().contains("vec4 used()"));
    }

    @Test void vertexWrapperRetainsSamplerHelpersCalledByPackMain() throws Exception {
        var shader = new LegacyFinalVertexAdapter().adapt("""
            #version 120
            uniform sampler2D colortex1;
            uniform sampler2D colortex2;
            vec4 unused(){return texture2D(colortex2,vec2(.5));}
            vec4 used(){return texture2D(colortex1,vec2(.5));}
            void main(){gl_Position=ftransform()+used();}
            """);
        assertEquals(1, shader.samplers().size());
        assertEquals(1, shader.samplers().getFirst().colorIndex());
        assertTrue(shader.source().contains("oculus_pack_main();"));
        assertTrue(shader.source().contains("vec4 used()"));
        assertFalse(shader.source().contains("vec4 unused()"));
    }

    @Test void activeDepthSnapshotsRemainIndependent() throws Exception {
        var shader = new LegacyFinalShaderAdapter().adapt("""
            uniform sampler2D depthtex0;
            uniform sampler2D depthtex1;
            uniform sampler2D colortex7;
            vec4 unused(){return texture2D(colortex7,vec2(.5));}
            void main(){gl_FragColor=texture2D(depthtex0,vec2(.5))+texture2D(depthtex1,vec2(.5));}
            """);
        assertEquals(2, shader.samplers().size());
        assertEquals(Set.of(0,1), shader.samplers().stream().map(s->s.colorIndex()).collect(java.util.stream.Collectors.toSet()));
    }
}
