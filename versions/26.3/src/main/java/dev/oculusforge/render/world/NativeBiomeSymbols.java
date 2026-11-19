package dev.oculusforge.render.world;

import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Locale;

/** Private symbolic matching codes, NOT Iris/OptiFine numeric biome IDs. Zero is unknown. */
public final class NativeBiomeSymbols {
    private static final Map<String,Integer> KEYS;
    static {
        var keys=new LinkedHashMap<String,Integer>();
        String names="""
            the_void plains sunflower_plains snowy_plains ice_spikes desert swamp mangrove_swamp
            forest flower_forest birch_forest dark_forest pale_garden old_growth_birch_forest
            old_growth_pine_taiga old_growth_spruce_taiga taiga snowy_taiga savanna savanna_plateau
            windswept_hills windswept_gravelly_hills windswept_forest windswept_savanna jungle
            sparse_jungle bamboo_jungle badlands eroded_badlands wooded_badlands meadow cherry_grove
            grove snowy_slopes frozen_peaks jagged_peaks stony_peaks river frozen_river beach
            snowy_beach stony_shore warm_ocean lukewarm_ocean deep_lukewarm_ocean ocean deep_ocean
            cold_ocean deep_cold_ocean frozen_ocean deep_frozen_ocean mushroom_fields dripstone_caves
            lush_caves deep_dark sulfur_caves nether_wastes warped_forest crimson_forest
            soul_sand_valley basalt_deltas the_end end_highlands end_midlands small_end_islands end_barrens dappled_forest
            """;
        for(String name:names.trim().split("\\s+")) keys.put("minecraft:"+name,keys.size()+1);
        KEYS=Map.copyOf(keys);
    }
    private NativeBiomeSymbols() { }
    public static int fromKey(String key) { return KEYS.getOrDefault(key,0); }
    public static int fromSymbol(String symbol) {
        if(!symbol.matches("BIOME_[A-Z_]+")) return 0;
        return fromKey("minecraft:"+symbol.substring(6).toLowerCase(Locale.ROOT));
    }
    public static boolean valid(int code) { return code>=0&&code<=KEYS.size(); }
}
