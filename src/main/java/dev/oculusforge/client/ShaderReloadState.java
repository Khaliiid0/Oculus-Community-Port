package dev.oculusforge.client;

import dev.oculusforge.config.OculusConfig;

/** Thread-safe selection identity and full-resource-reload barrier; contains no GPU objects. */
public final class ShaderReloadState {
    public record Request(long revision, OculusConfig config) {}
    public record ResourceRequest(long epoch, Request selection) {}
    private Request current;
    private long resourceEpoch;
    private boolean resourcePending;
    private boolean closed;

    public ShaderReloadState(OculusConfig initial) { current = new Request(0, initial); }

    public synchronized Request select(OculusConfig selection) {
        if (closed) throw new IllegalStateException("Shader service is closed");
        current = new Request(current.revision() + 1, selection);
        return current;
    }

    public synchronized ResourceRequest beginResources() {
        if (closed) throw new IllegalStateException("Shader service is closed");
        resourcePending = true;
        return new ResourceRequest(++resourceEpoch, current);
    }

    public synchronized boolean finishResources(long epoch) {
        if (closed || !resourcePending || epoch != resourceEpoch) return false;
        resourcePending = false;
        return true;
    }

    public synchronized boolean isCurrent(Request request) {
        return !closed && request.revision() == current.revision() && request.config().equals(current.config());
    }

    public synchronized boolean resourcesPending() { return resourcePending; }
    public synchronized Request current() { return current; }
    public synchronized void close() { closed = true; resourcePending = false; }
}
