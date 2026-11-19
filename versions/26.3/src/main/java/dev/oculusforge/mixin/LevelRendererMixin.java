package dev.oculusforge.mixin;

import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.*;
import com.mojang.renderpearl.api.textures.*;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.oculusforge.render.world.WorldRenderHooks;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Supplier;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.*;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.level.*;
import org.joml.Vector4f;
import org.joml.Vector4fc;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LevelRenderer.class, remap = false)
abstract class LevelRendererMixin {
    @Shadow @Final private LevelRenderState levelRenderState;

    @WrapMethod(method="render")
    private void oculus$sceneScope(GraphicsResourceAllocator allocator, boolean outline,
        CameraRenderState camera, GpuBufferSlice fog, Vector4f fogColor, boolean sky, boolean consistentDepth,
        Operation<Void> original) {
        boolean scene = WorldRenderHooks.beginSceneCapture();
        try { original.call(allocator,outline,camera,fog,fogColor,sky,consistentDepth); }
        catch (RuntimeException failure) {
            if (scene) WorldRenderHooks.failSceneCapture(failure);
            throw failure;
        } finally { if (scene) WorldRenderHooks.endSceneCapture(); }
    }

    @ModifyArg(method="render", at=@At(value="INVOKE",
        target="Lnet/minecraft/client/renderer/LevelRenderer;addMainPass(Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;Z)V"),
        index=3, require=1, expect=1, allow=1)
    private ChunkSectionsToRender oculus$prepare(ChunkSectionsToRender sections) {
        WorldRenderHooks.prepareFrame((LevelRenderer)(Object)this,sections,levelRenderState);
        return sections;
    }

    // Pinned synthetic method: its single pass-creation site is verified against
    // the exact Forge 66.1.2 bytecode. Other world/GUI passes remain untouched.
    @Redirect(method="lambda$addMainPass$0", at=@At(value="INVOKE",
        target="Lcom/mojang/renderpearl/api/commands/CommandEncoder;createRenderPass(Ljava/util/function/Supplier;Lcom/mojang/renderpearl/api/textures/GpuTextureView;Ljava/util/Optional;Lcom/mojang/renderpearl/api/textures/GpuTextureView;Ljava/util/OptionalDouble;)Lcom/mojang/renderpearl/api/commands/RenderPass;"),
        require=1, expect=1, allow=1)
    private RenderPass oculus$mainPass(CommandEncoder encoder, Supplier<String> label,
        GpuTextureView color, Optional<Vector4fc> clearColor, GpuTextureView depth, OptionalDouble clearDepth) {
        return WorldRenderHooks.createMainPass(encoder,label,color,clearColor,depth,clearDepth);
    }

    @Redirect(method={"executeSolid","executeClassicTransparency"}, at=@At(value="INVOKE",
        target="Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;renderGroup(Lnet/minecraft/client/renderer/chunk/ChunkSectionLayerGroup;Lcom/mojang/renderpearl/api/commands/RenderPass;Lcom/mojang/renderpearl/api/textures/GpuSampler;Lcom/mojang/renderpearl/api/textures/GpuTextureView;Z)V"),
        require=2, expect=2, allow=2)
    private void oculus$terrain(ChunkSectionsToRender sections, ChunkSectionLayerGroup group,
        RenderPass pass, GpuSampler sampler, GpuTextureView atlas, boolean wireframe) {
        WorldRenderHooks.renderTerrainGroup(sections,group,pass,sampler,atlas,wireframe);
    }

    @Inject(method="executeClassicTransparency", at=@At("HEAD"), require=1)
    private void oculus$opaqueSnapshot(ChunkSectionsToRender sections, FeatureRenderDispatcher.PreparedFrame features,
        RenderPass pass, CallbackInfo callback) {
        WorldRenderHooks.captureOpaqueDepthBeforeTransparency(pass);
    }
}
