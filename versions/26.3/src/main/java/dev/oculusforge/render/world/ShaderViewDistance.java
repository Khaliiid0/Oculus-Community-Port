package dev.oculusforge.render.world;

import net.minecraft.client.renderer.state.OptionsRenderState;

/** Iris's `far` means effective render distance in blocks, NOT camera.depthFar. */
public final class ShaderViewDistance {
    private ShaderViewDistance() { }

    public static float blocks(OptionsRenderState options) {
        return blocks(options.renderDistance);
    }

    public static float blocks(int chunks) {
        if(chunks<0)throw new IllegalArgumentException("Negative effective render distance");
        return chunks*16.0F;
    }
}
