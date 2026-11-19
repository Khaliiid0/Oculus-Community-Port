package dev.oculusforge.mixin;

import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.*;
import dev.oculusforge.render.world.WorldRenderHooks;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Only pipeline selection changes; vanilla keeps buffers, offsets, ordering and draw limits. */
@Mixin(value = {ChunkSectionsToRender.DrawSeparate.class, ChunkSectionsToRender.DrawIndirect.class}, remap = false)
abstract class TerrainBackendMixin {
    @Redirect(method = "render", at = @At(value = "INVOKE",
        target = "Lcom/mojang/renderpearl/api/commands/RenderPass;setPipeline(Lcom/mojang/renderpearl/api/pipeline/CompiledRenderPipeline;)V"),
        require = 1, expect = 1, allow = 1)
    private void oculus$select(RenderPass pass, CompiledRenderPipeline vanilla,
        ChunkSectionLayer layer, RenderPass originalPass, GpuBuffer indices, IndexType indexType,
        RenderPipeline override, RenderPipeline indirectOverride) {
        if (!WorldRenderHooks.terrainActive()) { pass.setPipeline(vanilla); return; }
        boolean indirect = (Object) this instanceof ChunkSectionsToRender.DrawIndirect;
        WorldRenderHooks.selectTerrainPipeline(pass, layer.pipeline(indirect),indirect);
    }
}
