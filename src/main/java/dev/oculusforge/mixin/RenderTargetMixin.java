package dev.oculusforge.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import dev.oculusforge.render.world.WorldSceneCapture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keep cached vanilla target identities stable; resolve the current frame's color only on access. */
@Mixin(value=RenderTarget.class,remap=false)
abstract class RenderTargetMixin {
    @Inject(method="getColorTexture",at=@At("RETURN"),cancellable=true)
    private void oculus$sceneColor(CallbackInfoReturnable<GpuTexture> callback) {
        callback.setReturnValue(WorldSceneCapture.colorTexture((RenderTarget)(Object)this,callback.getReturnValue()));
    }
    @Inject(method="getColorTextureView",at=@At("RETURN"),cancellable=true)
    private void oculus$sceneView(CallbackInfoReturnable<GpuTextureView> callback) {
        callback.setReturnValue(WorldSceneCapture.colorView((RenderTarget)(Object)this,callback.getReturnValue()));
    }
}
