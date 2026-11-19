package dev.oculusforge.mixin;

import dev.oculusforge.render.world.WorldRenderHooks;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(value = LevelRenderer.class, remap = false)
abstract class LevelRendererMixin {
    @com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod(method="render")
    private void oculus$sceneScope(com.mojang.blaze3d.resource.GraphicsResourceAllocator allocator,
        net.minecraft.client.DeltaTracker deltaTracker,boolean outline,
        net.minecraft.client.renderer.state.level.CameraRenderState camera,org.joml.Matrix4fc modelView,
        com.mojang.blaze3d.buffers.GpuBufferSlice fog,org.joml.Vector4f fogColor,boolean sky,
        com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original) {
        boolean scene=WorldRenderHooks.beginSceneCapture();
        try { original.call(allocator,deltaTracker,outline,camera,modelView,fog,fogColor,sky); }
        catch(RuntimeException failure) {
            if(!scene)throw failure;
            WorldRenderHooks.failSceneCapture(failure);
            // Vanilla's prepared feature frame and model-view stack may not have unwound.
            // Do not hide the original failure and continue into an invalid hand-render frame.
            throw failure;
        } finally { if(scene)WorldRenderHooks.endSceneCapture(); }
    }
    @Shadow @Final private LevelRenderState levelRenderState;

    @ModifyArg(
        method = "render",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;addMainPass(Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lnet/minecraft/client/renderer/state/level/LevelRenderState;Lnet/minecraft/util/profiling/ProfilerFiller;Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;)V"),
        index = 5
    )
    private ChunkSectionsToRender oculus$prepareWorldFrame(ChunkSectionsToRender sections) {
        WorldRenderHooks.prepareFrame((LevelRenderer) (Object) this, sections, this.levelRenderState);
        return sections;
    }
}
