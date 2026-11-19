package dev.oculusforge.render.world;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPassDescriptor;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import dev.oculusforge.render.program.ColorBufferFormat;
import dev.oculusforge.render.program.FullscreenColorTransfer;
import java.util.List;
import java.util.Optional;
import org.joml.Vector4fc;

/** Render-thread scope around LevelRenderer.render, always unwound by a finally block. */
public final class WorldSceneCapture {
    private static final ThreadLocal<Scope> CURRENT=new ThreadLocal<>();
    private WorldSceneCapture() { }

    private static final class Scope {
        final WorldRenderPipeline owner;
        final SceneColorBindings colors;
        final SceneFrameLease<WorldRenderPipeline> lifetime;
        Scope(WorldRenderPipeline owner, SceneColorBindings colors) {
            this.owner=owner;this.colors=colors;
            lifetime=new SceneFrameLease<>(owner);
        }
    }

    public static void begin(WorldRenderPipeline owner, RenderTarget original, GpuTexture color, GpuTextureView view, ColorBufferFormat format) {
        if (CURRENT.get() != null) throw new IllegalStateException("Nested HDR world scene capture");
        CURRENT.set(new Scope(owner,new SceneColorBindings(original,color,view,format)));
    }

    public static GpuTexture colorTexture(RenderTarget target,GpuTexture original) {
        var current=CURRENT.get();return current==null?original:current.colors.texture(target,original);
    }
    public static GpuTextureView colorView(RenderTarget target,GpuTextureView original) {
        var current=CURRENT.get();return current==null?original:current.colors.textureView(target,original);
    }
    public static boolean isMain(RenderTarget target) { var current=CURRENT.get();return current != null && current.colors.isMain(target); }
    public static boolean ownsTargets(WorldRenderPipeline owner) { var current=CURRENT.get();return current!=null&&current.owner==owner; }
    public static ColorBufferFormat format(RenderTarget target) {
        if (!isMain(target)) throw new IllegalStateException("Render target is not the active HDR scene");
        return CURRENT.get().colors.format();
    }

    public static RenderPipeline pipeline(RenderPipeline source,
        List<RenderPassDescriptor.Attachment<Optional<Vector4fc>>> attachments) {
        Scope scope=CURRENT.get();
        if (scope == null) return source;
        int mask=0;
        for (int i=0;i<attachments.size();i++) {
            var attachment=attachments.get(i);
            if (attachment != null && attachment.textureView().texture() == scope.colors.color()) mask |= 1 << i;
        }
        return ScenePipelineFormats.adapt(source,scope.colors.format(),mask);
    }

    /** A mid-frame pack failure must not free the framebuffer still used by vanilla passes. */
    public static boolean deferClose(WorldRenderPipeline owner) {
        var current=CURRENT.get();return current != null && current.lifetime.deferClose(owner);
    }

    public static void end() {
        Scope scope=java.util.Objects.requireNonNull(CURRENT.get(),"No HDR world scene to finish");
        // Restore original getters BEFORE resolving the destination or presenting to GUI/hand.
        CURRENT.remove();
        try {
            FullscreenColorTransfer.present(com.mojang.blaze3d.systems.RenderSystem.getDevice().createCommandEncoder(),
                scope.colors.view(),scope.colors.original().getColorTextureView());
        } finally {
            if (scope.lifetime.release()) scope.owner.close();
        }
    }
}
