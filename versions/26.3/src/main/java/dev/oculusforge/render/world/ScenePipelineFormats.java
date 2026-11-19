package dev.oculusforge.render.world;

import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.oculusforge.render.program.ColorBufferFormat;
import java.util.IdentityHashMap;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.Identifier;

/** Retarget only attachments identified as the owned scene, preserving all other render state. */
public final class ScenePipelineFormats {
    public static final int MAX_DESCRIPTORS = 256;
    private record Key(ColorBufferFormat format, int attachmentMask) { }
    private static final Map<RenderPipeline, Map<Key, RenderPipeline>> CACHE = new IdentityHashMap<>();
    private static int size;
    private ScenePipelineFormats() { }

    public static synchronized RenderPipeline adapt(RenderPipeline source, ColorBufferFormat format, int attachmentMask) {
        var original = source.getColorTargetStates();
        if (attachmentMask == 0) return source;
        if (attachmentMask < 0 || original.size() > 8 || (attachmentMask >>> original.size()) != 0)
            throw new IllegalArgumentException("Invalid HDR scene attachment mask");
        var colors = original.toArray(ColorTargetState[]::new);
        boolean changed = false;
        for (int i = 0; i < colors.length; i++) if ((attachmentMask & 1 << i) != 0) {
            var color = java.util.Objects.requireNonNull(colors[i], "Scene output has no pipeline target state");
            if(color.format()!=com.mojang.renderpearl.api.GpuFormat.RGBA8_UNORM && color.format()!=format.gpu())
                throw new IllegalStateException("HDR scene cannot retarget a foreign output format: "+color.format());
            int mask = color.writeMask() & format.writeMask();
            if (color.format() != format.gpu() || color.writeMask() != mask) {
                colors[i] = new ColorTargetState(color.blendFunction(), format.gpu(), mask);
                changed = true;
            }
        }
        if (!changed) return source;
        var key = new Key(format, attachmentMask);
        var entries = CACHE.get(source);
        if (entries != null && entries.containsKey(key)) return entries.get(key);
        if (size >= MAX_DESCRIPTORS)
            throw new IllegalStateException("HDR scene pipeline cache safety limit reached; reload resources before adding more pipelines");
        var builder = dev.oculusforge.port.PipelineDescriptorCopies.builder(source);
        for (int i = 0; i < colors.length; i++) builder.withColorTargetState(i, colors[i]);
        var result = builder.withLocation(Identifier.fromNamespaceAndPath("oculus",
            "scene/" + format.name().toLowerCase(java.util.Locale.ROOT) + "/" + attachmentMask + "/"
                + source.getLocation().getNamespace() + "/" + source.getLocation().getPath())).build();
        CACHE.computeIfAbsent(source, ignored -> new HashMap<>()).put(key, result);
        size++;
        return result;
    }

    public static synchronized int size() { return size; }
    /** Full resource reload only; shader toggles reuse the same descriptors. */
    public static synchronized void reset() { CACHE.clear(); size = 0; }
}
