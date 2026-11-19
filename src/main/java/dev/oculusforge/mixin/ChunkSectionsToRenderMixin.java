package dev.oculusforge.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import dev.oculusforge.render.world.WorldRenderHooks;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Supplier;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import org.joml.Vector4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ChunkSectionsToRender.class, remap = false)
abstract class ChunkSectionsToRenderMixin {
    @Inject(method = "renderGroup", at = @At("HEAD"), cancellable = true)
    private void oculus$begin(ChunkSectionLayerGroup group, GpuSampler sampler, CallbackInfo callback) {
        WorldRenderHooks.beginTerrainGroup((ChunkSectionsToRender) (Object) this, group, sampler);
        if (WorldRenderHooks.renderSplitOpaqueGroup((ChunkSectionsToRender) (Object) this, group, sampler)) {
            callback.cancel();
        }
    }

    @Inject(method = "renderGroup", at = @At("RETURN"))
    private void oculus$end(ChunkSectionLayerGroup group, GpuSampler sampler, CallbackInfo callback) {
        WorldRenderHooks.endTerrainGroup();
    }

    @Redirect(
        method = "renderGroup",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/CommandEncoder;createRenderPass(Ljava/util/function/Supplier;Lcom/mojang/blaze3d/textures/GpuTextureView;Ljava/util/Optional;Lcom/mojang/blaze3d/textures/GpuTextureView;Ljava/util/OptionalDouble;)Lcom/mojang/blaze3d/systems/RenderPass;")
    )
    private RenderPass oculus$attachments(CommandEncoder encoder, Supplier<String> label, GpuTextureView color,
                                          Optional<Vector4fc> clearColor, GpuTextureView depth, OptionalDouble clearDepth) {
        return WorldRenderHooks.createTerrainPass(encoder, label, color, clearColor, depth, clearDepth);
    }

    @Redirect(
        method = "renderGroup",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderPass;setPipeline(Lcom/mojang/blaze3d/pipeline/RenderPipeline;)V")
    )
    private void oculus$pipeline(RenderPass pass, RenderPipeline vanilla) {
        WorldRenderHooks.selectTerrainPipeline(pass, vanilla);
    }
}
