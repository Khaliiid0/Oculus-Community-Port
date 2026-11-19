package dev.oculusforge.render.program;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import dev.oculusforge.OculusForge;

/** One owned upload per compiled pack generation; never rebuilt per frame. */
public final class PackGpuTexture implements AutoCloseable {
    private final GpuTexture texture;
    private final GpuTextureView view;
    private boolean closed;
    private final boolean blur;
    private final boolean clamp;
    private final long bytes;
    private static final java.util.concurrent.atomic.AtomicInteger LIVE = new java.util.concurrent.atomic.AtomicInteger();
    private static final java.util.concurrent.atomic.AtomicLong LIVE_BYTES = new java.util.concurrent.atomic.AtomicLong();

    private PackGpuTexture(GpuTexture texture, GpuTextureView view, boolean blur, boolean clamp, long bytes) {
        this.texture = texture; this.view = view; this.blur = blur; this.clamp = clamp; this.bytes = bytes;
        LIVE.incrementAndGet(); LIVE_BYTES.addAndGet(bytes);
    }

    public static PackGpuTexture create(NoiseTextureData data) {
        return upload(data.resolution(), data.resolution(), data::pixelAbgr, "generated noisetex", true, false);
    }

    public static PackGpuTexture create(dev.oculusforge.render.world.MaterialIdTable data) {
        return upload(dev.oculusforge.render.world.MaterialIdTable.WIDTH, data.height(), data::pixelAbgr,
            "block material lookup", false, true);
    }

    public static PackGpuTexture create(String name, PackImageData data) {
        return upload(data.width(), data.height(), data::pixelAbgr, name, data.blur(), data.clamp());
    }

    public static PackGpuTexture create(SpecularAtlasData data) {
        return create(data.atlas());
    }

    public static PackGpuTexture create(StaticMaterialAtlasData data) {
        RenderSystem.assertOnRenderThread();
        GpuTexture texture=null;GpuTextureView view=null;
        try {
            texture=RenderSystem.getDevice().createTexture(()->"Oculus static "+data.kind().label()+" atlas",
                GpuTexture.USAGE_COPY_DST|GpuTexture.USAGE_TEXTURE_BINDING,GpuFormat.RGBA8_UNORM,
                data.width(0),data.height(0),1,data.mipCount());
            var encoder=RenderSystem.getDevice().createCommandEncoder();
            for(int level=0;level<data.mipCount();level++) {
                try(NativeImage image=new NativeImage(data.width(level),data.height(level),false)) {
                    for(int y=0;y<data.height(level);y++)for(int x=0;x<data.width(level);x++)
                        image.setPixelABGR(x,y,data.pixelAbgr(level,y*data.width(level)+x));
                    encoder.writeToTexture(texture,image,level,0,0,0);
                }
            }
            view=RenderSystem.getDevice().createTextureView(texture);
            return new PackGpuTexture(texture,view,false,true,data.byteSize());
        }catch(RuntimeException|Error failure) {
            if(view!=null)try{view.close();}catch(RuntimeException cleanup){failure.addSuppressed(cleanup);}
            if(texture!=null)try{texture.close();}catch(RuntimeException cleanup){failure.addSuppressed(cleanup);}
            throw failure;
        }
    }

    private static PackGpuTexture upload(int width, int height, java.util.function.IntUnaryOperator pixels,
        String label, boolean blur, boolean clamp) {
        RenderSystem.assertOnRenderThread();
        GpuTexture texture = null;
        GpuTextureView view = null;
        try {
            try (NativeImage image = new NativeImage(width, height, false)) {
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) image.setPixelABGR(x, y, pixels.applyAsInt(y * width + x));
            }
            texture = RenderSystem.getDevice().createTexture(
                () -> "Oculus " + label, GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                GpuFormat.RGBA8_UNORM, width, height, 1, 1);
            RenderSystem.getDevice().createCommandEncoder().writeToTexture(texture, image);
            view = RenderSystem.getDevice().createTextureView(texture);
            }
            long bytes = (long) width * height * 4;
            OculusForge.LOGGER.info("Uploaded pack texture '{}': {}x{} ({} bytes)", label, width, height, bytes);
            return new PackGpuTexture(texture, view, blur, clamp, bytes);
        } catch (RuntimeException | Error exception) {
            // Also close the texture if view creation or upload fails midway through activation.
            if (view != null) {
                try { view.close(); } catch (RuntimeException cleanup) { exception.addSuppressed(cleanup); }
            }
            if (texture != null) {
                try { texture.close(); } catch (RuntimeException cleanup) { exception.addSuppressed(cleanup); }
            }
            throw exception;
        }
    }

    public GpuTextureView view() {
        if (closed) throw new IllegalStateException("The pack texture has been closed");
        return view;
    }

    public com.mojang.renderpearl.api.textures.GpuSampler sampler() {
        var filter = blur ? com.mojang.renderpearl.api.textures.FilterMode.LINEAR : com.mojang.renderpearl.api.textures.FilterMode.NEAREST;
        return clamp ? RenderSystem.getSamplerCache().getClampToEdge(filter) : RenderSystem.getSamplerCache().getRepeat(filter);
    }

    /** Owned pack textures only, not all Minecraft/driver allocations. */
    public static int liveCount() { return LIVE.get(); }
    public static long liveBytes() { return LIVE_BYTES.get(); }

    @Override
    public void close() {
        RenderSystem.assertOnRenderThread();
        if (closed) return;
        closed = true;
        try { view.close(); } finally {
            texture.close();
            LIVE.decrementAndGet(); LIVE_BYTES.addAndGet(-bytes);
        }
    }
}
