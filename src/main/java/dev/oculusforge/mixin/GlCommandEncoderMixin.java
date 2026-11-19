package dev.oculusforge.mixin;

import org.lwjgl.opengl.GL30C;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Keep the encoder's pipeline-change cache, but never let one MRT toggle every attachment. */
@Mixin(targets = "com.mojang.blaze3d.opengl.GlCommandEncoder", remap = false)
abstract class GlCommandEncoderMixin {
    @Redirect(method = "applyPipelineState", at = @At(value = "INVOKE",
        target = "Lcom/mojang/blaze3d/opengl/GlStateManager;_enableBlend(I)V"), require = 1, expect = 1, allow = 1)
    private void oculus$enableBlend(int index) { GL30C.glEnablei(GL30C.GL_BLEND, index); }

    @Redirect(method = "applyPipelineState", at = @At(value = "INVOKE",
        target = "Lcom/mojang/blaze3d/opengl/GlStateManager;_disableBlend(I)V"), require = 1, expect = 1, allow = 1)
    private void oculus$disableBlend(int index) { GL30C.glDisablei(GL30C.GL_BLEND, index); }

}
