package dev.oculusforge.port;

import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.ShaderSource;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/** One generation owns one compiled pipeline; descriptors are not GPU owners. */
public final class OwnedPipeline implements AutoCloseable {
    private final CompiledRenderPipeline pipeline;
    private final AtomicBoolean closed = new AtomicBoolean();

    private OwnedPipeline(CompiledRenderPipeline pipeline) { this.pipeline = pipeline; }

    /**
     * Must run on the render thread. The supplied compiler may do frontend work
     * asynchronously, but finishCompile runs on this caller before source disposal.
     * No cancellation/timeout may abandon pending native compiler resources.
     */
    public static OwnedPipeline compile(ShaderSource source,
        Function<ShaderSource, CompletableFuture<CompiledRenderPipeline.Pending>> compiler) {
        Objects.requireNonNull(source, "source");
        CompiledRenderPipeline compiled = null;
        try {
            try (source) {
                var pending = Objects.requireNonNull(
                    Objects.requireNonNull(compiler, "compiler").apply(source), "compile future").join();
                compiled = Objects.requireNonNull(pending, "pending pipeline").finishCompile();
                if (compiled == null) throw new IllegalStateException("Shader compiler rejected the pipeline; see latest.log");
                if (compiled.isClosed()) throw new IllegalStateException("Shader compiler returned a closed pipeline");
            }
            return new OwnedPipeline(compiled);
        } catch (RuntimeException | Error failure) {
            if (compiled != null) {
                try { if (!compiled.isClosed()) compiled.close(); }
                catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
            }
            throw failure;
        }
    }

    public CompiledRenderPipeline get() {
        if (closed.get()) throw new IllegalStateException("Compiled pipeline owner is closed");
        return pipeline;
    }

    @Override public void close() {
        if (closed.compareAndSet(false, true)) pipeline.close();
    }
}
