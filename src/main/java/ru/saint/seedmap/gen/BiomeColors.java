package ru.saint.seedmap.gen;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Biome colors close to the chunkbase / cubiomes palette. */
public final class BiomeColors {
	private static final Map<String, Integer> RGB = new HashMap<>();
	private static final ConcurrentHashMap<Holder<Biome>, Integer> CACHE = new ConcurrentHashMap<>();

	private static void put(String id, int rgb) {
		RGB.put(id, rgb);
	}

	static {
		put("ocean", 0x000070); put("deep_ocean", 0x000030); put("warm_ocean", 0x0000AC); put("lukewarm_ocean", 0x000090);
		put("deep_lukewarm_ocean", 0x000040); put("cold_ocean", 0x202070); put("deep_cold_ocean", 0x202038);
		put("frozen_ocean", 0x7070D6); put("deep_frozen_ocean", 0x404090); put("river", 0x0000FF); put("frozen_river", 0xA0A0FF);
		put("plains", 0x8DB360); put("sunflower_plains", 0xB5DB88); put("snowy_plains", 0xFFFFFF); put("ice_spikes", 0xB4DCDC);
		put("desert", 0xFA9418); put("swamp", 0x07F9B2); put("mangrove_swamp", 0x2CCC8E);
		put("forest", 0x056621); put("flower_forest", 0x2D8E49); put("birch_forest", 0x307444); put("old_growth_birch_forest", 0x589C6C);
		put("dark_forest", 0x40511A); put("pale_garden", 0x7B8378); put("taiga", 0x0B6A5F); put("snowy_taiga", 0x31554A);
		put("old_growth_pine_taiga", 0x596651); put("old_growth_spruce_taiga", 0x818E79);
		put("jungle", 0x537B09); put("sparse_jungle", 0x628B17); put("bamboo_jungle", 0x768E14);
		put("savanna", 0xBDB25F); put("savanna_plateau", 0xA79D64); put("windswept_savanna", 0xE5DA87);
		put("badlands", 0xD94515); put("wooded_badlands", 0xB09765); put("eroded_badlands", 0xFF6D3D);
		put("windswept_hills", 0x606060); put("windswept_forest", 0x5B7352); put("windswept_gravelly_hills", 0x888888);
		put("meadow", 0x60A445); put("cherry_grove", 0xFF91C8); put("grove", 0x47726C); put("snowy_slopes", 0xC4C4C4);
		put("jagged_peaks", 0xDCDCC8); put("frozen_peaks", 0xB0B3CE); put("stony_peaks", 0x7B8F74);
		put("beach", 0xFADE55); put("snowy_beach", 0xFAF0C0); put("stony_shore", 0xA2A284); put("mushroom_fields", 0xFF00FF);
		put("dripstone_caves", 0x4E3012); put("lush_caves", 0x283C00); put("deep_dark", 0x031F29);
		put("nether_wastes", 0x572526); put("soul_sand_valley", 0x4D3A2E); put("crimson_forest", 0x981A11);
		put("warped_forest", 0x49907B); put("basalt_deltas", 0x645F63);
		put("the_end", 0x8080FF); put("small_end_islands", 0x4B4BAB); put("end_midlands", 0xC9C959);
		put("end_highlands", 0xB5B536); put("end_barrens", 0x7070CC); put("the_void", 0x000000);
	}

	private BiomeColors() {}

	public static int argb(Holder<Biome> biome) {
		Integer c = CACHE.get(biome);
		if (c != null) return c;
		int v = compute(biome);
		CACHE.put(biome, v);
		return v;
	}

	private static int compute(Holder<Biome> biome) {
		String ns = biome.unwrapKey().map(k -> k.identifier().getNamespace()).orElse("");
		String path = biome.unwrapKey().map(k -> k.identifier().getPath()).orElse("");
		Integer rgb = "minecraft".equals(ns) ? RGB.get(path) : null;
		if (rgb == null) {
			int h = (ns + ":" + path).hashCode();
			int r = 64 + ((h >> 16) & 0x7F), g = 64 + ((h >> 8) & 0x7F), b = 64 + (h & 0x7F);
			rgb = (r << 16) | (g << 8) | b;
		}
		return 0xFF000000 | rgb;
	}
}
