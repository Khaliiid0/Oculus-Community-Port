package dev.oculusforge.port;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.*;
import com.mojang.renderpearl.frontend.FrontendRenderPipeline;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.function.Function;

/**
 * Bounded, non-owning links to Minecraft-cache pipelines. Clearing links never
 * closes GPU objects. Source ambiguity or overflow rejects retargeting, not
 * ordinary vanilla rendering. Native pipelines with matching formats pass through.
 */
public final class CompiledPipelineRetargeter {
    private final IdentityHashMap<CompiledRenderPipeline, RenderPipeline> sources = new IdentityHashMap<>();
    private final int limit;

    public CompiledPipelineRetargeter(int limit) {
        if (limit < 1 || limit > 4096) throw new IllegalArgumentException("Invalid pipeline link budget");
        this.limit = limit;
    }

    public synchronized boolean remember(RenderPipeline source, CompiledRenderPipeline compiled) {
        if (source == null || compiled == null || compiled.isClosed()) return false;
        if (sources.containsKey(compiled)) {
            var previous = sources.get(compiled);
            if (previous != source) sources.put(compiled, null); // Permanently ambiguous until reload.
            return previous == source;
        }
        if (sources.size() >= limit) return false;
        sources.put(compiled, source);
        return true;
    }

    public CompiledRenderPipeline retarget(CompiledRenderPipeline original, GpuFormat format, int writeMask,
        int attachmentMask, Function<RenderPipeline, RenderPipeline> adapt,
        Function<RenderPipeline, CompiledRenderPipeline> compileBorrowed) {
        if (attachmentMask == 0) return original;
        Objects.requireNonNull(format);
        if (writeMask < 0 || (writeMask & ~15) != 0) throw new IllegalArgumentException("Invalid scene write mask");
        var metadata = metadata(original);
        validateMask(metadata, attachmentMask);
        if (matches(metadata,format,writeMask,attachmentMask)) return original;
        final RenderPipeline source;
        synchronized (this) { source = sources.get(original); }
        if (source == null) throw new IllegalStateException(
            "HDR scene pipeline has no unique cached descriptor (missing, ambiguous or link budget exceeded)");
        var descriptor = Objects.requireNonNull(adapt.apply(source), "adapted descriptor");
        var result = Objects.requireNonNull(compileBorrowed.apply(descriptor), "compiled scene pipeline");
        var adapted = metadata(result);
        validateMask(adapted,attachmentMask);
        if (adapted.colorTargetStates().size() != metadata.colorTargetStates().size()
            || !matches(adapted,format,writeMask,attachmentMask))
            throw new IllegalStateException("Compiled HDR pipeline does not match its scene attachments");
        for (int slot = 0; slot < metadata.colorTargetStates().size(); slot++) {
            var before = metadata.colorTargetStates().get(slot);
            var after = adapted.colorTargetStates().get(slot);
            if ((attachmentMask & 1 << slot) == 0) {
                if (!Objects.equals(before,after)) throw new IllegalStateException("HDR retarget changed an unowned attachment");
            } else if (!before.blendFunction().equals(after.blendFunction())
                || after.writeMask() != (before.writeMask() & writeMask)) {
                throw new IllegalStateException("HDR retarget changed scene blend/write semantics");
            }
        }
        // Both objects are borrowed from Minecraft's cache, including on failure.
        return result;
    }

    private static FrontendRenderPipeline metadata(CompiledRenderPipeline pipeline) {
        if (pipeline == null || pipeline.isClosed()) throw new IllegalStateException("Cannot bind a closed/null scene pipeline");
        if (!(pipeline instanceof FrontendRenderPipeline frontend))
            throw new IllegalStateException("Unknown compiled pipeline implementation for HDR scene");
        return frontend;
    }

    private static void validateMask(FrontendRenderPipeline pipeline, int mask) {
        int count = pipeline.colorTargetStates().size();
        if (mask < 0 || count > 8 || (mask >>> count) != 0)
            throw new IllegalArgumentException("Invalid compiled scene attachment mask");
        for (int slot = 0; slot < count; slot++)
            if ((mask & 1 << slot) != 0 && pipeline.colorTargetStates().get(slot) == null)
                throw new IllegalStateException("Scene attachment has no pipeline state");
    }

    private static boolean matches(FrontendRenderPipeline pipeline, GpuFormat format, int writeMask, int mask) {
        for (int slot = 0; slot < pipeline.colorTargetStates().size(); slot++) if ((mask & 1 << slot) != 0) {
            var state = pipeline.colorTargetStates().get(slot);
            if (state.format() != format || (state.writeMask() & writeMask) != state.writeMask()) return false;
        }
        return true;
    }

    public synchronized int size() { return sources.size(); }
    public synchronized void clear() { sources.clear(); }
}
