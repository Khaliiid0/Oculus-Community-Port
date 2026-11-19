package dev.oculusforge.client;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayDeque;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;

final class LatestTaskExecutorTest {
    @Test void onlyNewestPendingRequestRunsAndOlderFuturesAreCancelled() {
        var work = new ArrayDeque<Runnable>();
        try (var executor = new LatestTaskExecutor(work::add, () -> {})) {
            var first = executor.submit(() -> 1); var second = executor.submit(() -> 2); var third = executor.submit(() -> 3);
            assertEquals(1, work.size()); assertTrue(first.isCancelled()); assertTrue(second.isCancelled());
            work.remove().run(); assertEquals(3, third.join());
        }
    }

    @Test void runningWorkCanQueueOneNewestSuccessor() {
        var work = new ArrayDeque<Runnable>();
        try (var executor = new LatestTaskExecutor(work::add, () -> {})) {
            var results = new java.util.ArrayList<CompletableFuture<Integer>>();
            var first = executor.submit(() -> {
                results.add(executor.submit(() -> 2)); results.add(executor.submit(() -> 3)); return 1;
            });
            work.remove().run();
            assertEquals(1, first.join()); assertTrue(results.get(0).isCancelled()); assertEquals(3, results.get(1).join());
        }
    }

    @Test void preparationFailureDoesNotPoisonLaterWork() {
        var work = new ArrayDeque<Runnable>();
        try (var executor = new LatestTaskExecutor(work::add, () -> {})) {
            var failed = executor.submit(() -> { throw new java.io.IOException("bad shader"); });
            work.remove().run(); assertThrows(CompletionException.class, failed::join);
            var valid = executor.submit(() -> 42); work.remove().run(); assertEquals(42, valid.join());
        }
    }

    @Test void closeCancelsQueuedWorkAndRefusesNewJobs() {
        var work = new ArrayDeque<Runnable>();
        var executor = new LatestTaskExecutor(work::add, () -> {});
        var queued = executor.submit(() -> fail("Closed executor must not run queued work"));
        executor.close(); executor.close(); work.remove().run();
        assertTrue(queued.isCancelled());
        assertThrows(CompletionException.class, () -> executor.submit(() -> 3).join());
    }

    @Test void aThousandClicksStillScheduleOnlyOneWorkerDrain() {
        var work = new ArrayDeque<Runnable>();
        try (var executor = new LatestTaskExecutor(work::add, () -> {})) {
            CompletableFuture<Integer> last = null;
            for (int i = 0; i < 1000; i++) { int value = i; last = executor.submit(() -> value); }
            assertEquals(1, work.size()); work.remove().run(); assertEquals(999, last.join());
        }
    }

    @Test void workerSchedulingFailureCompletesTheFuture() {
        try (var executor = new LatestTaskExecutor(task -> { throw new java.util.concurrent.RejectedExecutionException(); }, () -> {})) {
            assertThrows(CompletionException.class, () -> executor.submit(() -> 1).join());
        }
    }
}
