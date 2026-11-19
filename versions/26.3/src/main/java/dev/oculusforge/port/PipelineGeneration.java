package dev.oculusforge.port;

import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.function.Supplier;

/** Render-thread confined, bounded ownership; identity keys match descriptor caches. */
public final class PipelineGeneration<K> implements AutoCloseable {
    private final IdentityHashMap<K, OwnedPipeline> pipelines = new IdentityHashMap<>();
    private final ArrayList<OwnedPipeline> acquisitionOrder = new ArrayList<>();
    private final int limit;
    private boolean closed;

    public PipelineGeneration(int limit) {
        if (limit < 1 || limit > 512) throw new IllegalArgumentException("Invalid pipeline generation limit");
        this.limit = limit;
    }

    public CompiledRenderPipeline acquire(K descriptor, Supplier<OwnedPipeline> factory) {
        requireOpen();
        Objects.requireNonNull(descriptor, "descriptor");
        var existing = pipelines.get(descriptor);
        if (existing != null) return existing.get();
        if (pipelines.size() >= limit) throw new IllegalStateException("Compiled pipeline generation budget exceeded");
        var owner = Objects.requireNonNull(factory.get(), "pipeline owner");
        try {
            var compiled = owner.get();
            pipelines.put(descriptor, owner);
            acquisitionOrder.add(owner);
            return compiled;
        } catch (RuntimeException | Error failure) {
            pipelines.remove(descriptor);
            try { owner.close(); } catch (RuntimeException | Error cleanup) {
                if (failure != cleanup) failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    public CompiledRenderPipeline get(K descriptor) {
        requireOpen();
        var owner = pipelines.get(Objects.requireNonNull(descriptor, "descriptor"));
        if (owner == null) throw new IllegalStateException("Pipeline does not belong to this compiled generation");
        return owner.get();
    }

    public int size() { return pipelines.size(); }

    @Override public void close() {
        if (closed) return;
        closed = true;
        var retired = acquisitionOrder.reversed().toArray(AutoCloseable[]::new);
        pipelines.clear();
        acquisitionOrder.clear();
        ResourceDisposal.closeAll(retired);
    }

    private void requireOpen() {
        if (closed) throw new IllegalStateException("Compiled pipeline generation is closed");
    }
}
