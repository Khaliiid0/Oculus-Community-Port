package dev.oculusforge.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.oculusforge.render.world.SceneCompiledPipelines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value=RenderSystem.class,remap=false)
abstract class RenderSystemMixin {
    @Inject(method="getCompiledPipeline",at=@At("RETURN"),require=1,expect=1,allow=1)
    private static void oculus$remember(RenderPipeline source, CallbackInfoReturnable<CompiledRenderPipeline> callback) {
        SceneCompiledPipelines.remember(source,callback.getReturnValue());
    }

    @Inject(method="getCompiledPipelineNullable",at=@At("RETURN"),require=2,expect=2,allow=2)
    private static void oculus$rememberNullable(RenderPipeline source, CallbackInfoReturnable<CompiledRenderPipeline> callback) {
        SceneCompiledPipelines.remember(source,callback.getReturnValue());
    }
}
