package dev.oculusforge.render.program;

import java.util.Objects;
import java.util.function.Supplier;

/** Logical depth identity is not the same as the currently shared physical image. */
public final class WorldDepthSamplers {
    private WorldDepthSamplers() {}

    public static boolean requiresOpaqueSnapshot(LegacyFinalShaderAdapter.SamplerBinding binding) {
        return binding.texture()==LegacyFinalShaderAdapter.TextureSemantic.DEPTH && opaque(binding.colorIndex());
    }

    private static boolean opaque(int index) {
        return switch(index) {
            case 0 -> false;
            // Hand rendering is currently outside the LevelRenderer/post scope.
            // Thus both opaque-with-hand (1) and opaque-without-hand (2) use the
            // pre-translucent snapshot, NEVER the live depth after water/glass.
            // Keep their logical IDs distinct for a future native hand bridge.
            case 1,2 -> true;
            default -> throw new IllegalArgumentException("Unsupported world depth index: "+index);
        };
    }

    public static <T> T resolve(int index,Supplier<T> live,Supplier<T> snapshot) {
        return Objects.requireNonNull((opaque(index)?snapshot:live).get(),"Missing world depth image");
    }
}
