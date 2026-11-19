package dev.oculusforge.port;

import java.util.Objects;

/** 26.3 chunk inputs; section instances are vertex binding 1, not a fabricated instance index. */
public final class TerrainShaderAbi {
    public static final String GLOBALS = """
        layout(std140) uniform Globals {
            ivec3 CameraBlockPos; float GlintAlpha;
            vec3 CameraOffset; float GameTime;
            vec2 ScreenSize; int MenuBlurRadius; int UseRgss;
        };
        """;
    public static final String TERRAIN = """
        layout(std140) uniform TerrainUniform { mat4 ModelViewMat; ivec2 TextureSize; };
        """;
    public static final String SECTION = """
        layout(std140) uniform ChunkSection { ivec3 ChunkPosition; float ChunkVisibility; };
        """;
    private static final String INSTANCE_INPUTS = """
        in ivec3 ChunkPosition;
        in float ChunkVisibility;
        """;

    private TerrainShaderAbi() {}

    /** Specializes only our generated block. Unknown/ambiguous ABI sources are never guessed. */
    public static String vertex(String prepared, boolean indirect) {
        Objects.requireNonNull(prepared, "prepared vertex");
        if (!prepared.contains(GLOBALS) || !prepared.contains(TERRAIN))
            throw new IllegalArgumentException("Terrain shader is missing the 26.3 globals/transform ABI");
        int first = prepared.indexOf(SECTION);
        if (first < 0 || prepared.indexOf(SECTION, first + SECTION.length()) >= 0)
            throw new IllegalArgumentException("Terrain shader needs exactly one generated section block");
        if (!indirect) return prepared;
        return prepared.substring(0, first) + INSTANCE_INPUTS + prepared.substring(first + SECTION.length());
    }
}
