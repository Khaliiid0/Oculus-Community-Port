package dev.oculusforge.render.program;

import static org.junit.jupiter.api.Assertions.*;
import static dev.oculusforge.render.program.ShadowTerrainPolicy.Stage.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import dev.oculusforge.shaderpack.*;

final class ShadowTerrainPolicyTest {
    @Test void translucentGpuProbePreparesNativeWorldAndIndependentDepthReads() throws Exception {
        var root=Path.of(getClass().getClassLoader().getResource("translucent-shadow-depth").toURI());
        var prepared=new PackProgramCompiler(ShaderPackLimits.DEFAULT).prepare(
            new ShaderPackDescriptor("translucent","Translucent",root,ShaderPackDescriptor.Kind.DIRECTORY));
        assertEquals(ShadowTerrainPolicy.DEFAULT,prepared.world().shadowTerrain());
        var variant=prepared.world().variant("shaders");
        assertNotNull(variant.nativeTerrain(),variant.nativeTerrainIssue());
        assertNotNull(variant.nativeShadow(),variant.nativeShadowIssue());
        var fragment=new LegacyFinalShaderAdapter().adapt(Files.readString(root.resolve("shaders/final.fsh")));
        assertEquals(2,fragment.samplers().stream().filter(s->s.texture()==LegacyFinalShaderAdapter.TextureSemantic.SHADOW_DEPTH).count());
        assertTrue(fragment.source().contains("oculus_fetchDepth(shadowtex0,"));
        assertTrue(fragment.source().contains("oculus_fetchDepth(shadowtex1,"));
    }
    @Test void defaultOrderCapturesOpaqueDepthBeforeTransparentDraws() throws Exception {
        assertEquals(ShadowTerrainPolicy.DEFAULT, ShadowTerrainPolicy.parse("", Map.of()));
        assertEquals(List.of(OPAQUE,SNAPSHOT,TRANSLUCENT), ShadowTerrainPolicy.DEFAULT.stages());
    }
    @Test void disabledGroupsStillCaptureClearedOpaqueDepth() {
        assertEquals(List.of(SNAPSHOT),new ShadowTerrainPolicy(false,false).stages());
        assertEquals(List.of(SNAPSHOT,TRANSLUCENT),new ShadowTerrainPolicy(false,true).stages());
        assertEquals(List.of(OPAQUE,SNAPSHOT),new ShadowTerrainPolicy(true,false).stages());
        assertThrows(UnsupportedOperationException.class,()->ShadowTerrainPolicy.DEFAULT.stages().clear());
    }
    @Test void flagsWhitespaceCommentsAndLastAssignmentAreRespected() throws Exception {
        var p=ShadowTerrainPolicy.parse("# shadowTerrain=false\nshadowTerrain = true\nshadowTerrain=false\nshadowTranslucent : false\n",Map.of());
        assertEquals(new ShadowTerrainPolicy(false,false),p);
    }
    @Test void knownConditionalEnvironmentSelectsOnlyActiveFlags() throws Exception {
        String text="#if MC_VERSION >= 12602\nshadowTranslucent=false\n#else\nshadowTerrain=false\n#endif\n";
        assertEquals(new ShadowTerrainPolicy(true,false),ShadowTerrainPolicy.parse(text,Map.of("MC_VERSION","12602")));
        assertEquals(new ShadowTerrainPolicy(false,true),ShadowTerrainPolicy.parse(text,Map.of("MC_VERSION","12111")));
    }
    @Test void unknownRelevantOptionsFailRatherThanGuess() {
        for(String key:List.of("shadowTerrain","shadowTranslucent","shadow\\u0054errain")) {
            assertThrows(ShaderCompatibilityException.class,()->ShadowTerrainPolicy.parse(
                "#if UNKNOWN_OPTION\n"+key+"=false\n#endif\n",Map.of()));
        }
    }
    @Test void unknownUnrelatedPropertiesDoNotChangeShadowPolicy() throws Exception {
        assertEquals(ShadowTerrainPolicy.DEFAULT,ShadowTerrainPolicy.parse(
            "#if UNKNOWN_OPTION\nuniform.float.example=1.0\n#endif\n",Map.of()));
    }
    @Test void invalidFlagsAndUnfinishedBranchesFailClosed() {
        for(String text:List.of("shadowTerrain=maybe","shadowTranslucent=1","#if 1\nshadowTerrain=false"))
            assertThrows(ShaderCompatibilityException.class,()->ShadowTerrainPolicy.parse(text,Map.of()));
    }
    @Test void unrelatedLocalMacrosDoNotRejectPacksWithoutShadowFlagBranches() throws Exception {
        assertEquals(ShadowTerrainPolicy.DEFAULT,ShadowTerrainPolicy.parse(
            "#ifdef MC_OS_MAC\n#define COLORED_LIGHTING 0\n#endif\n",Map.of("MC_OS_WINDOWS","1")));
    }
    @Test void locallyRedefinedFlagConditionsAreNotGuessedFromTheEnvironment() {
        assertThrows(ShaderCompatibilityException.class,()->ShadowTerrainPolicy.parse(
            "#define QUALITY 0\n#if QUALITY\nshadowTerrain=false\n#endif\n",Map.of("QUALITY","1")));
    }
    @Test void escapedKeysAndContinuationCannotHideOverrides() throws Exception {
        assertFalse(ShadowTerrainPolicy.parse("shadow\\u0054errain=fal\\\n se",Map.of()).opaque());
    }
    @Test void planCopiesPreserveShadowPolicy() {
        var policy=new ShadowTerrainPolicy(false,true);
        var plan=new WorldProgramPlan(Map.of(),false,policy);
        assertEquals(policy,plan.withFormats(Map.of()).shadowTerrain());
        assertEquals(policy,plan.withRequiredColorBuffers(Map.of()).shadowTerrain());
    }
    @Test void inspectionLoadsFlagsAndRejectsUnimplementedLayerSpecificShadows(@TempDir Path root) throws Exception {
        Path shaders=Files.createDirectories(root.resolve("shaders"));
        Files.writeString(shaders.resolve("shadow.vsh"),"void main(){gl_Position=ftransform();}");
        Files.writeString(shaders.resolve("shadow.fsh"),"void main(){gl_FragColor=vec4(1);}");
        Files.writeString(shaders.resolve("shaders.properties"),"shadowTerrain=false\nshadowTranslucent=true");
        try(var fs=new DirectoryPackFileSystem(root,ShaderPackLimits.DEFAULT)) {
            var plan=WorldProgramPlan.inspect(fs,List.of("shaders"),ShaderPackLimits.DEFAULT,Map.of());
            assertEquals(new ShadowTerrainPolicy(false,true),plan.shadowTerrain());
            assertNotNull(plan.variant("shaders").nativeShadow());
        }
        Files.writeString(shaders.resolve("shadow_water.fsh"),"void main(){gl_FragColor=vec4(1);}");
        try(var fs=new DirectoryPackFileSystem(root,ShaderPackLimits.DEFAULT)) {
            var variant=WorldProgramPlan.inspect(fs,List.of("shaders"),ShaderPackLimits.DEFAULT,Map.of()).variant("shaders");
            assertNull(variant.nativeShadow());
            assertTrue(variant.nativeShadowIssue().contains("per-layer shadow selection"));
        }
    }
}
