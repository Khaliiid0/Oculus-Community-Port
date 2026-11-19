package dev.oculusforge.render.world;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import dev.oculusforge.render.program.ColorBufferFormat;
import java.util.Objects;

/** Frame-local color binding, never a replacement RenderTarget that a vanilla renderer could cache. */
record SceneColorBindings(RenderTarget original, GpuTexture color, GpuTextureView view, ColorBufferFormat format) {
    SceneColorBindings {
        Objects.requireNonNull(original);Objects.requireNonNull(color);Objects.requireNonNull(view);Objects.requireNonNull(format);
        if(color.isClosed() || view.isClosed() || view.texture()!=color || view.baseMipLevel()!=0 || view.mipLevels()!=1
            || color==original.getColorTexture() || color.getFormat()!=format.gpu()
            || original.width!=view.getWidth(0) || original.height!=view.getHeight(0))
            throw new IllegalArgumentException("HDR scene color must be distinct, open, correctly formatted and match the viewport");
        Objects.requireNonNull(original.getDepthTexture(),"Scene borrows Minecraft depth");
        Objects.requireNonNull(original.getDepthTextureView(),"Scene borrows Minecraft depth view");
    }
    boolean isMain(RenderTarget candidate) { return candidate==original; }
    GpuTexture texture(RenderTarget candidate,GpuTexture fallback) { return isMain(candidate)?color:fallback; }
    GpuTextureView textureView(RenderTarget candidate,GpuTextureView fallback) { return isMain(candidate)?view:fallback; }
}
