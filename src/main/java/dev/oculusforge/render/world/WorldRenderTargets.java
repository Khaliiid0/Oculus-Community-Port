package dev.oculusforge.render.world;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import dev.oculusforge.OculusForge;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** Sparse, owned render attachments for one size/gbuffer layout. */
public final class WorldRenderTargets implements AutoCloseable {
    private static final org.joml.Vector4fc EMPTY_SHADOW_COLOR = new org.joml.Vector4f(0);
    private static final AtomicInteger LIVE_TEXTURES = new AtomicInteger();
    private static final AtomicLong LIVE_BYTES = new AtomicLong();
    private final WorldTargetLayout layout;
    private final Map<Integer, GpuTexture> colorTextures = new LinkedHashMap<>();
    private final Map<Integer, GpuTextureView> colorViews = new LinkedHashMap<>();
    private final Map<Integer, org.joml.Vector4fc> clearColors = new LinkedHashMap<>();
    private final Map<GpuTexture, Long> ownedTextures = new LinkedHashMap<>();
    private final Map<Integer, GpuTexture> shadowColors = new LinkedHashMap<>();
    private final Map<Integer, GpuTextureView> shadowColorViews = new LinkedHashMap<>();
    private GpuTexture shadowDepth;
    private GpuTextureView shadowDepthView;
    private GpuTexture opaqueShadowDepth;
    private GpuTextureView opaqueShadowDepthView;
    private boolean opaqueShadowDepthReady;
    private GpuTexture opaqueWorldDepth;
    private GpuTextureView opaqueWorldDepthView;
    private boolean opaqueWorldDepthReady;
    private boolean closed;
    private boolean initialized;

    private WorldRenderTargets(WorldTargetLayout layout) {
        this.layout = layout;
        for (int index : layout.auxiliaryBuffers()) {
            clearColors.put(index, layout.formats().clearValue(index).vector(layout.formats().get(index)));
        }
    }

    public static WorldRenderTargets create(WorldTargetLayout layout) {
        RenderSystem.assertOnRenderThread();
        WorldRenderTargets result = new WorldRenderTargets(layout);
        try {
            for (int colorIndex : layout.auxiliaryBuffers().stream().sorted().toList()) {
                GpuTexture texture = result.allocate("Oculus colortex" + colorIndex,
                    layout.formats().get(colorIndex).gpu(), layout.width(), layout.height());
                result.colorTextures.put(colorIndex, texture);
                result.colorViews.put(colorIndex, RenderSystem.getDevice().createTextureView(texture));
            }
            if(layout.sceneColor()) {
                var texture=result.allocate("Oculus HDR world colortex0",layout.formats().get(0).gpu(),layout.width(),layout.height());
                result.colorTextures.put(0,texture);
                result.colorViews.put(0,RenderSystem.getDevice().createTextureView(texture));
            }
            if (layout.opaqueWorldDepth()) {
                result.opaqueWorldDepth = result.allocate("Oculus depthtex1", GpuFormat.D32_FLOAT, layout.width(), layout.height());
                result.opaqueWorldDepthView = RenderSystem.getDevice().createTextureView(result.opaqueWorldDepth);
            }
            if (layout.shadowResolution() > 0) {
                int resolution = layout.shadowResolution();
                for (int index : layout.shadowColors().stream().sorted().toList()) {
                    GpuTexture texture = result.allocate("Oculus shadowcolor" + index, GpuFormat.RGBA8_UNORM, resolution, resolution);
                    result.shadowColors.put(index, texture);
                    result.shadowColorViews.put(index, RenderSystem.getDevice().createTextureView(texture));
                }
                result.shadowDepth = result.allocate("Oculus shadowtex0", GpuFormat.D32_FLOAT, resolution, resolution);
                result.shadowDepthView = RenderSystem.getDevice().createTextureView(result.shadowDepth);
                result.opaqueShadowDepth = result.allocate("Oculus shadowtex1", GpuFormat.D32_FLOAT, resolution, resolution);
                result.opaqueShadowDepthView = RenderSystem.getDevice().createTextureView(result.opaqueShadowDepth);
            }
            OculusForge.LOGGER.info("Oculus world targets: {} auxiliary buffers {}, shadow {}x{}, {} textures / {} bytes, scene={}",
                layout.auxiliaryBuffers().size(), layout.auxiliaryBuffers().stream().sorted().toList(),
                layout.shadowResolution(), layout.shadowResolution(), layout.textureCount(), layout.byteSize(),
                layout.sceneColor()?layout.formats().get(0):"borrowed RGBA8");
            return result;
        } catch (RuntimeException | Error failure) {
            try { result.close(); } catch (RuntimeException cleanupFailure) { failure.addSuppressed(cleanupFailure); }
            throw failure;
        }
    }

    private GpuTexture allocate(String label, GpuFormat format, int width, int height) {
        GpuTexture texture = RenderSystem.getDevice().createTexture(() -> label,
            GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_COPY_SRC | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT,
            format, width, height, 1, 1);
        long bytes = Math.multiplyExact(Math.multiplyExact((long) width, height), format.blockSize());
        ownedTextures.put(texture, bytes);
        LIVE_TEXTURES.incrementAndGet();
        LIVE_BYTES.addAndGet(bytes);
        return texture;
    }

