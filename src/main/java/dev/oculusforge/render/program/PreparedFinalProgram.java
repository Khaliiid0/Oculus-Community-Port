package dev.oculusforge.render.program;

import dev.oculusforge.shaderpack.ShaderPackDescriptor;
import java.util.List;

public record PreparedFinalProgram(ShaderPackDescriptor pack, String stageName, String vertexSource,
                                   LegacyFinalShaderAdapter.AdaptedShader fragment, List<Integer> outputBuffers,
                                   ColorBufferFormats formats) {
    public PreparedFinalProgram(ShaderPackDescriptor pack, String stageName, String vertexSource,
                                LegacyFinalShaderAdapter.AdaptedShader fragment, List<Integer> outputBuffers) {
        this(pack, stageName, vertexSource, fragment, outputBuffers, ColorBufferFormats.DEFAULT);
    }
    public PreparedFinalProgram(ShaderPackDescriptor pack, String stageName, String vertexSource,
                                LegacyFinalShaderAdapter.AdaptedShader fragment, int outputBuffer) {
        this(pack, stageName, vertexSource, fragment, List.of(outputBuffer));
    }

    public PreparedFinalProgram {
        formats = java.util.Objects.requireNonNull(formats);
        outputBuffers = List.copyOf(outputBuffers);
        if (outputBuffers.isEmpty()) throw new IllegalArgumentException("A program needs at least one output");
    }

    public int outputBuffer() { return outputBuffers.getFirst(); }

    public PreparedFinalProgram withFormats(ColorBufferFormats requested) {
        return new PreparedFinalProgram(pack, stageName, vertexSource, fragment, outputBuffers, requested);
    }
}
