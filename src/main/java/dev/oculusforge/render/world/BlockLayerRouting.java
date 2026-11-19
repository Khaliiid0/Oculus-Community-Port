package dev.oculusforge.render.world;

import dev.oculusforge.render.program.BlockMaterialMap.RenderLayer;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/** Immutable publication; each section compilation pins one table, even across a pack switch. */
public final class BlockLayerRouting<K> {
    private volatile Map<K, RenderLayer> active = Map.of();
    private final ThreadLocal<Context<K>> compiling = new ThreadLocal<>();

    /** Called on the render thread. The callback schedules vanilla's geometry invalidation. */
    public boolean publish(Map<K, RenderLayer> requested, Runnable invalidate) {
        Map<K, RenderLayer> next = Map.copyOf(requested), previous = active;
        Objects.requireNonNull(invalidate);
        if (previous.equals(next)) return false;
        active = next;
        try { invalidate.run(); }
        catch (RuntimeException | Error failure) {
            active = previous;
            // A partially scheduled rebuild must also see the restored table.
            try { invalidate.run(); } catch (RuntimeException | Error cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
            throw failure;
        }
        return true;
    }

    public <T> T compile(Supplier<T> operation) {
        Context<K> previous = compiling.get();
        compiling.set(new Context<>(active));
        try { return operation.get(); }
        finally {
            if (previous == null) compiling.remove(); else compiling.set(previous);
        }
    }

    /** No per-block allocation. A finally block must restore the returned previous layer. */
    public RenderLayer enterBlock(K block) {
        Context<K> context = compiling.get();
        if (context == null) return null;
        RenderLayer previous = context.forced;
        context.forced = context.layers.get(block);
        return previous;
    }

    public void restoreBlock(RenderLayer previous) {
        Context<K> context = compiling.get();
        if (context != null) context.forced = previous;
    }

    public RenderLayer forcedLayer() {
        Context<K> context = compiling.get();
        return context == null ? null : context.forced;
    }

    private static final class Context<K> {
        private final Map<K, RenderLayer> layers;
        private RenderLayer forced;
        private Context(Map<K, RenderLayer> layers) { this.layers = layers; }
    }
}
