package dev.oculusforge.render.world;

import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import dev.oculusforge.render.program.ShaderCompatibilityException;

/** Validation for the stock descriptor's shared blend-factor restriction. */
public final class IndexedTerrainBlending {
    private IndexedTerrainBlending() { }

    public static boolean requiresIndependentFunctions(ColorTargetState[] states) {
        BlendFunction first = null;
        for (var state : states) if (state != null && state.blendFunction().isPresent()) {
            var function = state.blendFunction().orElseThrow();
            if (first != null && !first.equals(function)) return true;
            first = function;
        }
        return false;
    }
    public static void requireSupported(ColorTargetState[] states) throws ShaderCompatibilityException {
        if (requiresIndependentFunctions(states))
            throw new ShaderCompatibilityException("Different enabled per-buffer terrain blend factors are not supported by the pinned RenderPearl pipeline descriptors; per-buffer on/off is supported");
    }
}
