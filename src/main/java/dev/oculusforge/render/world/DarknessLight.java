package dev.oculusforge.render.world;

import net.minecraft.client.renderer.state.LightmapRenderState;

/** The extracted lightmap pulse, including Minecraft's accessibility scaling. */
public final class DarknessLight {
    private DarknessLight() { }
    public static float capture(LightmapRenderState lightmap) {
        return checked(java.util.Objects.requireNonNull(lightmap,"lightmap").darknessEffectScale);
    }
    public static float checked(float value) {
        // Preserve finite modded amplitudes; do not substitute the raw effect blend or clamp.
        if(!Float.isFinite(value)||value<0) throw new IllegalArgumentException("Invalid darkness light factor");
        return value;
    }
}
