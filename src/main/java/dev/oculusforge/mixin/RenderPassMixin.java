package dev.oculusforge.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderPassDescriptor;
import java.util.List;
import java.util.Optional;
import org.joml.Vector4fc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value=RenderPass.class,remap=false)
abstract class RenderPassMixin {
    @Shadow @Final private List<RenderPassDescriptor.Attachment<Optional<Vector4fc>>> colorAttachments;
    @ModifyVariable(method="setPipeline",at=@At("HEAD"),argsOnly=true)
    private RenderPipeline oculus$sceneFormat(RenderPipeline pipeline) {
        return dev.oculusforge.render.world.WorldSceneCapture.pipeline(pipeline,colorAttachments);
    }
}
