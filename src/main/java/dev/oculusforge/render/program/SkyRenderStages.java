package dev.oculusforge.render.program;

import java.util.Map;
import java.util.LinkedHashMap;

/** Sky-stage IDs match the Iris 26.1 WorldRenderingPhase ordering. */
public final class SkyRenderStages {
    public static final int SKY = 1;
    private SkyRenderStages() {}
    public static Map<String,String> macros() {
        String[] names = {"NONE", "SKY", "SUNSET", "CUSTOM_SKY", "SUN", "MOON", "STARS", "VOID"};
        var result = new LinkedHashMap<String,String>();
        for (int i=0;i<names.length;i++) result.put("MC_RENDER_STAGE_" + names[i], Integer.toString(i));
        return Map.copyOf(result);
    }
    /** A dedicated End-sky pipeline is always the SKY phase, not sun/moon/stars.
     * Specialize both stages rather than allocate a mutable per-draw uniform buffer.
     */
    public static String specializeEndSky(String source) {
        String block = NativeSkyVertexAdapter.STAGE_BLOCK.strip();
        if (!source.contains(block)) throw new IllegalArgumentException("Missing sky stage block");
        return source.replace(block, "const int oculusSkyRenderStage = " + SKY + ";");
    }
    public static boolean endSkyEligible(NativeSkyProgram program) {
        return program != null && program.samplers().stream().allMatch(s ->
            s.name().equals("Sampler0") && s.texture() == LegacyFinalShaderAdapter.TextureSemantic.CUSTOM
                && s.imageKey().equals("native-sky-texture"));
    }
}
