package dev.oculusforge.port;

import static org.junit.jupiter.api.Assertions.*;
import com.mojang.renderpearl.api.pipeline.*;
import java.util.concurrent.*;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

class OwnedPipelineTest {
    static final class Source implements ShaderSource {
        int closes;
        boolean failClose;
        @Override public String getShader(Identifier id, ShaderType type) { return "source"; }
        @Override public CachedIncludeSource getInclude(Identifier id) { return null; }
        @Override public void close() {
            closes++;
            if (failClose) throw new IllegalStateException("source cleanup");
        }
    }
    static final class Pipeline implements CompiledRenderPipeline {
        int closes;
        boolean failClose;
        @Override public boolean isClosed() { return closes != 0; }
        @Override public void close() {
            closes++;
            if (failClose) throw new IllegalStateException("pipeline cleanup");
        }
    }
    private static CompletableFuture<CompiledRenderPipeline.Pending> ready(Pipeline pipeline) {
        return CompletableFuture.completedFuture(() -> pipeline);
    }

    @Test void successTransfersOwnershipAndClosesSourceOnly() {
        var source = new Source(); var pipeline = new Pipeline();
        var owner = OwnedPipeline.compile(source, s -> ready(pipeline));
        assertEquals(1, source.closes); assertEquals(0, pipeline.closes);
        assertSame(pipeline, owner.get()); owner.close(); owner.close();
        assertEquals(1, pipeline.closes);
        assertThrows(IllegalStateException.class, owner::get);
    }

    @Test void sourceRemainsAliveUntilFinishOnCallingThread() throws Exception {
        var source = new Source(); var pipeline = new Pipeline();
        var future = new CompletableFuture<CompiledRenderPipeline.Pending>();
        var started = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var result = executor.submit(() -> {
                Thread caller = Thread.currentThread();
                return OwnedPipeline.compile(source, s -> {
                    started.countDown();
                    return future.thenApply(pending -> () -> {
                        assertSame(caller, Thread.currentThread());
                        assertEquals(0, source.closes);
                        return pending.finishCompile();
                    });
                });
            });
            try {
                assertTrue(started.await(5, TimeUnit.SECONDS));
                assertEquals(0, source.closes);
                assertFalse(result.isDone());
            } finally { future.complete(() -> pipeline); }
            try (var owner = result.get(5, TimeUnit.SECONDS)) { assertSame(pipeline, owner.get()); }
        }
        assertEquals(1, source.closes); assertEquals(1, pipeline.closes);
    }

    @Test void synchronousCompilerFailureStillClosesSource() {
        var source = new Source(); var failure = new IllegalStateException("compile");
        assertSame(failure, assertThrows(IllegalStateException.class,
            () -> OwnedPipeline.compile(source, s -> { throw failure; })));
        assertEquals(1, source.closes);
    }

    @Test void asynchronousCompilerFailureStillClosesSource() {
        var source = new Source(); var failure = new IllegalStateException("async");
        var thrown = assertThrows(CompletionException.class,
            () -> OwnedPipeline.compile(source, s -> CompletableFuture.failedFuture(failure)));
        assertSame(failure, thrown.getCause()); assertEquals(1, source.closes);
    }

    @Test void nullCompiledResultRejectsRatherThanPretendingItIsValid() {
        var source = new Source();
        assertThrows(IllegalStateException.class, () -> OwnedPipeline.compile(source,
            s -> CompletableFuture.completedFuture(CompiledRenderPipeline.Pending.NULL)));
        assertEquals(1, source.closes);
    }

    @Test void finishFailureClosesSource() {
        var source = new Source();
        assertThrows(IllegalStateException.class, () -> OwnedPipeline.compile(source,
            s -> CompletableFuture.completedFuture(() -> { throw new IllegalStateException("finish"); })));
        assertEquals(1, source.closes);
    }

    @Test void sourceCleanupFailureDisposesAlreadyCompiledPipeline() {
        var source = new Source(); source.failClose = true; var pipeline = new Pipeline();
        var thrown = assertThrows(IllegalStateException.class,
            () -> OwnedPipeline.compile(source, s -> ready(pipeline)));
        assertEquals("source cleanup", thrown.getMessage()); assertEquals(1, pipeline.closes);
    }

    @Test void cleanupFailureDoesNotHideOriginalFailure() {
        var source = new Source(); source.failClose = true;
        var pipeline = new Pipeline(); pipeline.failClose = true;
        var thrown = assertThrows(IllegalStateException.class,
            () -> OwnedPipeline.compile(source, s -> ready(pipeline)));
        assertEquals("source cleanup", thrown.getMessage());
        assertEquals("pipeline cleanup", thrown.getSuppressed()[0].getMessage());
        assertEquals(1, pipeline.closes);
    }

    @Test void retiringOldGenerationDoesNotDisposeNewGeneration() {
        var oldPipeline = new Pipeline(); var newPipeline = new Pipeline();
        var oldOwner = OwnedPipeline.compile(new Source(), s -> ready(oldPipeline));
        try (var newOwner = OwnedPipeline.compile(new Source(), s -> ready(newPipeline))) {
            oldOwner.close();
            assertEquals(1, oldPipeline.closes); assertEquals(0, newPipeline.closes);
            assertSame(newPipeline, newOwner.get());
        }
        assertEquals(1, newPipeline.closes);
    }

    @Test void failedDisposalIsNotRepeated() {
        var pipeline = new Pipeline(); pipeline.failClose = true;
        var owner = OwnedPipeline.compile(new Source(), s -> ready(pipeline));
        assertThrows(IllegalStateException.class, owner::close);
        assertDoesNotThrow(owner::close); assertEquals(1, pipeline.closes);
    }

    @Test void closedCompilerResultCannotBeUsedOrDisposedTwice() {
        var source = new Source(); var pipeline = new Pipeline(); pipeline.close();
        assertThrows(IllegalStateException.class, () -> OwnedPipeline.compile(source, s -> ready(pipeline)));
        assertEquals(1, source.closes); assertEquals(1, pipeline.closes);
    }

    @Test void malformedCompilerResultsCloseTheirSource() {
        var source1 = new Source(); var source2 = new Source();
        assertThrows(NullPointerException.class, () -> OwnedPipeline.compile(source1, s -> null));
        assertThrows(NullPointerException.class, () -> OwnedPipeline.compile(source2,
            s -> CompletableFuture.completedFuture(null)));
        assertEquals(1, source1.closes); assertEquals(1, source2.closes);
    }

    @Test void compilerAndSourceFailuresKeepOriginalCause() {
        var source = new Source(); source.failClose = true;
        var original = new IllegalStateException("compile");
        var thrown = assertThrows(IllegalStateException.class,
            () -> OwnedPipeline.compile(source, s -> { throw original; }));
        assertSame(original, thrown);
        assertEquals("source cleanup", thrown.getSuppressed()[0].getMessage());
        assertEquals(1, source.closes);
    }
}
