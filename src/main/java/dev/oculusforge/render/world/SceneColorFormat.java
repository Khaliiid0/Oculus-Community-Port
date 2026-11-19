package dev.oculusforge.render.world;

import dev.oculusforge.render.program.ColorBufferFormat;
import dev.oculusforge.render.program.ColorBufferFormats;

/** Explicit development admission; no silent precision downgrade or transparency bypass. */
public final class SceneColorFormat {
    public static final boolean ENABLED = Boolean.getBoolean("oculus.experimental.hdrWorldScene");
    private SceneColorFormat() { }

    public static ColorBufferFormat terrain(ColorBufferFormats formats) {
        return ENABLED ? formats.get(0) : ColorBufferFormat.RGBA8;
    }

    public static boolean requiresCapture(ColorBufferFormats formats) {
        return ENABLED && formats.get(0) != ColorBufferFormat.RGBA8;
    }

    public static void requireSupported(ColorBufferFormats formats, boolean improvedTransparency) {
        if (improvedTransparency)
            throw new IllegalStateException("HDR world scene does not yet support improved-transparency intermediate targets");
        if (!formats.shouldClear(0) || formats.clearColors().containsKey(0))
            throw new IllegalStateException("HDR world scene custom colortex0 clear/history is not implemented");
        if(formats.get(0).writeMask()==7)
            throw new IllegalStateException("HDR world scene padded RGB implicit-alpha clearing is not implemented");
    }
}
