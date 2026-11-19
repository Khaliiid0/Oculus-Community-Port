package dev.oculusforge.mixin;

import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.frontend.FrontendRenderPass;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import java.util.List;
import java.util.Optional;
import org.joml.Vector4fc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value=FrontendRenderPass.class,remap=false)
abstract class RenderPassMixin {
    @Shadow @Final private List<RenderPassDescriptor.Attachment<Optional<Vector4fc>>> colorAttachments;
    @ModifyVariable(method="setPipeline",at=@At("HEAD"),argsOnly=true,ordinal=0,require=1,expect=1,allow=1)
    private CompiledRenderPipeline oculus$sceneFormat(CompiledRenderPipeline pipeline) {
        return dev.oculusforge.render.world.WorldSceneCapture.pipeline(pipeline,colorAttachments);
    }
}