    public boolean matches(WorldTargetLayout requested) { return !closed && layout.equals(requested); }
    public void beginFrame() {
        RenderSystem.assertOnRenderThread();
        if (closed) throw new IllegalStateException("World targets are closed");
        var encoder = RenderSystem.getDevice().createCommandEncoder();
        // Empty shadow chunk lists may never open a render pass. Never expose last frame's
        // shadow colors or depth to terrain in that case. Minecraft uses reverse depth:
        // zero is the empty/far value (converted to legacy depth one by the adapters).
        opaqueShadowDepthReady = false;
        opaqueWorldDepthReady = false;
        if (shadowDepth != null) {
            for (GpuTexture shadowColor : shadowColors.values())
                encoder.clearColorTexture(shadowColor, EMPTY_SHADOW_COLOR);
            encoder.clearDepthTexture(shadowDepth, 0.0);
            encoder.clearDepthTexture(opaqueShadowDepth, 0.0);
        }
        for (var entry : colorTextures.entrySet()) {
            if(entry.getKey()==0)continue; // LevelRenderer's clear pass supplies the real scene fog clear.
            if (!initialized || layout.formats().shouldClear(entry.getKey())) {
                encoder.clearColorTexture(entry.getValue(), clearColors.get(entry.getKey()));
            }
        }
        initialized = true;
    }
    public boolean matches(int width, int height, java.util.Set<Integer> buffers, int shadowResolution,
                           dev.oculusforge.render.program.ColorBufferFormats formats) {
        return matches(new WorldTargetLayout(width, height, buffers, shadowResolution, formats));
    }
    public GpuTexture colorTexture(int index) { checkColorIndex(index); return colorTextures.get(index); }
    public GpuTextureView colorView(int index) { checkColorIndex(index); return colorViews.get(index); }
    public GpuTexture shadowColor() { return shadowColor(0); }
    public GpuTextureView shadowColorView() { return shadowColorView(0); }
    public GpuTexture shadowColor(int index) { return requireShadow(shadowColors.get(index)); }
    public GpuTextureView shadowColorView(int index) { return requireShadow(shadowColorViews.get(index)); }
    public GpuTexture shadowDepth() { return requireShadow(shadowDepth); }
    public GpuTextureView shadowDepthView() { return requireShadow(shadowDepthView); }

    /** Copy only between passes: never sample the depth attachment being written. */
    public void snapshotOpaqueWorldDepth(GpuTexture source) {
        RenderSystem.assertOnRenderThread();
        if (closed || opaqueWorldDepth == null || opaqueWorldDepthReady)
            throw new IllegalStateException("Opaque world depth is unavailable or already captured");
        if (source == null || source.getFormat() != GpuFormat.D32_FLOAT
            || source.getWidth(0) != layout.width() || source.getHeight(0) != layout.height())
            throw new IllegalStateException("Opaque world depth source does not match the viewport");
        RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(
            source, opaqueWorldDepth, 0, 0, 0, 0, 0, layout.width(), layout.height());
        opaqueWorldDepthReady = true;
    }

    public GpuTextureView opaqueWorldDepthView() {
        if (closed || !opaqueWorldDepthReady || opaqueWorldDepthView == null)
            throw new IllegalStateException("Opaque world depth has not been captured this frame");
        return opaqueWorldDepthView;
    }

    /** Must run after the opaque shadow pass closes, before any transparent shadow draw. */
    public void snapshotOpaqueShadowDepth() {
        RenderSystem.assertOnRenderThread();
        GpuTexture source = requireShadow(shadowDepth);
        GpuTexture destination = requireShadow(opaqueShadowDepth);
        if (opaqueShadowDepthReady) throw new IllegalStateException("Opaque shadow depth was already captured this frame");
        int resolution = layout.shadowResolution();
        RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(
            source, destination, 0, 0, 0, 0, 0, resolution, resolution);
        opaqueShadowDepthReady = true;
    }

    public GpuTextureView shadowDepthView(int index) {
        if (index == 0) return shadowDepthView();
        if (index != 1) throw new IllegalArgumentException("Unavailable shadowtex" + index);
        GpuTextureView view = requireShadow(opaqueShadowDepthView);
        if (!opaqueShadowDepthReady) throw new IllegalStateException("Opaque shadow depth has not been captured this frame");
        return view;
    }

    /** Oculus-owned world textures only; excludes Minecraft targets and driver allocations. */
    public static int liveCount() { return LIVE_TEXTURES.get(); }
    public static long liveBytes() { return LIVE_BYTES.get(); }

    private void checkColorIndex(int index) {
        if (closed || !colorTextures.containsKey(index)) throw new IllegalArgumentException("Unavailable colortex" + index);
    }

    private <T> T requireShadow(T value) {
        if (closed || value == null) throw new IllegalStateException("Shadow targets are unavailable");
        return value;
    }

    @Override
    public void close() {
        RenderSystem.assertOnRenderThread();
        if (closed) return;
        closed = true;
        RuntimeException failure = null;
        for (GpuTextureView view : colorViews.values()) failure = close(view, failure);
        for (GpuTextureView view : shadowColorViews.values()) failure = close(view, failure);
        failure = close(shadowDepthView, failure);
        failure = close(opaqueShadowDepthView, failure);
        failure = close(opaqueWorldDepthView, failure);
        for (var entry : ownedTextures.entrySet()) {
            try {
                entry.getKey().close();
                LIVE_TEXTURES.decrementAndGet();
                LIVE_BYTES.addAndGet(-entry.getValue());
            } catch (RuntimeException exception) {
                failure = accumulate(failure, exception);
            }
        }
        if (failure != null) throw failure;
    }

    private static RuntimeException close(AutoCloseable value, RuntimeException failure) {
        if (value == null) return failure;
        try { value.close(); }
        catch (Exception exception) {
            return accumulate(failure, exception instanceof RuntimeException runtime ? runtime
                : new IllegalStateException("Could not release a world-render target", exception));
        }
        return failure;
    }

    private static RuntimeException accumulate(RuntimeException failure, RuntimeException exception) {
        if (failure == null) return exception;
        failure.addSuppressed(exception);
        return failure;
    }
}
