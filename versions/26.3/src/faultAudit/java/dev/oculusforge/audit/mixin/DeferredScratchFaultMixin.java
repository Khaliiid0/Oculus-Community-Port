package dev.oculusforge.audit.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.mojang.renderpearl.api.textures.*;
import dev.oculusforge.audit.DeferredFaultAudit;
import dev.oculusforge.render.pipeline.DeferredScratchTargets;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value=DeferredScratchTargets.class,remap=false)
abstract class DeferredScratchFaultMixin {
    @WrapOperation(method="ensure",at=@At(value="INVOKE",target="Ldev/oculusforge/render/pipeline/DeferredScratchTargets$Factory;view(Lcom/mojang/renderpearl/api/textures/GpuTexture;)Lcom/mojang/renderpearl/api/textures/GpuTextureView;"),require=1,allow=1)
    private GpuTextureView audit$view(DeferredScratchTargets.Factory factory,GpuTexture texture,Operation<GpuTextureView> original) {
        DeferredFaultAudit.beforeView();return original.call(factory,texture);
    }
}
