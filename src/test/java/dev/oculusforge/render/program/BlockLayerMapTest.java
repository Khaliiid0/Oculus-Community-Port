package dev.oculusforge.render.program;

import static org.junit.jupiter.api.Assertions.*;
import dev.oculusforge.shaderpack.*;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BlockLayerMapTest {
    @TempDir Path directory;

    @Test void parsesAllPlatformLayersAndNormalizesOnlyTheMergedCutoutMesh() throws Exception {
        var map = BlockMaterialMap.parse("layer.solid=stone\nlayer.cutout=glass\n"
            + "layer.cutout_mipped=minecraft:oak_leaves\nlayer.translucent=mod:block\nblock.43=stone", Map.of());
        assertEquals(Map.of("minecraft:stone", BlockMaterialMap.RenderLayer.SOLID,
            "minecraft:glass", BlockMaterialMap.RenderLayer.CUTOUT,
            "minecraft:oak_leaves", BlockMaterialMap.RenderLayer.CUTOUT,
            "mod:block", BlockMaterialMap.RenderLayer.TRANSLUCENT), map.layers());
        assertEquals(43, map.rules().getFirst().materialId());
        assertThrows(UnsupportedOperationException.class, () -> map.layers().clear());
    }

    @Test void selectsVersionBranchesAndPropertiesContinuationBeforeParsingLayers() throws Exception {
        var map = BlockMaterialMap.parse("# comment\n#if MC_VERSION >= 12100\nlayer.translucent=glass \\\n"
            + " oak_leaves\n#else\nlayer.unknown=stone\n#endif\n", Map.of("MC_VERSION", "12111"));
        assertEquals(2, map.layers().size());
        assertEquals(BlockMaterialMap.RenderLayer.TRANSLUCENT, map.layers().get("minecraft:oak_leaves"));
    }

    @Test void emptyAndRepeatedKeysFollowExistingPropertiesSemantics() throws Exception {
        var map = BlockMaterialMap.parse("layer.solid=stone\nlayer.solid=dirt\nlayer.cutout=\n", Map.of());
        assertEquals(Map.of("minecraft:dirt", BlockMaterialMap.RenderLayer.SOLID), map.layers());
        assertTrue(map.rules().isEmpty());
    }

    @Test void conflictingBlockLayersRejectRatherThanDependOnPropertiesIterationOrder() {
        var error = assertThrows(ShaderCompatibilityException.class, () -> BlockMaterialMap.parse(
            "layer.solid=stone\nlayer.translucent=minecraft:stone", Map.of()));
        assertTrue(error.getMessage().contains("Conflicting block layer overrides"));
    }

    @Test void equivalentMergedCutoutDuplicatesAreUnambiguous() throws Exception {
        var map = BlockMaterialMap.parse("layer.cutout=oak_leaves oak_leaves\nlayer.cutout_mipped=oak_leaves", Map.of());
        assertEquals(1, map.layers().size());
    }

    @Test void tagsStatePredicatesLegacyAndMalformedNamesRemainUnsupported() {
        for (String selector : new String[]{"%minecraft:leaves", "#minecraft:leaves", "oak_leaves:persistent=true",
            "1", "Minecraft:glass", "minecraft:", "minecraft:Glass", "minecraft:glass:age=1", "minecraft:glass:"})
            assertThrows(Exception.class, () -> BlockMaterialMap.parse("layer.translucent=" + selector, Map.of()), selector);
        assertThrows(ShaderCompatibilityException.class, () -> BlockMaterialMap.parse("layer.cutout_extra=glass", Map.of()));
    }

    @Test void materialAndLayerSelectorsShareTheSameBoundEvenForRepeatedBlocks() {
        assertThrows(ShaderCompatibilityException.class, () -> BlockMaterialMap.parse(
            "block.1=" + "stone ".repeat(BlockMaterialMap.MAX_RULES) + "\nlayer.cutout=glass", Map.of()));
        assertThrows(ShaderCompatibilityException.class, () -> BlockMaterialMap.parse(
            "layer.solid=" + "stone ".repeat(BlockMaterialMap.MAX_RULES + 1), Map.of()));
    }

    @Test void optionalAbsentMapDoesNotInventMaterialIdsOrLayers() throws Exception {
        try (var pack = new DirectoryPackFileSystem(directory, ShaderPackLimits.DEFAULT)) {
            assertSame(BlockMaterialMap.EMPTY, BlockMaterialMap.load(pack, Map.of(), false));
            assertThrows(ShaderCompatibilityException.class, () -> BlockMaterialMap.load(pack, Map.of(), true));
        }
    }

    @Test void layerFixtureLoadsEvenWhenNoShaderRequestsMaterialIds() throws Exception {
        var root = Path.of(getClass().getClassLoader().getResource("block-layer-routing").toURI());
        var prepared = new PackProgramCompiler(ShaderPackLimits.DEFAULT).prepare(new ShaderPackDescriptor(
            "layers", "Layers", root, ShaderPackDescriptor.Kind.DIRECTORY));
        assertEquals(Map.of("minecraft:oak_leaves", BlockMaterialMap.RenderLayer.TRANSLUCENT), prepared.materials().layers());
        assertTrue(prepared.materials().rules().isEmpty());
        var world = prepared.world().variant("shaders");
        assertTrue(world.layerRuntimeEligible());
        assertTrue(world.nativePrograms().stream().noneMatch(NativeTerrainProgram::usesMaterialIds));
        assertEquals(java.util.List.of(0, 4), world.terrainLayers().program(TerrainLayerPlan.Layer.TRANSLUCENT).targets());
    }
}
