package dev.oculusforge.audit.mixin;

import dev.oculusforge.audit.DeferredFaultAudit;
import dev.oculusforge.render.pipeline.WorldDeferredPass;
import dev.oculusforge.render.program.PackShaderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=WorldDeferredPass.class,remap=false)
abstract class DeferredPassFaultMixin {
    @Inject(method="execute",at=@At("HEAD"),require=1,allow=1)
    private void audit$begin(PackShaderPipeline pipeline,PackShaderPipeline.Variant variant,LevelRenderState state,
                            RenderTarget main,CallbackInfo ci){DeferredFaultAudit.begin(pipeline.packId(),state.shouldUseMultiDrawIndirectForTerrain);}
    @Inject(method="execute",at=@At("RETURN"),require=1,allow=1)
    private void audit$end(CallbackInfo ci){DeferredFaultAudit.end();}
}
