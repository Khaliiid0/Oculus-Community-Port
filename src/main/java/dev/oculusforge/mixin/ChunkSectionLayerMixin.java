package dev.oculusforge.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.oculusforge.render.world.ExtendedTerrainMesh;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ChunkSectionLayer.class, remap = false)
abstract class ChunkSectionLayerMixin {
    @Shadow @Final @Mutable private RenderPipeline pipeline;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void oculus$fixedChunkLayout(CallbackInfo callback) {
        pipeline = ExtendedTerrainMesh.pipeline(pipeline);
    }
}
