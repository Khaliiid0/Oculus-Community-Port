package dev.oculusforge.mixin;

import dev.oculusforge.render.program.RenderAnimationClock;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GameRenderer.class, remap = false)
abstract class GameRendererMixin {
    @Inject(method = "extractCamera", at = @At("RETURN"))
    private void oculus$finishFogSnapshot(net.minecraft.client.DeltaTracker deltaTracker,
        float worldPartialTicks,float cameraEntityPartialTicks,CallbackInfo ci) {
        var renderer=(GameRenderer)(Object)this;
        var camera=renderer.gameRenderState().levelRenderState.cameraRenderState;
        dev.oculusforge.render.world.NativeFogData.finishFrame(
            (dev.oculusforge.render.world.WorldEnvironmentState)camera,camera,
            dev.oculusforge.render.world.SkyColor.fromArgb(renderer.mainCamera().attributeProbe().getValue(
                // World interpolation, not the potentially frozen camera entity's interpolation.
                net.minecraft.world.attribute.EnvironmentAttributes.SKY_COLOR,worldPartialTicks)));
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void oculus$beginAnimationFrame(CallbackInfo ci) { RenderAnimationClock.beginFrame(); }

    @Inject(method = "render", at = @At("RETURN"))
    private void oculus$endAnimationFrame(CallbackInfo ci) { RenderAnimationClock.endFrame(); }
}
