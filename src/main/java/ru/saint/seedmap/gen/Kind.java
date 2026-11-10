package ru.saint.seedmap.gen;

import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import ru.saint.seedmap.SeedMapConfig;

/** Structure kinds. dims bitmask: 1 = overworld, 2 = nether, 4 = end. */
public enum Kind {
	VILLAGE("village", 0, true, 1, Integer.MIN_VALUE),
	OUTPOST("outpost", 1, false, 1, Integer.MIN_VALUE),
	MANSION("mansion", 2, true, 1, Integer.MIN_VALUE),
	JUNGLE_TEMPLE("jungle_temple", 3, false, 1, Integer.MIN_VALUE),
	DESERT_TEMPLE("desert_temple", 4, false, 1, Integer.MIN_VALUE),
	WITCH_HUT("witch_hut", 5, false, 1, Integer.MIN_VALUE),
	IGLOO("igloo", 6, false, 1, Integer.MIN_VALUE),
	SHIPWRECK("shipwreck", 7, false, 1, Integer.MIN_VALUE),
	OCEAN_RUINS("ocean_ruins", 8, false, 1, Integer.MIN_VALUE),
	TREASURE("treasure", 9, true, 1, Integer.MIN_VALUE),
	MONUMENT("monument", 10, false, 1, Integer.MIN_VALUE),
	MINESHAFT("mineshaft", 11, false, 1, Integer.MIN_VALUE),
	STRONGHOLD("stronghold", 12, false, 1, Integer.MIN_VALUE),
	ANCIENT_CITY("ancient_city", 13, true, 1, -27),
	TRAIL_RUINS("trail_ruins", 14, false, 1, Integer.MIN_VALUE),
	TRIAL_CHAMBERS("trial_chambers", 15, true, 1, -30),
	RUINED_PORTAL("ruined_portal", 16, true, 3, Integer.MIN_VALUE),
	FORTRESS("fortress", 17, true, 2, 64),
	BASTION("bastion", 18, true, 2, 33),
	NETHER_FOSSIL("nether_fossil", 19, false, 2, 40),
	END_CITY("end_city", 20, true, 4, Integer.MIN_VALUE),
	END_CITY_SHIP("end_city_ship", 30, true, 4, Integer.MIN_VALUE),
	END_GATEWAY("end_gateway", 28, true, 4, Integer.MIN_VALUE),
	DESERT_WELL("desert_well", 24, true, 1, Integer.MIN_VALUE),
	GEODE("geode", 25, false, 1, Integer.MIN_VALUE),
	DUNGEON("dungeon", 26, false, 1, Integer.MIN_VALUE),
	FOSSIL("fossil", 27, false, 1, Integer.MIN_VALUE),
	LAVA_POOL("lava_pool", 29, false, 1, Integer.MIN_VALUE),
	OTHER("other", 21, true, 7, Integer.MIN_VALUE);

	/** Decoration features computed by FeatureFinder (cheap ones, region cache "features"). */
	public static final Kind[] LIGHT_FEATURES = {END_GATEWAY, DESERT_WELL, GEODE, FOSSIL, LAVA_POOL};

	public final String id;
	public final int icon;
	public final boolean def;
	public final int dims;
	public final int fixedY;

	Kind(String id, int icon, boolean def, int dims, int fixedY) {
		this.id = id;
		this.icon = icon;
		this.def = def;
		this.dims = dims;
		this.fixedY = fixedY;
	}

	public String langKey() {
		return "seedmapxaero.kind." + id;
	}

	public String layerId() {
		return "kind." + id;
	}

	public boolean enabled() {
		return SeedMapConfig.INSTANCE.isOn(layerId(), def);
	}

	public static Kind byId(String id) {
		if (id == null) return null;
		for (Kind k : values()) if (k.id.equals(id)) return k;
		return null;
	}

	public static int dimBit(ResourceKey<Level> dim) {
		if (Level.OVERWORLD.equals(dim)) return 1;
		if (Level.NETHER.equals(dim)) return 2;
		if (Level.END.equals(dim)) return 4;
		return 7;
	}

	public static Kind fromStructure(Identifier id) {
		if (id == null || !"minecraft".equals(id.getNamespace())) return OTHER;
		String p = id.getPath();
		if (p.startsWith("village_")) return VILLAGE;
		if (p.startsWith("shipwreck")) return SHIPWRECK;
		if (p.startsWith("ocean_ruin")) return OCEAN_RUINS;
		if (p.startsWith("mineshaft")) return MINESHAFT;
		if (p.startsWith("ruined_portal")) return RUINED_PORTAL;
		switch (p) {
			case "pillager_outpost": return OUTPOST;
			case "mansion": return MANSION;
			case "jungle_pyramid": return JUNGLE_TEMPLE;
			case "desert_pyramid": return DESERT_TEMPLE;
			case "swamp_hut": return WITCH_HUT;
			case "igloo": return IGLOO;
			case "buried_treasure": return TREASURE;
			case "monument": return MONUMENT;
			case "stronghold": return STRONGHOLD;
			case "ancient_city": return ANCIENT_CITY;
			case "trail_ruins": return TRAIL_RUINS;
			case "trial_chambers": return TRIAL_CHAMBERS;
			case "fortress": return FORTRESS;
			case "bastion_remnant": return BASTION;
			case "nether_fossil": return NETHER_FOSSIL;
			case "end_city": return END_CITY;
			default: return OTHER;
		}
	}
}
