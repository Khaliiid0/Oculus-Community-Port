package dev.oculusforge.render.program;

import com.mojang.renderpearl.api.GpuFormat;

/** Filterable, non-integer color formats understood by the current shader bridge. */
public enum ColorBufferFormat {
    R8(GpuFormat.R8_UNORM), RG8(GpuFormat.RG8_UNORM), RGB8(GpuFormat.RGBA8_UNORM, true), RGBA8(GpuFormat.RGBA8_UNORM),
    R8_SNORM(GpuFormat.R8_SNORM), RG8_SNORM(GpuFormat.RG8_SNORM), RGB8_SNORM(GpuFormat.RGBA8_SNORM, true), RGBA8_SNORM(GpuFormat.RGBA8_SNORM),
    R16(GpuFormat.R16_UNORM), RG16(GpuFormat.RG16_UNORM), RGB16(GpuFormat.RGBA16_UNORM, true), RGBA16(GpuFormat.RGBA16_UNORM),
    R16_SNORM(GpuFormat.R16_SNORM), RG16_SNORM(GpuFormat.RG16_SNORM), RGB16_SNORM(GpuFormat.RGBA16_SNORM, true), RGBA16_SNORM(GpuFormat.RGBA16_SNORM),
    R16F(GpuFormat.R16_FLOAT), RG16F(GpuFormat.RG16_FLOAT), RGB16F(GpuFormat.RGBA16_FLOAT, true), RGBA16F(GpuFormat.RGBA16_FLOAT),
    R32F(GpuFormat.R32_FLOAT), RG32F(GpuFormat.RG32_FLOAT), RGB32F(GpuFormat.RGBA32_FLOAT, true), RGBA32F(GpuFormat.RGBA32_FLOAT),
    RGB10_A2(GpuFormat.RGB10A2_UNORM), R11F_G11F_B10F(GpuFormat.RG11B10_FLOAT);

    private final GpuFormat gpu;
    private final boolean paddedRgb;
    private final org.joml.Vector4fc clearColor;
    ColorBufferFormat(GpuFormat gpu) { this(gpu, false); }
    ColorBufferFormat(GpuFormat gpu, boolean paddedRgb) {
        this.gpu = gpu;
        this.paddedRgb = paddedRgb;
        this.clearColor = new org.joml.Vector4f(0, 0, 0, paddedRgb ? 1 : 0);
    }
    public GpuFormat gpu() { return gpu; }
    public int bytesPerPixel() { return gpu.blockSize(); }
    // Minecraft has RGB enum values but no GL texture allocation mappings for them.
    // Preserve RGB precision with RGBA storage; clear alpha to one and never write it.
    public int writeMask() { return paddedRgb ? 7 : 15; }
    public org.joml.Vector4fc clearColor() { return clearColor; }

    public void validateTextureMapping() throws ShaderCompatibilityException {
        if (com.mojang.renderpearl.backend.opengl.GlConst.toGlInternalId(gpu) == 0
            || com.mojang.renderpearl.backend.opengl.GlConst.toGlExternalId(gpu) == 0
            || com.mojang.renderpearl.backend.opengl.GlConst.toGlType(gpu) == 0) {
            throw new ShaderCompatibilityException("No OpenGL texture mapping for " + this);
        }
    }

    public static ColorBufferFormat parse(String value) throws ShaderCompatibilityException {
        // Legacy unsized declarations retain the historical normalized eight-bit storage.
        if (value.equals("RGBA")) return RGBA8;
        if (value.equals("RGB")) return RGB8;
        try { return valueOf(value); }
        catch (IllegalArgumentException failure) {
            throw new ShaderCompatibilityException("Unsupported color-buffer format '" + value
                + "'; integer targets and format expressions are not implemented");
        }
    }
}
