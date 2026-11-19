package dev.oculusforge.audit.mixin;

import dev.oculusforge.audit.DeferredFaultAudit;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets="dev.oculusforge.render.pipeline.FullscreenProgramRunner",remap=false)
abstract class FullscreenFaultMixin {
    @Inject(method="execute",at=@At("RETURN"),require=1,allow=1)
    private static void audit$completedDraw(CallbackInfo ci){DeferredFaultAudit.afterDraw();}
}
