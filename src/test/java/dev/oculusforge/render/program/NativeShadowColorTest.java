package dev.oculusforge.render.program;

import static org.junit.jupiter.api.Assertions.*;
import dev.oculusforge.render.world.ShadowColorReadGuard;
import dev.oculusforge.shaderpack.*;
import java.nio.file.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class NativeShadowColorTest {
    @Test void twoColorGpuProbePreparesProducerAndBothConsumers() throws Exception {
        var root=Path.of(getClass().getClassLoader().getResource("shadow-colors").toURI());
        var p=new PackProgramCompiler(ShaderPackLimits.DEFAULT).prepare(new ShaderPackDescriptor("two","Two",root,ShaderPackDescriptor.Kind.DIRECTORY));
        var world=p.world().variant("shaders");
        assertNotNull(world.nativeTerrain(),world.nativeTerrainIssue());
        assertNotNull(world.nativeShadow(),world.nativeShadowIssue());
        assertEquals(List.of(1,0),world.shadowTargets());
        assertEquals(List.of(0,1),world.nativeTerrain().shadowColorInputs());
        assertEquals(List.of(0,1),p.variants().get("shaders").programs().getFirst().fragment().samplers().stream()
            .filter(s->s.texture()==LegacyFinalShaderAdapter.TextureSemantic.SHADOW_COLOR)
            .map(LegacyFinalShaderAdapter.SamplerBinding::colorIndex).toList());
    }
    @Test void gpuProbePreparesNativeProducerAndConsumer() throws Exception {
        var root=Path.of(getClass().getClassLoader().getResource("shadow-read").toURI());
        var p=new PackProgramCompiler(ShaderPackLimits.DEFAULT).prepare(new ShaderPackDescriptor("probe","Probe",root,ShaderPackDescriptor.Kind.DIRECTORY));
        var world=p.world().variant("shaders");
        assertNotNull(world.nativeTerrain(),world.nativeTerrainIssue());
        assertNotNull(world.nativeShadow(),world.nativeShadowIssue());
        assertTrue(world.nativeTerrain().usesShadowColor());
        assertFalse(world.nativeShadow().usesShadowColor());
    }
    @TempDir Path directory;
    private static final String V = "void main(){gl_Position=ftransform();}";
    private static final String F = "uniform sampler2D shadowcolor0;\nvoid main(){gl_FragColor=texture2D(shadowcolor0,vec2(.5));}";
    private void file(String name, String text) throws Exception {
        var p = directory.resolve("shaders/" + name);
        Files.createDirectories(p.getParent()); Files.writeString(p,text);
    }
    private PreparedPackPipeline prepare() throws Exception {
        file("gbuffers_terrain.vsh",V); file("gbuffers_terrain.fsh",F);
        file("final.fsh","void main(){gl_FragColor=vec4(1);}");
        return new PackProgramCompiler(ShaderPackLimits.DEFAULT).prepare(
            new ShaderPackDescriptor("shadow-read","Shadow read",directory,ShaderPackDescriptor.Kind.DIRECTORY));
    }
    @Test void terrainBindsDistinctShadowSamplerAcrossMeshModes() throws Exception {
        for (boolean extended : new boolean[] {false,true}) {
            var p = NativeTerrainProgram.adaptWithMesh(V,F,List.of(0),false,extended);
            assertTrue(p.usesShadowColor());
            assertTrue(p.fragmentSource().contains("texture(OculusShadowColor,"));
            assertFalse(p.usesNoise());
        }
    }
    @Test void shadowFeedbackAndUnsupportedAliasesRemainRejected() {
        assertThrows(ShaderCompatibilityException.class,()->NativeTerrainProgram.adaptShadow(V,F,List.of(0)));
        for (String fragment : List.of(F.replace("shadowcolor0","shadowcolor2"), F.replace("shadowcolor0","colortex0"),
            F.replace("sampler2D","sampler2DShadow"), F.replace("shadowcolor0;","shadowcolor0[2];"),
            "uniform sampler2D shadowcolor0;\n"+F, F.replace("shadowcolor0","OculusShadowColor")))
            assertThrows(ShaderCompatibilityException.class,()->NativeTerrainProgram.adapt(V,fragment,List.of(0)));
        assertThrows(ShaderCompatibilityException.class,()->NativeTerrainProgram.adapt("uniform sampler2D shadowcolor0;\n"
            +V.replace("ftransform()","ftransform()+texture2D(shadowcolor0,vec2(.5))"),F,List.of(0)));
    }
    @Test void activeReadRequiresNativeProducer() throws Exception {
        var p = prepare().world().variant("shaders");
        assertNull(p.nativeTerrain());
        assertTrue(p.nativeTerrainIssue().contains("supported native shadow producer"));
        file("shadow.vsh",V);
        file("shadow.fsh","uniform float unknownValue;\nvoid main(){gl_FragColor=vec4(unknownValue);}");
        p = prepare().world().variant("shaders");
        assertNull(p.nativeTerrain()); assertNull(p.nativeShadow());
        file("shadow.fsh","void main(){gl_FragColor=vec4(.25,.5,.75,1);}");
        p = prepare().world().variant("shaders");
        assertNotNull(p.nativeShadow(),p.nativeShadowIssue());
        assertNotNull(p.nativeTerrain(),p.nativeTerrainIssue());
        assertTrue(p.nativeTerrain().usesShadowColor());
    }
    @Test void unusedSamplerDoesNotRequestShadowTexture() throws Exception {
        var p = NativeTerrainProgram.adapt(V,"uniform sampler2D shadowcolor0;\nvoid main(){gl_FragColor=vec4(1);}",List.of(0));
        assertFalse(p.usesShadowColor());
    }
    @Test void bothReadersStayDistinctAndRequireTheirActualProducer() throws Exception {
        var source = "uniform sampler2D shadowcolor0;\nuniform sampler2D shadowcolor1;\n"
            + "void main(){gl_FragColor=texture2D(shadowcolor0,vec2(.5))+texture2D(shadowcolor1,vec2(.5));}";
        for (boolean extended : new boolean[]{false,true}) {
            var consumer = NativeTerrainProgram.adaptWithMesh(V,source,List.of(0),false,extended);
            assertEquals(List.of(0,1),consumer.shadowColorInputs());
            var one = NativeTerrainProgram.adaptShadow(V,"void main(){gl_FragColor=vec4(1);}",List.of(0));
            assertFalse(consumer.hasShadowProducer(one));
            var both = NativeTerrainProgram.adaptShadow(V,
                "void main(){gl_FragData[0]=vec4(.25);gl_FragData[1]=vec4(.75);}",List.of(1,0));
            assertTrue(consumer.hasShadowProducer(both));
            assertNotEquals(both.fingerprint(),NativeTerrainProgram.adaptShadow(V,
                "void main(){gl_FragData[0]=vec4(.25);gl_FragData[1]=vec4(.75);}",List.of(0,1)).fingerprint());
            assertThrows(ShaderCompatibilityException.class,()->NativeTerrainProgram.adaptShadow(V,source,List.of(0,1)));
        }
    }
    @Test void missingOutputAndUnsupportedShadowFormatsAreNotInvented() throws Exception {
        var fragment = "void main(){gl_FragColor=vec4(1);}";
        assertThrows(ShaderCompatibilityException.class,()->NativeTerrainProgram.adaptShadow(V,fragment,List.of(0,1)));
        assertThrows(ShaderCompatibilityException.class,()->NativeTerrainProgram.adaptShadow(V,fragment,List.of(1,1)));
        assertThrows(ShaderCompatibilityException.class,()->NativeTerrainProgram.adaptShadow(V,
            "/*\nconst int shadowcolor1Format=RGBA16F;\n*/\n"+fragment,List.of(1)));
        assertEquals(List.of(1),NativeTerrainProgram.adaptShadow(V,
            "const int shadowcolor1Format=RGBA8;\n"+fragment,List.of(1)).outputTargets());
    }
    @Test void unusedWorldDepthDeclarationDoesNotInventAShadowRead() throws Exception {
        var f="uniform sampler2D depthtex1;\nvoid main(){gl_FragColor=vec4(1);}";
        var p=NativeTerrainProgram.adaptShadow(V,f,List.of(0));
        assertFalse(p.usesOpaqueWorldDepth());
        assertFalse(p.fragmentSource().contains("depthtex1"));
        assertThrows(ShaderCompatibilityException.class,()->NativeTerrainProgram.adaptShadow(V,
            f.replace("sampler2D depthtex1", "float depthtex1"),List.of(0)));
        assertThrows(ShaderCompatibilityException.class,()->NativeTerrainProgram.adaptShadow(V,
            f.replace("vec4(1)", "texture2D(depthtex1,vec2(.5))"),List.of(0)));
    }
    @Test void secondReaderRemainsUnsupportedUntilEveryLayerHasAProducer() throws Exception {
        file("gbuffers_water.vsh",V);
        file("gbuffers_water.fsh",F.replace("shadowcolor0","shadowcolor1"));
        file("shadow.vsh",V);
        file("shadow.fsh","void main(){gl_FragColor=vec4(1);}");
        var world = prepare().world().variant("shaders");
        assertNull(world.terrainLayers().program(TerrainLayerPlan.Layer.TRANSLUCENT).nativeProgram());
        file("shadow.fsh","/* DRAWBUFFERS:10 */\nvoid main(){gl_FragData[0]=vec4(.75);gl_FragData[1]=vec4(.25);}");
        world = prepare().world().variant("shaders");
        assertEquals(List.of(1,0),world.shadowTargets());
        assertTrue(world.terrainLayers().nativeProgramsReady());
    }
    @Test void readGuardRejectsIncompleteFramesAndFeedback() {
        assertDoesNotThrow(()->ShadowColorReadGuard.requireCompleted(false,true));
        assertThrows(IllegalStateException.class,()->ShadowColorReadGuard.requireCompleted(false,false));
        assertThrows(IllegalStateException.class,()->ShadowColorReadGuard.requireCompleted(true,true));
        assertThrows(IllegalStateException.class,()->ShadowColorReadGuard.requireCompleted(true,false));
    }
}
