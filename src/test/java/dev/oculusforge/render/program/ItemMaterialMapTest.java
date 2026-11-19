package dev.oculusforge.render.program;

import static org.junit.jupiter.api.Assertions.*;
import dev.oculusforge.render.world.*;
import dev.oculusforge.shaderpack.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ItemMaterialMapTest {
    @TempDir Path directory;
    @Test void explicitIdsNamespacesAndUnregisteredModelsAreNotRegistryIndices() throws Exception {
        var map=ItemMaterialMap.parse("item.44002=torch minecraft:soul_torch\nitem.45001=custom:models/trim_emerald\n",Map.of());
        assertEquals(44002,map.id("minecraft:torch"));
        assertEquals(44002,map.id("minecraft:soul_torch"));
        assertEquals(45001,map.id("custom:models/trim_emerald"));
        assertEquals(-1,map.id("minecraft:stone"));
        assertThrows(UnsupportedOperationException.class,()->map.ids().clear());
    }
    @Test void modelOverridesTakePrecedenceAndAirIsNotMissingPlayer() throws Exception {
        var map=ItemMaterialMap.parse("item.40008=air\nitem.7=stick\nitem.9=custom:torch_model",Map.of());
        assertEquals(new HeldItemIds(9,40008),map.resolve(new HeldItemKeys(
            HeldItemKeys.modelOrItem("custom:torch_model","minecraft:stick"),"minecraft:air")));
        assertEquals("minecraft:stick",HeldItemKeys.modelOrItem(null,"minecraft:stick"));
        assertEquals(HeldItemIds.NONE,map.resolve(HeldItemKeys.NONE));
        assertEquals(HeldItemIds.NONE,ItemMaterialMap.EMPTY.resolve(new HeldItemKeys("minecraft:air","minecraft:torch")));
        assertSame(HeldItemKeys.NONE,HeldItemKeys.capture(null));
    }
    @Test void signedIntegerRangeIsPreservedWithoutFloatConversions() throws Exception {
        var map=ItemMaterialMap.parse("item.-2147483648=stone\nitem.2147483647=dirt\nitem.0=air\nitem.-1=stick",Map.of());
        assertEquals(new HeldItemIds(Integer.MIN_VALUE,Integer.MAX_VALUE),map.resolve(new HeldItemKeys("minecraft:stone","minecraft:dirt")));
        assertEquals(0,map.id("minecraft:air"));assertEquals(-1,map.id("minecraft:stick"));
    }
    @Test void duplicatePropertyKeysReplaceAndEquivalentSelectorsAgree() throws Exception {
        var map=ItemMaterialMap.parse("item.12=stone\nitem.3=dirt\nitem.12=sand minecraft:sand",Map.of());
        assertEquals(-1,map.id("minecraft:stone"));assertEquals(12,map.id("minecraft:sand"));assertEquals(3,map.id("minecraft:dirt"));
        assertThrows(ShaderCompatibilityException.class,()->ItemMaterialMap.parse("item.1=stone\nitem.2=minecraft:stone",Map.of()));
    }
    @Test void commentsVersionConditionsAndContinuationsWork() throws Exception {
        var map=ItemMaterialMap.parse("# ordinary comment\n! another\n#if MC_VERSION >= 12100\nitem.12=stone \\\n dirt\n#else\nitem.3=sand\n#endif\nitem.5=",Map.of("MC_VERSION","12111"));
        assertEquals(Map.of("minecraft:stone",12,"minecraft:dirt",12),map.ids());
    }
    @Test void malformedAndUnsupportedRulesFailClosed() {
        for(String source:List.of("item.2147483648=stone","item.-2147483649=stone","item.+1=stone","item.01=stone",
            "item.-0=stone","block.1=stone","item.1=1","item.1=%minecraft:logs","item.1=#minecraft:logs",
            "item.1=minecraft:*","item.1=stone:age=1","item.1=Minecraft:stone","item.1=stone\0","item.1=\\uZZZZ",
            "#include \"external\"","#if 0\n#define X stone\n#endif"))
            assertThrows(Exception.class,()->ItemMaterialMap.parse(source,Map.of()),source);
    }
    @Test void boundsSourceSelectorsAndCopiesCallerMap() {
        assertThrows(ShaderCompatibilityException.class,()->ItemMaterialMap.parse(" ".repeat(ItemMaterialMap.MAX_SOURCE+1),Map.of()));
        assertThrows(ShaderCompatibilityException.class,()->ItemMaterialMap.parse("item.1="+"stone ".repeat(ItemMaterialMap.MAX_SELECTORS+1),Map.of()));
        var source=new HashMap<String,Integer>();source.put("minecraft:stone",1);
        var map=new ItemMaterialMap(source);source.put("minecraft:stone",2);assertEquals(1,map.id("minecraft:stone"));
    }
    @Test void absentMapHasExplicitMissingSentinelsAndMapsRemainPackLocal() throws Exception {
        try(var pack=new DirectoryPackFileSystem(directory,ShaderPackLimits.DEFAULT)) {
            assertSame(ItemMaterialMap.EMPTY,ItemMaterialMap.load(pack,Map.of()));
        }
        var keys=new HeldItemKeys("minecraft:torch","minecraft:air");
        var a=ItemMaterialMap.parse("item.1=torch air",Map.of());var b=ItemMaterialMap.parse("item.2=torch air",Map.of());
        assertEquals(new HeldItemIds(1,1),a.resolve(keys));assertEquals(new HeldItemIds(2,2),b.resolve(keys));
        assertEquals(new HeldItemIds(1,1),a.resolve(keys));
    }
}
