package dev.oculusforge.client;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** One running preparation plus at most one newest queued request; obsolete futures finish cancelled. */
public final class LatestTaskExecutor implements AutoCloseable {
    private final Executor executor;
    private final Runnable shutdown;
    private Task<?> pending;
    private Task<?> running;
    private boolean draining;
    private boolean closed;

    public LatestTaskExecutor() {
        var service = Executors.newSingleThreadExecutor(task -> {
            Thread worker = new Thread(task, "Oculus shader preparation");
            worker.setDaemon(true);
            return worker;
        });
        executor = service;
        shutdown = service::shutdownNow;
    }

    LatestTaskExecutor(Executor executor, Runnable shutdown) {
        this.executor = executor; this.shutdown = shutdown;
    }

    public synchronized <T> CompletableFuture<T> submit(Callable<T> action) {
        if (closed) return CompletableFuture.failedFuture(new IllegalStateException("Shader executor is closed"));
        if (pending != null) pending.future.cancel(false);
        Task<T> task = new Task<>(action, new CompletableFuture<>());
        pending = task;
        if (!draining) {
            draining = true;
            try { executor.execute(this::drain); }
            catch (RuntimeException failure) {
                draining = false; pending = null; task.future.completeExceptionally(failure);
            }
        }
        return task.future;
    }

    private void drain() {
        while (true) {
            Task<?> task;
            synchronized (this) {
                task = pending;
                pending = null;
                if (closed || task == null) { running = null; draining = false; return; }
                running = task;
            }
            task.run();
            synchronized (this) { running = null; }
        }
    }

    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        if (pending != null) pending.future.cancel(false);
        if (running != null) running.future.cancel(false);
        pending = null;
        shutdown.run();
    }

    private record Task<T>(Callable<T> action, CompletableFuture<T> future) {
        void run() {
            if (future.isDone()) return;
            try { future.complete(action.call()); }
            catch (Throwable failure) { future.completeExceptionally(failure); }
        }
    }
}
