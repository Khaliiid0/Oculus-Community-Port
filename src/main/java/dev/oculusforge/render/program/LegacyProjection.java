package dev.oculusforge.render.program;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;

/** Matches legacy NDC [-1,1] to depth samplers exposing 1 - Minecraft depth. */
public final class LegacyProjection {
    private LegacyProjection() {}

    public static Matrix4f fromReverseDepth(Matrix4fc projection, boolean zeroToOne) {
        // Legacy z/w = 1 - 2 * native z/w on zero-to-one devices,
        // or -native z/w on negative-one-to-one devices. Preserve x, y and w.
        return new Matrix4f().m22(zeroToOne ? -2.0f : -1.0f)
            .m32(zeroToOne ? 1.0f : 0.0f).mul(projection);
    }
}
