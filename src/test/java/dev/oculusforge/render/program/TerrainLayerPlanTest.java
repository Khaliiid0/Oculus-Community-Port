package dev.oculusforge.render.program;

import static org.junit.jupiter.api.Assertions.*;
import static dev.oculusforge.render.program.TerrainLayerPlan.Layer.*;
import dev.oculusforge.shaderpack.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class TerrainLayerPlanTest {
    @TempDir Path root;
    private void shader(String name,String targets) throws Exception {
        Path shaders=Files.createDirectories(root.resolve("shaders"));
        Files.writeString(shaders.resolve(name+".vsh"),"void main(){gl_Position=ftransform();}");
        Files.writeString(shaders.resolve(name+".fsh"),"/* DRAWBUFFERS:"+targets+" */\nvoid main(){gl_FragData[0]=vec4(1);}");
    }
    private TerrainLayerPlan inspect() throws Exception {
        try(var fs=new DirectoryPackFileSystem(root,ShaderPackLimits.DEFAULT)) {
            return TerrainLayerPlan.inspect(fs,"shaders",ShaderPackLimits.DEFAULT,Map.of(),NativeCustomUniforms.EMPTY,
                new ColorBufferFormats.Builder(ColorBufferFormats.DEFAULT));
        }
    }
    @Test void commonFallbackIsPreparedOnceAndSharedAcrossLayers() throws Exception {
        shader("gbuffers_terrain","0");
        var plan=inspect();
        assertSame(plan.program(SOLID),plan.program(CUTOUT));
        assertSame(plan.program(SOLID),plan.program(TRANSLUCENT));
        assertNotNull(plan.program(SOLID).nativeProgram());
        assertFalse(plan.requiresOpaquePassSplit());
        assertTrue(plan.runtimeReadyWithoutOpaqueSplit());
    }
    @Test void specificProgramsNeverLeakToOtherLayers() throws Exception {
        shader("gbuffers_terrain","0");shader("gbuffers_terrain_solid","4");shader("gbuffers_water","7");
        var plan=inspect();
        assertEquals("gbuffers_terrain_solid",plan.program(SOLID).name());
        assertEquals("gbuffers_terrain",plan.program(CUTOUT).name());
        assertEquals("gbuffers_water",plan.program(TRANSLUCENT).name());
        assertEquals(Set.of(0,4,7),plan.requiredColorBuffers());
        assertTrue(plan.requiresOpaquePassSplit());
        assertFalse(plan.runtimeReadyWithoutOpaqueSplit());
    }
    @Test void orderedOutputLayoutsMatterEvenWithSameBufferSet() throws Exception {
        shader("gbuffers_terrain_solid","04");shader("gbuffers_terrain_cutout","40");shader("gbuffers_water","0");
        var plan=inspect();
        assertEquals(List.of(0,4),plan.program(SOLID).targets());
        assertEquals(List.of(4,0),plan.program(CUTOUT).targets());
        assertTrue(plan.requiresOpaquePassSplit());
        assertTrue(plan.nativeProgramsReady());
    }
    @Test void texturedLitPrecedesTexturedAndBasic() throws Exception {
        shader("gbuffers_textured_lit","0");shader("gbuffers_textured","3");shader("gbuffers_basic","4");
        assertEquals("gbuffers_textured_lit",inspect().program(SOLID).name());
    }
    @Test void solidOnlyPackDoesNotBecomeCutoutOrWaterFallback() throws Exception {
        shader("gbuffers_terrain_solid","4");
        var plan=inspect();
        assertNotNull(plan.program(SOLID).nativeProgram());
        assertNull(plan.program(CUTOUT).nativeProgram());
        assertNull(plan.program(TRANSLUCENT).nativeProgram());
        assertTrue(plan.program(CUTOUT).issue().contains("No terrain fallback"));
    }
    @Test void incompleteSpecificPairDoesNotSilentlyUseGenericShader() throws Exception {
        shader("gbuffers_terrain","0");
        Files.writeString(root.resolve("shaders/gbuffers_water.fsh"),"void main(){gl_FragColor=vec4(1);}");
        var program=inspect().program(TRANSLUCENT);
        assertEquals("gbuffers_water",program.name());assertNull(program.nativeProgram());
        assertTrue(program.issue().contains("Missing paired"));
    }
    @Test void oneUnsupportedProgramDoesNotMislabelOtherLayers() throws Exception {
        shader("gbuffers_terrain","0");shader("gbuffers_water","7");
        Files.writeString(root.resolve("shaders/gbuffers_water.vsh"),
            "uniform float unsupportedWater;\nvoid main(){gl_Position=ftransform()*unsupportedWater;}");
        var plan=inspect();
        assertNotNull(plan.program(SOLID).nativeProgram());assertNull(plan.program(TRANSLUCENT).nativeProgram());
        assertTrue(plan.program(TRANSLUCENT).issue().contains("unsupportedWater"));
        assertEquals(List.of(7),plan.program(TRANSLUCENT).targets());
    }
    @Test void shadowProducerGuardIsPerLayer() throws Exception {
        shader("gbuffers_terrain","0");shader("gbuffers_water","0");
        Files.writeString(root.resolve("shaders/gbuffers_water.fsh"),
            "uniform sampler2D shadowtex1;\nvoid main(){gl_FragColor=texture2D(shadowtex1,vec2(.5));}");
        var original=inspect();
        assertTrue(original.program(TRANSLUCENT).nativeProgram().usesOpaqueShadowDepth());
        var guarded=original.requireShadowProducer(false);
        assertNotNull(guarded.program(SOLID).nativeProgram());
        assertNull(guarded.program(TRANSLUCENT).nativeProgram());
        assertTrue(guarded.program(TRANSLUCENT).issue().contains("supported native shadow producer"));
        assertSame(original,original.requireShadowProducer(true));
    }
    @Test void inspectionKeepsLegacyBaseInactiveAndPreservesLayersOnPlanCopies() throws Exception {
        shader("gbuffers_terrain","0");shader("gbuffers_water","7");
        try(var fs=new DirectoryPackFileSystem(root,ShaderPackLimits.DEFAULT)) {
            var variant=WorldProgramPlan.inspect(fs,List.of("shaders"),ShaderPackLimits.DEFAULT,Map.of()).variant("shaders");
            assertNull(variant.nativeTerrain());
            assertTrue(variant.nativeTerrainIssue().contains("per-layer selection"));
            assertNotNull(variant.terrainLayers().program(TRANSLUCENT).nativeProgram());
            assertTrue(variant.layerRuntimeEligible());
            assertTrue(variant.worldColorTargets().contains(7));
            assertSame(variant.terrainLayers(),variant.withFormats(ColorBufferFormats.DEFAULT).terrainLayers());
            assertSame(variant.terrainLayers(),variant.withColorTargets(Set.of(9)).terrainLayers());
        }
    }
    @Test void plansAndTheirOutputListsAreImmutableAndComplete() throws Exception {
        shader("gbuffers_terrain","0");var plan=inspect();
        assertThrows(UnsupportedOperationException.class,()->plan.programs().clear());
        assertThrows(UnsupportedOperationException.class,()->plan.program(SOLID).targets().clear());
        assertThrows(IllegalArgumentException.class,()->new TerrainLayerPlan(Map.of()));
        assertThrows(IllegalArgumentException.class,()->new TerrainLayerPlan.Program("bad",List.of(0),null,""));
    }

    @Test void distinctProbeHasCompatibleOpaqueOutputsAndSeparateWaterLayout() throws Exception {
        Path probe=Path.of(getClass().getClassLoader().getResource("native-terrain-layers").toURI());
        var prepared=new PackProgramCompiler(ShaderPackLimits.DEFAULT).prepare(
            new ShaderPackDescriptor("layers","Layers",probe,ShaderPackDescriptor.Kind.DIRECTORY));
        var variant=prepared.world().variant("shaders");
        assertTrue(variant.terrainLayers().runtimeReadyWithoutOpaqueSplit());
        assertEquals(List.of(0),variant.terrainLayers().program(SOLID).targets());
        assertEquals(List.of(0),variant.terrainLayers().program(CUTOUT).targets());
        assertEquals(List.of(0,4),variant.terrainLayers().program(TRANSLUCENT).targets());
        assertEquals(3,variant.nativeTerrainPrograms().size());
        assertTrue(variant.worldColorTargets().contains(4));
    }

    @Test void unsupportedLayerCannotActivatePartialNativeRouting() throws Exception {
        shader("gbuffers_terrain","0");
        Files.writeString(root.resolve("shaders/gbuffers_water.fsh"),"void main(){gl_FragColor=vec4(1);}");
        assertFalse(inspect().runtimeReadyWithoutOpaqueSplit());
        assertFalse(inspect().nativeProgramsReady());
    }

    @Test void differingOpaqueLayoutsActivateCompleteNativePlan() throws Exception {
        shader("gbuffers_terrain","0");
        shader("gbuffers_terrain_solid","04");
        shader("gbuffers_terrain_cutout","60");
        shader("gbuffers_water","07");
        try (var fs = new DirectoryPackFileSystem(root,ShaderPackLimits.DEFAULT)) {
            var variant=WorldProgramPlan.inspect(fs,List.of("shaders"),ShaderPackLimits.DEFAULT,Map.of()).variant("shaders");
            assertTrue(variant.layerRuntimeEligible());
            assertTrue(variant.terrainLayers().requiresOpaquePassSplit());
            assertEquals(List.of(6,0),variant.terrainLayers().program(CUTOUT).targets());
            // Identical adapted sources may be shared despite different attachments.
            for (var layer : TerrainLayerPlan.Layer.values())
                assertTrue(variant.nativeTerrainPrograms().contains(variant.terrainLayers().program(layer).nativeProgram()));
            assertTrue(variant.worldColorTargets().containsAll(Set.of(0,4,6,7)));
        }
    }

    @Test void descriptorSignatureChangesWithLayerShaderOutputOrderAndFormat() throws Exception {
        shader("gbuffers_terrain","0");shader("gbuffers_water","04");
        String first=inspect().descriptorSignature(ColorBufferFormats.DEFAULT);
        shader("gbuffers_water","40");
        var plan=inspect();
        String reordered=plan.descriptorSignature(ColorBufferFormats.DEFAULT);
        assertNotEquals(first,reordered);
        Files.writeString(root.resolve("shaders/gbuffers_water.fsh"),
            "/* DRAWBUFFERS:40 */\nvoid main(){gl_FragData[0]=vec4(.5);gl_FragData[1]=vec4(1);}");
        assertNotEquals(reordered,inspect().descriptorSignature(ColorBufferFormats.DEFAULT));
        assertNotEquals(reordered,plan.descriptorSignature(new ColorBufferFormats(Map.of(4,ColorBufferFormat.RGBA16F))));
    }

    @Test void waterOnlyResourceBindingsAreIncludedInPreparation() throws Exception {
        shader("gbuffers_terrain","0");shader("gbuffers_water","0");
        Files.writeString(root.resolve("shaders/gbuffers_water.vsh"),
            TerrainMeshMode.EXTENDED?"attribute vec4 mc_Entity;\nvarying float material;\nvoid main(){material=mc_Entity.x;gl_Position=ftransform();}":
                "varying float material;\nvoid main(){material=1.0;gl_Position=ftransform();}");
        Files.writeString(root.resolve("shaders/gbuffers_water.fsh"),
            "varying float material;\nuniform sampler2D noisetex;\nuniform float viewWidth;\n"
            +"void main(){gl_FragColor=texture2D(noisetex,vec2(.5))*vec4(material/viewWidth);}");
        Files.writeString(root.resolve("shaders/final.fsh"),"void main(){gl_FragColor=vec4(1);}");
        Files.writeString(root.resolve("shaders/block.properties"),"block.7=minecraft:red_stained_glass");
        var prepared=new PackProgramCompiler(ShaderPackLimits.DEFAULT).prepare(
            new ShaderPackDescriptor("bindings","Bindings",root,ShaderPackDescriptor.Kind.DIRECTORY));
        var world=prepared.world().variant("shaders");
        assertTrue(world.terrainLayers().runtimeReadyWithoutOpaqueSplit(),world.terrainLayers().program(TRANSLUCENT).issue());
        var water=world.terrainLayers().program(TRANSLUCENT).nativeProgram();
        assertEquals(TerrainMeshMode.EXTENDED,water.usesMaterialIds());assertTrue(water.usesWorldMatrices());assertTrue(water.usesNoise());
        assertNotNull(prepared.noise());
        assertTrue(world.nativePrograms().contains(water));
    }

    @Test void waterOnlyCustomUniformsAreCollectedBeforeLayerAdaptation() throws Exception {
        shader("gbuffers_terrain","0");shader("gbuffers_water","0");
        Files.writeString(root.resolve("shaders/shaders.properties"),"uniform.float.waterTint=frameCounter % 2");
        Files.writeString(root.resolve("shaders/gbuffers_water.fsh"),
            "uniform float waterTint;\nvoid main(){gl_FragColor=vec4(waterTint);}");
        Files.writeString(root.resolve("shaders/final.fsh"),"void main(){gl_FragColor=vec4(1);}");
        var prepared=new PackProgramCompiler(ShaderPackLimits.DEFAULT).prepare(
            new ShaderPackDescriptor("custom-water","Custom Water",root,ShaderPackDescriptor.Kind.DIRECTORY));
        var world=prepared.world().variant("shaders");
        assertTrue(world.terrainLayers().runtimeReadyWithoutOpaqueSplit(),world.terrainLayers().program(TRANSLUCENT).issue());
        assertTrue(world.terrainLayers().program(TRANSLUCENT).nativeProgram().usesWorldMatrices());
        assertTrue(world.terrainLayers().program(TRANSLUCENT).nativeProgram().fragmentSource().contains("oculusCustomValue0"));
    }

    @Test void unrecognizedTerrainOverrideDoesNotSilentlyActivateNativeLayers() throws Exception {
        shader("gbuffers_terrain","0");shader("gbuffers_terrain_unknown","0");
        try(var fs=new DirectoryPackFileSystem(root,ShaderPackLimits.DEFAULT)) {
            var world=WorldProgramPlan.inspect(fs,List.of("shaders"),ShaderPackLimits.DEFAULT,Map.of()).variant("shaders");
            assertTrue(world.terrainLayers().runtimeReadyWithoutOpaqueSplit());
            assertFalse(world.layerRuntimeEligible());
            assertTrue(world.nativeTerrainPrograms().isEmpty());
        }
    }
}
