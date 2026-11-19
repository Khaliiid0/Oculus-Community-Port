package dev.oculusforge.render.program;

/** Pack shadow matrices remain conventional; only rasterizer output becomes reverse Z. */
public final class ShadowClipSpace {
    public static final String ZERO_TO_ONE_DEFINE = "OCULUS_SHADOW_ZERO_TO_ONE";
    public static final String CONVERSION = """
        #ifdef OCULUS_SHADOW_ZERO_TO_ONE
            gl_Position.z = (gl_Position.w - gl_Position.z) * 0.5;
        #else
            gl_Position.z = -gl_Position.z;
        #endif
        """;

    private ShadowClipSpace() {}
}
