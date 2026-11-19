package dev.oculusforge.audit.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.textures.GpuTexture;
import dev.oculusforge.audit.DeferredFaultAudit;
import dev.oculusforge.render.pipeline.DeferredTextureCopies;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=DeferredTextureCopies.class,remap=false)
abstract class DeferredCopyFaultMixin {
    @Inject(method="validate",at=@At("RETURN"),require=1,allow=1)
    private void audit$validated(CallbackInfo ci){DeferredFaultAudit.beforeCommit();}
    @WrapOperation(method="commit",at=@At(value="INVOKE",target="Lcom/mojang/renderpearl/api/commands/CommandEncoder;copyTextureToTexture(Lcom/mojang/renderpearl/api/textures/GpuTexture;Lcom/mojang/renderpearl/api/textures/GpuTexture;IIIIIII)V"),require=1,allow=1)
    private void audit$copy(CommandEncoder encoder,GpuTexture source,GpuTexture target,int mip,int sx,int sy,int dx,int dy,int width,int height,Operation<Void> original) {
        original.call(encoder,source,target,mip,sx,sy,dx,dy,width,height);DeferredFaultAudit.afterCopy();
    }
}
