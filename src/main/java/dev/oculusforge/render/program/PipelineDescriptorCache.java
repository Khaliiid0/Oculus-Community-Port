package dev.oculusforge.render.program;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Minecraft keys its GPU pipeline cache by RenderPipeline object identity.
 * Intern descriptors for identical source, and bound distinct programs until a full reload.
 * This class does not close Minecraft-owned compiled pipelines.
 */
public final class PipelineDescriptorCache<T> {
    public static final int DEFAULT_LIMIT = 512;
    private final int limit;
    private final Map<String, T> entries = new HashMap<>();

    public PipelineDescriptorCache() { this(DEFAULT_LIMIT); }
    PipelineDescriptorCache(int limit) {
        if (limit < 1) throw new IllegalArgumentException("Pipeline limit must be positive");
        this.limit = limit;
    }

    public synchronized void requireCapacity(Set<String> requested) throws ShaderCompatibilityException {
        Set<String> missing = new HashSet<>(requested);
        missing.removeAll(entries.keySet());
        if (missing.size() > limit - entries.size()) throw new ShaderCompatibilityException(
            "Shader pipeline cache safety limit reached (" + limit + " distinct programs). "
                + "Press F3+T for a full resource reload before loading more distinct shader sources.");
    }

    public synchronized T getOrCreate(String fingerprint, Supplier<T> factory) throws ShaderCompatibilityException {
        T existing = entries.get(fingerprint);
        if (existing != null) return existing;
        requireCapacity(Set.of(fingerprint));
        T created = java.util.Objects.requireNonNull(factory.get());
        entries.put(fingerprint, created);
        return created;
    }

    public synchronized int size() { return entries.size(); }
    public synchronized void clear() { entries.clear(); }
}
