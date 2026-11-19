package dev.oculusforge.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import dev.oculusforge.render.world.WorldRenderHooks;
import net.minecraft.client.renderer.SkyRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = SkyRenderer.class, remap = false)
abstract class SkyRendererMixin {
    @Redirect(method = "renderEndSky", at = @At(value = "INVOKE",
        target = "Lcom/mojang/blaze3d/systems/RenderPass;setPipeline(Lcom/mojang/blaze3d/pipeline/RenderPipeline;)V"))
    private void oculus$endSkyPipeline(RenderPass pass, RenderPipeline vanilla) {
        WorldRenderHooks.selectEndSkyPipeline(pass, vanilla);
    }
}
