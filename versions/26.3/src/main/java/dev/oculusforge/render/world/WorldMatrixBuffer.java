package dev.oculusforge.render.world;

import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.GpuFence;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.oculusforge.render.program.NativeWorldBindings;
import java.util.concurrent.atomic.AtomicInteger;

/** Three bounded frame slots, shared by all native terrain/shadow draws in one pack. */
public final class WorldMatrixBuffer implements AutoCloseable {
    private static final AtomicInteger LIVE = new AtomicInteger();
    private final GpuBuffer[] buffers = new GpuBuffer[3];
    private final GpuFence[] fences = new GpuFence[3];
    private int current;
    private boolean populated, closed, counted;

    public WorldMatrixBuffer() {
        RenderSystem.assertOnRenderThread();
        try {
            for (int i = 0; i < buffers.length; i++) {
                int slot = i;
                buffers[i] = RenderSystem.getDevice().createBuffer(() -> "Oculus world matrices #" + slot,
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE, NativeWorldBindings.BYTE_SIZE);
            }
            counted = true;
            LIVE.incrementAndGet();
        } catch (RuntimeException | Error failure) {
            try { close(); } catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    public static int liveCount() { return LIVE.get(); }
    public void update(WorldMatrixData data) {
        RenderSystem.assertOnRenderThread();
        if (closed) throw new IllegalStateException("Closed world matrix buffer");
        // Called before this frame's graph is built, after previous-frame draws were submitted.
        if (populated) {
            fences[current] = RenderSystem.getDevice().createCommandEncoder().createFence();
            current = (current + 1) % buffers.length;
        }
        if (fences[current] != null) {
            fences[current].awaitCompletion(Long.MAX_VALUE);
            fences[current].close();
            fences[current] = null;
        }
        populated = false;
        try (var mapped = buffers[current].map(false, true)) { data.write(mapped.data()); }
        populated = true;
    }

    public void bind(RenderPass pass) {
        if (closed || !populated) throw new IllegalStateException("World matrices have not been prepared for drawing");
        pass.setUniform(NativeWorldBindings.BLOCK_NAME, buffers[current]);
    }

    @Override public void close() {
        RenderSystem.assertOnRenderThread();
        if (closed) return;
        closed = true;
        RuntimeException failure = null;
        for (int i = 0; i < buffers.length; i++) {
            try { if (fences[i] != null) fences[i].close(); }
            catch (RuntimeException e) { if (failure == null) failure = e; else failure.addSuppressed(e); }
            try { if (buffers[i] != null) buffers[i].close(); }
            catch (RuntimeException e) { if (failure == null) failure = e; else failure.addSuppressed(e); }
        }
        if (counted) { LIVE.decrementAndGet(); counted = false; }
        if (failure != null) throw failure;
    }
}
