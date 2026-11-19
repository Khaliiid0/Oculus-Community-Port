package dev.oculusforge.mixin;

import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.commands.RenderPass;
import dev.oculusforge.render.world.WorldRenderHooks;
import net.minecraft.client.renderer.SkyRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = SkyRenderer.class, remap = false)
abstract class SkyRendererMixin {
    @Redirect(method = "renderEndSky", at = @At(value = "INVOKE",
        target = "Lcom/mojang/renderpearl/api/commands/RenderPass;setPipeline(Lcom/mojang/renderpearl/api/pipeline/CompiledRenderPipeline;)V"), require = 1, expect = 1, allow = 1)
    private void oculus$endSkyPipeline(RenderPass pass, CompiledRenderPipeline vanilla) {
        WorldRenderHooks.selectEndSkyPipeline(pass, vanilla);
    }
}
