package dev.oculusforge.audit.mixin;

import com.mojang.renderpearl.api.commands.RenderPass;
import dev.oculusforge.audit.DeferredFaultAudit;
import dev.oculusforge.render.pipeline.ShaderPipelineManager;
import dev.oculusforge.render.world.WorldRenderHooks;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=WorldRenderHooks.class,remap=false)
abstract class DeferredRecoveryAuditMixin {
    @Shadow private static volatile ShaderPipelineManager manager;
    @Inject(method="captureOpaqueDepthBeforeTransparency",at=@At("TAIL"),require=1,allow=1)
    private static void audit$retired(RenderPass pass,CallbackInfo ci){DeferredFaultAudit.afterBoundary(manager,pass);}
}
