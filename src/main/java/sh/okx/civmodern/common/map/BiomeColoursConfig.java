package sh.okx.civmodern.common.map;

import java.util.HashMap;
import java.util.Map;

/**
 * Colours for the biome overlay, keyed by biome registry name, curated the same way
 * {@link ColoursConfig#BLOCK_COLOURS} is curated for blocks. A biome the server adds through a
 * datapack simply falls back to {@link #colourFor(String, int)}'s id-keyed ramp.
 */
public class BiomeColoursConfig {
    public static final Map<String, Integer> BIOME_COLOURS = new HashMap<>() {{
        // Oceans and rivers: a blue ramp, darker with depth and colder with temperature.
        put("minecraft:ocean", 0x2B5D8C);
        put("minecraft:deep_ocean", 0x1C3F63);
        put("minecraft:warm_ocean", 0x2B87AE);
        put("minecraft:lukewarm_ocean", 0x2A73A0);
        put("minecraft:deep_lukewarm_ocean", 0x1E5279);
        put("minecraft:cold_ocean", 0x2E5F86);
        put("minecraft:deep_cold_ocean", 0x20415E);
        put("minecraft:frozen_ocean", 0x6E93AD);
        put("minecraft:deep_frozen_ocean", 0x4C6E87);
        put("minecraft:river", 0x3E7CB1);
        put("minecraft:frozen_river", 0x82AFC7);

        // Beaches and shores: sandy and stone tones.
        put("minecraft:beach", 0xD8CA8E);
        put("minecraft:snowy_beach", 0xE4E6DE);
        put("minecraft:stony_shore", 0x8B8C86);

        // Grassy biomes: a spread of greens.
        put("minecraft:plains", 0x8DB360);
        put("minecraft:sunflower_plains", 0x9ACB55);
        put("minecraft:meadow", 0x63A947);

        // Forests.
        put("minecraft:forest", 0x4E7A34);
        put("minecraft:flower_forest", 0x6BAA45);
        put("minecraft:birch_forest", 0x6C9450);
        put("minecraft:old_growth_birch_forest", 0x5E8546);
        put("minecraft:dark_forest", 0x3B4C2A);
        put("minecraft:cherry_grove", 0xE897C4);

        // Taiga and cold hills.
        put("minecraft:taiga", 0x36654A);
        put("minecraft:snowy_taiga", 0x5C7A6C);
        put("minecraft:old_growth_pine_taiga", 0x2E5A44);
        put("minecraft:old_growth_spruce_taiga", 0x30563F);

        // Windswept / hills.
        put("minecraft:windswept_hills", 0x767F6B);
        put("minecraft:windswept_gravelly_hills", 0x848C81);
        put("minecraft:windswept_forest", 0x5E6E52);
        put("minecraft:windswept_savanna", 0xA69350);

        // Jungle.
        put("minecraft:jungle", 0x2C8A2C);
        put("minecraft:sparse_jungle", 0x4B9B3F);
        put("minecraft:bamboo_jungle", 0x3D9A3D);

        // Savanna.
        put("minecraft:savanna", 0xB5A644);
        put("minecraft:savanna_plateau", 0xA89A45);

        // Dry / arid.
        put("minecraft:desert", 0xD9C86A);
        put("minecraft:badlands", 0xB0632E);
        put("minecraft:eroded_badlands", 0xC17A3F);
        put("minecraft:wooded_badlands", 0x93602E);

        // Wetlands.
        put("minecraft:swamp", 0x556B4E);
        put("minecraft:mangrove_swamp", 0x486B4A);

        // Snow and mountains.
        put("minecraft:snowy_plains", 0xE9EBE6);
        put("minecraft:ice_spikes", 0xB7DCE0);
        put("minecraft:snowy_slopes", 0xCBD8D6);
        put("minecraft:grove", 0x7FA689);
        put("minecraft:frozen_peaks", 0xA9C4CC);
        put("minecraft:jagged_peaks", 0x9FB3B8);
        put("minecraft:stony_peaks", 0x8C8F86);

        // Caves.
        put("minecraft:dripstone_caves", 0x836349);
        put("minecraft:lush_caves", 0x3E7C4A);
        put("minecraft:deep_dark", 0x35363E);

        // Misc overworld.
        put("minecraft:mushroom_fields", 0xC65C9B);
        put("minecraft:the_void", 0x0A0A0C);

        // Nether.
        put("minecraft:nether_wastes", 0x6E3A34);
        put("minecraft:crimson_forest", 0x972A2A);
        put("minecraft:warped_forest", 0x2B8C82);
        put("minecraft:soul_sand_valley", 0x3E5155);
        put("minecraft:basalt_deltas", 0x5C555C);

        // The End.
        put("minecraft:the_end", 0xD9D8A8);
        put("minecraft:end_highlands", 0xC9CB93);
        put("minecraft:end_midlands", 0xD3D2A0);
        put("minecraft:small_end_islands", 0xDEDDB0);
        put("minecraft:end_barrens", 0xBDBC8C);
    }};

    /** Deterministic fallback for a biome not in {@link #BIOME_COLOURS} (a datapack biome). */
    private static final int[] FALLBACK_RAMP = {
        0x8F7A55, 0x6F8F6C, 0x8A6F8F, 0x6F8A8F, 0x8F8A6F, 0x7A6F8F,
    };

    /**
     * @return the curated colour for {@code biomeName}, or a colour deterministically chosen from
     * {@code biomeId} when the biome isn't in {@link #BIOME_COLOURS}
     */
    public static int colourFor(String biomeName, int biomeId) {
        Integer colour = BIOME_COLOURS.get(biomeName);
        if (colour != null) {
            return colour;
        }
        return FALLBACK_RAMP[Math.floorMod(biomeId, FALLBACK_RAMP.length)];
    }

    private BiomeColoursConfig() {
    }
}
