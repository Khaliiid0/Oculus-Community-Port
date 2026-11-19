package dev.oculusforge.render.world;

import dev.oculusforge.OculusForge;
import dev.oculusforge.render.program.BlockMaterialMap;
import dev.oculusforge.render.program.ShaderCompatibilityException;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraftforge.registries.GameData;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

public final class BlockMaterialResolver {
    private BlockMaterialResolver() { }

    public static MaterialIdTable resolve(BlockMaterialMap map) throws ShaderCompatibilityException {
        var registry = GameData.BlockCallbacks.getBlockStateIDMap();
        int count = registry.size() + 1;
        if (count > MaterialIdTable.MAX_STATES) throw new ShaderCompatibilityException("Block state registry exceeds material table budget");
        int[] ids = MaterialIdTable.unmapped(count);
        int matched = 0, missing = 0, visits = 0;
        for (var rule : map.rules()) {
            var holder = BuiltInRegistries.BLOCK.get(Identifier.parse(rule.block()));
            if (holder.isEmpty()) { missing++; continue; }
            for (BlockState state : holder.get().value().getStateDefinition().getPossibleStates()) {
                if (++visits > 4_000_000) throw new ShaderCompatibilityException("Material selector state-visit budget exceeded");
                int index = registry.getId(state) + 1;
                if (index <= 0 || index >= ids.length || ids[index] != -1) continue;
                if (matches(rule, state)) { ids[index] = rule.materialId(); matched++; }
            }
        }
        OculusForge.LOGGER.info("Resolved material table: {} mapped block states, {} absent block selectors, {} lookup entries", matched, missing, ids.length);
        return new MaterialIdTable(ids);
    }

    private static boolean matches(BlockMaterialMap.Rule rule, BlockState state) {
        for (var filter : rule.properties().entrySet()) {
            var property = state.getBlock().getStateDefinition().getProperty(filter.getKey());
            if (property == null || !filter.getValue().contains(valueName(state, property))) return false;
        }
        return true;
    }

    private static <T extends Comparable<T>> String valueName(BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }
}
