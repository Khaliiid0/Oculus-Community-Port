package dev.oculusforge.render.program;

import static org.junit.jupiter.api.Assertions.*;
import dev.oculusforge.shaderpack.DirectoryPackFileSystem;
import dev.oculusforge.shaderpack.ShaderPackLimits;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class BlockMaterialMapTest {
    @TempDir Path directory;

    @Test void preservesSelectorOrderNamespacesAndFilteredValues() throws Exception {
        var map = BlockMaterialMap.parse("block.42=minecraft:oak_stairs:half=top:facing=north,south stone\nblock.7=mod:block\n", Map.of());
        assertEquals(3, map.rules().size());
        var rule = map.rules().getFirst();
        assertEquals(42, rule.materialId());
        assertEquals("minecraft:oak_stairs", rule.block());
        assertTrue(rule.matches(Map.of("half", "top", "facing", "north", "waterlogged", "false")));
        assertFalse(rule.matches(Map.of("half", "top")));
        assertFalse(rule.matches(Map.of("half", "bottom", "facing", "north")));
        assertEquals("minecraft:stone", map.rules().get(1).block());
        assertEquals("mod:block", map.rules().get(2).block());
        assertThrows(UnsupportedOperationException.class, () -> map.rules().clear());
        assertThrows(UnsupportedOperationException.class, () -> rule.properties().get("half").clear());
    }

    @Test void preprocessesVersionConditionsCommentsAndContinuations() throws Exception {
        String source = "# ordinary comment\n! another comment\n#if MC_VERSION >= 12100\nblock.12=stone " +
            "\\" + "\n dirt\n#else\nblock.3=sand\n#endif\nblock.5=\n";
        var map = BlockMaterialMap.parse(source, Map.of("MC_VERSION", "12111"));
        assertEquals(2, map.rules().size());
        assertEquals(12, map.rules().getLast().materialId());
        assertEquals("minecraft:dirt", map.rules().getLast().block());
    }

    @Test void duplicateKeysReplaceValuesWithoutReorderingPrecedence() throws Exception {
        var map = BlockMaterialMap.parse("block.12=stone\nblock.3=dirt\nblock.12=sand\n", Map.of());
        assertEquals(12, map.rules().getFirst().materialId());
        assertEquals("minecraft:sand", map.rules().getFirst().block());
        assertEquals(3, map.rules().getLast().materialId());
    }

    @Test void unsupportedOrMalformedRulesFailClosed() {
        for (String source : new String[] {
            "block.65536=stone", "block.-1=stone", "block.1=1", "layer.unknown=stone",
            "block.1=%minecraft:leaves", "block.1=#minecraft:leaves",
            "block.1=stone:age=", "block.1=stone:age=1:age=2", "block.1=Minecraft:stone",
            "#include \"outside.properties\"\n", "#define X stone\nblock.1=X",
            "block.1=stone\u0000", "block.1=" + "\\" + "uZZZZ"
        }) assertThrows(Exception.class, () -> BlockMaterialMap.parse(source, Map.of()), source);
    }

    @Test void boundsSourceAndSelectorCount() {
        assertThrows(ShaderCompatibilityException.class, () -> BlockMaterialMap.parse(" ".repeat(BlockMaterialMap.MAX_SOURCE + 1), Map.of()));
        assertThrows(ShaderCompatibilityException.class, () -> BlockMaterialMap.parse("block.1=" + "stone ".repeat(BlockMaterialMap.MAX_RULES + 1), Map.of()));
    }

    @Test void absentExplicitMapDoesNotInventLegacyIds() throws Exception {
        try (var pack = new DirectoryPackFileSystem(directory, ShaderPackLimits.DEFAULT)) {
            var e = assertThrows(ShaderCompatibilityException.class, () -> BlockMaterialMap.load(pack, Map.of()));
            assertTrue(e.getMessage().contains("legacy default IDs"));
        }
    }
}
