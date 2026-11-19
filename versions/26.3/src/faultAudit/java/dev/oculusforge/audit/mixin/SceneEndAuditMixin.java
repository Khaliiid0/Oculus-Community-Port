package dev.oculusforge.audit.mixin;

import dev.oculusforge.audit.DeferredFaultAudit;
import dev.oculusforge.render.world.WorldSceneCapture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=WorldSceneCapture.class,remap=false)
abstract class SceneEndAuditMixin {
    @Inject(method="end",at=@At("RETURN"),require=1,allow=1)
    private static void audit$released(CallbackInfo ci){DeferredFaultAudit.afterScene();}
}
