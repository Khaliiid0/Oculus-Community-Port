package dev.oculusforge.render.world;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.oculusforge.OculusForge;
import dev.oculusforge.render.program.BlockMaterialMap;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

/** Model-quad routing only: never changes global block properties, physics or fluid identity. */
public final class BlockLayerOverrides {
    public static final BlockLayerRouting<Block> ROUTING = new BlockLayerRouting<>();
    private BlockLayerOverrides() { }

    public static Map<Block, BlockMaterialMap.RenderLayer> resolve(BlockMaterialMap map) {
        var result = new HashMap<Block, BlockMaterialMap.RenderLayer>();
        int missing = 0;
        for (var entry : map.layers().entrySet()) {
            var block = BuiltInRegistries.BLOCK.get(Identifier.parse(entry.getKey()));
            if (block.isEmpty()) { missing++; continue; }
            result.put(block.get().value(), entry.getValue());
        }
        if (!map.layers().isEmpty()) OculusForge.LOGGER.info(
            "Resolved block layer overrides: {} blocks, {} absent selectors", result.size(), missing);
        return Map.copyOf(result);
    }

    public static void install(Map<Block, BlockMaterialMap.RenderLayer> layers) {
        RenderSystem.assertOnRenderThread();
        if (ROUTING.publish(layers, () -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.levelExtractor != null) minecraft.levelExtractor.allChanged();
        })) OculusForge.LOGGER.info("Oculus block layer routing changed: {} overrides; scheduled chunk rebuild", layers.size());
    }

    public static ChunkSectionLayer layer(ChunkSectionLayer vanilla) {
        var forced = ROUTING.forcedLayer();
        return forced == null ? vanilla : switch (forced) {
            case SOLID -> ChunkSectionLayer.SOLID;
            case CUTOUT -> ChunkSectionLayer.CUTOUT;
            case TRANSLUCENT -> ChunkSectionLayer.TRANSLUCENT;
        };
    }
}
