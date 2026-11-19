package dev.oculusforge.port;

import com.mojang.renderpearl.api.pipeline.ShaderSource;
import com.mojang.renderpearl.api.pipeline.ShaderType;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.resources.Identifier;

/**
 * Owned 26.3 source provider for already-preprocessed pack shaders. It never
 * resolves includes from Minecraft resources, disk or the network. Keep this
 * provider alive until every asynchronous compilation using it has completed.
 */
public final class PreparedShaderSource implements ShaderSource {
    public static final int MAX_STAGES = 512;
    public static final int MAX_SOURCE_CHARS = 32 * 1024 * 1024;
    private Map<ShaderType, Map<Identifier, String>> sources;
    private boolean closed;

    public PreparedShaderSource(Map<ShaderType, Map<Identifier, String>> input) {
        Objects.requireNonNull(input, "input");
        var copy = new EnumMap<ShaderType, Map<Identifier, String>>(ShaderType.class);
        long characters = 0;
        int stages = 0;
        for (var stage : input.entrySet()) {
            Objects.requireNonNull(stage.getKey(), "shader type");
            var values = Map.copyOf(stage.getValue());
            stages = Math.addExact(stages, values.size());
            for (var source : values.values()) characters += source.length();
            if (stages > MAX_STAGES || characters > MAX_SOURCE_CHARS) {
                throw new IllegalArgumentException("Prepared shader source budget exceeded");
            }
            copy.put(stage.getKey(), values);
        }
        sources = Map.copyOf(copy);
    }

    public static PreparedShaderSource pair(Identifier vertexId, String vertex,
                                           Identifier fragmentId, String fragment) {
        return new PreparedShaderSource(Map.of(
            ShaderType.VERTEX, Map.of(vertexId, vertex),
            ShaderType.FRAGMENT, Map.of(fragmentId, fragment)));
    }

    @Override
    public synchronized String getShader(Identifier id, ShaderType type) {
        requireOpen();
        return sources.getOrDefault(Objects.requireNonNull(type, "type"), Map.of())
            .get(Objects.requireNonNull(id, "id"));
    }

    @Override
    public synchronized CachedIncludeSource getInclude(Identifier id) {
        requireOpen();
        Objects.requireNonNull(id, "id");
        // GlslCompiler converts null to its owned "not found" result. Do not
        // allocate native include buffers or fall back outside the pack sandbox.
        return null;
    }

    @Override
    public synchronized void close() {
        sources = Map.of();
        closed = true;
    }

    public synchronized boolean isClosed() { return closed; }

    private void requireOpen() {
        if (closed) throw new IllegalStateException("Prepared shader source is closed");
    }
}
