package dev.oculusforge.port;

import static org.junit.jupiter.api.Assertions.*;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class PipelineGenerationTest {
    static final class Pipeline implements CompiledRenderPipeline {
        int closes;
        final Runnable dispose;
        Pipeline() { this(() -> {}); }
        Pipeline(Runnable dispose) { this.dispose = dispose; }
        @Override public boolean isClosed() { return closes != 0; }
        @Override public void close() { closes++; dispose.run(); }
    }
    private static OwnedPipeline own(Pipeline pipeline) {
        return OwnedPipeline.compile(new OwnedPipelineTest.Source(),
            source -> CompletableFuture.completedFuture(() -> pipeline));
    }

    @Test void repeatedDescriptorIsCompiledOnlyOnce() {
        var key = new Object(); var pipeline = new Pipeline(); var calls = new AtomicInteger();
        try (var generation = new PipelineGeneration<Object>(2)) {
            assertSame(pipeline, generation.acquire(key, () -> { calls.incrementAndGet(); return own(pipeline); }));
            assertSame(pipeline, generation.acquire(key, () -> { fail("duplicate compilation"); return null; }));
            assertSame(pipeline, generation.get(key));
            assertEquals(1, calls.get()); assertEquals(1, generation.size());
        }
        assertEquals(1, pipeline.closes);
    }

    @Test void equalButDifferentDescriptorsHaveIndependentOwnership() {
        var a = new String("same"); var b = new String("same");
        var first = new Pipeline(); var second = new Pipeline();
        try (var generation = new PipelineGeneration<String>(2)) {
            generation.acquire(a, () -> own(first)); generation.acquire(b, () -> own(second));
            assertSame(first, generation.get(a)); assertSame(second, generation.get(b));
            assertEquals(2, generation.size());
        }
        assertEquals(1, first.closes); assertEquals(1, second.closes);
    }

    @Test void generationLimitRejectsBeforeCreatingMoreGpuResources() {
        try (var generation = new PipelineGeneration<Object>(1)) {
            generation.acquire(new Object(), () -> own(new Pipeline()));
            assertThrows(IllegalStateException.class, () -> generation.acquire(new Object(), () -> {
                fail("budget must reject before compiling"); return null;
            }));
        }
        assertThrows(IllegalArgumentException.class, () -> new PipelineGeneration<>(0));
        assertThrows(IllegalArgumentException.class, () -> new PipelineGeneration<>(513));
    }

    @Test void laterCompileFailureRollsBackAllEarlierResources() {
        var pipeline = new Pipeline(); var original = new IllegalStateException("second compile");
        var thrown = assertThrows(IllegalStateException.class, () -> {
            try (var generation = new PipelineGeneration<Object>(4)) {
                generation.acquire(new Object(), () -> own(pipeline));
                generation.acquire(new Object(), () -> { throw original; });
            }
        });
        assertSame(original, thrown); assertEquals(1, pipeline.closes);
    }

    @Test void closesInReverseOrderEvenWhenCleanupFails() {
        var order = new ArrayList<Integer>();
        var first = new Pipeline(() -> order.add(1));
        var second = new Pipeline(() -> { order.add(2); throw new IllegalStateException("second"); });
        var third = new Pipeline(() -> { order.add(3); throw new IllegalArgumentException("third"); });
        var generation = new PipelineGeneration<Object>(3);
        generation.acquire(new Object(), () -> own(first));
        generation.acquire(new Object(), () -> own(second));
        generation.acquire(new Object(), () -> own(third));
        var failure = assertThrows(IllegalArgumentException.class, generation::close);
        assertEquals("third", failure.getMessage());
        assertEquals("second", failure.getSuppressed()[0].getMessage());
        assertEquals(List.of(3, 2, 1), order); assertEquals(0, generation.size());
        assertDoesNotThrow(generation::close);
        assertEquals(1, first.closes); assertEquals(1, second.closes); assertEquals(1, third.closes);
    }

    @Test void rollbackFailureDoesNotMaskCompileFailure() {
        var original = new IllegalStateException("compile");
        var pipeline = new Pipeline(() -> { throw new IllegalArgumentException("cleanup"); });
        var thrown = assertThrows(IllegalStateException.class, () -> {
            try (var generation = new PipelineGeneration<Object>(2)) {
                generation.acquire(new Object(), () -> own(pipeline));
                throw original;
            }
        });
        assertSame(original, thrown); assertEquals("cleanup", thrown.getSuppressed()[0].getMessage());
    }

    @Test void oneGenerationCannotFindOrCloseAnothersPipeline() {
        var descriptor = new Object(); var first = new Pipeline(); var second = new Pipeline();
        var oldGeneration = new PipelineGeneration<Object>(1);
        try (var next = new PipelineGeneration<Object>(1)) {
            oldGeneration.acquire(descriptor, () -> own(first));
            assertThrows(IllegalStateException.class, () -> next.get(descriptor));
            next.acquire(descriptor, () -> own(second)); oldGeneration.close();
            assertSame(second, next.get(descriptor)); assertEquals(0, second.closes);
        }
        assertEquals(1, first.closes); assertEquals(1, second.closes);
    }

    @Test void closedGenerationRejectsNewCompilationAndStaleReads() {
        var generation = new PipelineGeneration<Object>(1); generation.close();
        assertThrows(IllegalStateException.class, () -> generation.acquire(new Object(), () -> {
            fail("closed generation must not compile"); return null;
        }));
        assertThrows(IllegalStateException.class, () -> generation.get(new Object()));
    }

    @Test void invalidOwnerDoesNotConsumeBudgetAndRetryCanSucceed() {
        try (var generation = new PipelineGeneration<Object>(1)) {
            var key = new Object();
            assertThrows(NullPointerException.class, () -> generation.acquire(key, () -> null));
            var retired = own(new Pipeline()); retired.close();
            assertThrows(IllegalStateException.class, () -> generation.acquire(key, () -> retired));
            assertEquals(0, generation.size());
            var replacement = new Pipeline();
            assertSame(replacement, generation.acquire(key, () -> own(replacement)));
        }
    }

    @Test void cleanupAttemptsEveryResourceIncludingAfterFatalFailure() {
        var order = new ArrayList<Integer>(); var original = new AssertionError("first");
        var failure = assertThrows(AssertionError.class, () -> ResourceDisposal.closeAll(
            () -> { order.add(1); throw original; }, null,
            () -> { order.add(2); throw new IllegalStateException("second"); },
            () -> order.add(3)));
        assertSame(original, failure); assertEquals(List.of(1, 2, 3), order);
        assertEquals("second", failure.getSuppressed()[0].getMessage());
    }

    @Test void checkedCleanupFailureIsWrappedWithoutDiscardingLaterCleanup() {
        var visited = new AtomicInteger(); var original = new Exception("checked");
        var failure = assertThrows(IllegalStateException.class, () -> ResourceDisposal.closeAll(
            () -> { throw original; }, visited::incrementAndGet));
        assertSame(original, failure.getCause()); assertEquals(1, visited.get());
    }
}
