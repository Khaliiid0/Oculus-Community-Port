package dev.oculusforge.mixin;

import com.mojang.renderpearl.backend.opengl.GlStateManager;
import dev.oculusforge.render.program.TextureBindingCapacity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Extend the existing cache, retaining vanilla binding/deletion invalidation behavior. */
@Mixin(value = GlStateManager.class, remap = false)
abstract class GlStateManagerMixin {
    @ModifyConstant(method = "<clinit>", constant = @Constant(intValue = 12), require = 1, expect = 1, allow = 1)
    private static int oculus$textureTrackingCapacity(int original) {
        return TextureBindingCapacity.extendTracking(original);
    }
}
