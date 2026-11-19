package dev.oculusforge.mixin;

import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Reuse vanilla's dispatch: both separate and indirect draws retain their own data. */
@Mixin(value = ChunkSectionsToRender.class, remap = false)
public interface ChunkSectionsToRenderMixin {
    @Invoker("renderLayers")
    void oculus$renderLayers(ChunkSectionLayer[] layers, GpuSampler sampler, RenderPass pass,
        GpuTextureView atlas, GpuTextureView lightmap, RenderPipeline override, RenderPipeline indirectOverride);
}
