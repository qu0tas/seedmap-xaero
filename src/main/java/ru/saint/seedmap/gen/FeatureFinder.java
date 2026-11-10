package ru.saint.seedmap.gen;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import ru.saint.seedmap.SeedMapClient;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Re-implements vanilla decoration placement (ChunkGenerator#applyBiomeDecoration) for a few
 * point features shown by chunkbase: desert wells, geodes, fossils, dungeons, end gateways, lava lakes.
 * Uses the same decoration/feature seeds and the same order of random calls as the placement modifiers
 * of the vanilla placed features. The final feature-specific terrain checks are not simulated,
 * so results are candidates ("approx").
 */
public final class FeatureFinder {
	private FeatureFinder() {}

	public record Spec(String id, PlacedFeature feature, int step, int index, boolean heavy) {}

	private static final String[] IDS = {
		"desert_well", "amethyst_geode", "fossil_upper", "fossil_lower",
		"end_gateway_return", "lake_lava_surface", "monster_room", "monster_room_deep"
	};

	static List<Spec> build(DimGen g) {
		List<Spec> out = new ArrayList<>();
		try {
			List<FeatureSorter.StepFeatureData> steps = steps(g);
			HolderLookup.RegistryLookup<PlacedFeature> reg = g.registryAccess.lookupOrThrow(Registries.PLACED_FEATURE);
			for (String id : IDS) {
				Optional<Holder.Reference<PlacedFeature>> h = reg.get(ResourceKey.create(Registries.PLACED_FEATURE, Identifier.withDefaultNamespace(id)));
				if (h.isEmpty()) continue;
				PlacedFeature pf = h.get().value();
				for (int s = 0; s < steps.size(); s++) {
					int idx = steps.get(s).features().indexOf(pf);
					if (idx >= 0) {
						out.add(new Spec(id, pf, s, idx, id.startsWith("monster_room")));
						break;
					}
				}
			}
		} catch (Throwable t) {
			SeedMapClient.LOG.warn("SeedMap: cannot prepare feature placement", t);
		}
		SeedMapClient.LOG.info("SeedMap: {} decoration features available for {}", out.size(), g.dim.identifier());
		return out;
	}

	@SuppressWarnings("unchecked")
	private static List<FeatureSorter.StepFeatureData> steps(DimGen g) {
		try {
			Field f = ChunkGenerator.class.getDeclaredField("featuresPerStep");
			f.setAccessible(true);
			Object v = f.get(g.generator);
			if (v instanceof Supplier<?> s) return (List<FeatureSorter.StepFeatureData>) s.get();
		} catch (Throwable t) {
			SeedMapClient.LOG.debug("featuresPerStep reflection failed", t);
		}
		return FeatureSorter.buildFeaturesPerStep(List.copyOf(g.biomeSource.possibleBiomes()),
			b -> b.value().getGenerationSettings().features(), true);
	}

	public static List<Found> find(DimGen g, int rx, int rz, int size, boolean heavy) {
		List<Found> out = new ArrayList<>();
		List<Spec> specs = g.featureSpecs();
		if (specs.isEmpty()) return out;
		boolean any = false;
		for (Spec s : specs) if (s.heavy() == heavy) any = true;
		if (!any) return out;
		WorldgenRandom rnd = new WorldgenRandom(new XoroshiroRandomSource(0L));
		int cx0 = rx * size, cz0 = rz * size;
		for (int cx = cx0; cx < cx0 + size; cx++) {
			if (g.disposed) return out;
			for (int cz = cz0; cz < cz0 + size; cz++) {
				int bx = cx << 4, bz = cz << 4;
				long deco = rnd.setDecorationSeed(g.seed, bx, bz);
				for (Spec sp : specs) {
					if (sp.heavy() != heavy) continue;
					rnd.setFeatureSeed(deco, sp.index(), sp.step());
					try {
						place(g, sp, rnd, bx, bz, out);
					} catch (Throwable t) {
						SeedMapClient.LOG.debug("feature placement failed", t);
					}
				}
			}
		}
		return out;
	}

	private static boolean rarity(WorldgenRandom r, int chance) {
		return r.nextFloat() < 1.0F / (float) chance;
	}

	private static int uniform(WorldgenRandom r, int lo, int hi) {
		if (lo > hi) return lo;
		return r.nextInt(hi - lo + 1) + lo;
	}

	private static boolean biome(DimGen g, Spec sp, int x, int y, int z) {
		Holder<Biome> b = g.zoomedBiome(x, y, z);
		return b.value().getGenerationSettings().hasFeature(sp.feature());
	}

	private static void place(DimGen g, Spec sp, WorldgenRandom r, int bx, int bz, List<Found> out) {
		final int minY = g.minGenY();
		final int top = g.topY();
		final String sid = "minecraft:" + sp.id();
		switch (sp.id()) {
			case "desert_well" -> {
				if (!rarity(r, 1000)) return;
				int x = r.nextInt(16) + bx, z = r.nextInt(16) + bz;
				int y = g.height(x, z, Heightmap.Types.MOTION_BLOCKING);
				if (y <= minY || !biome(g, sp, x, y, z)) return;
				out.add(new Found(Kind.DESERT_WELL, sid, x, y, z, true, true));
			}
			case "amethyst_geode" -> {
				if (!rarity(r, 24)) return;
				int x = r.nextInt(16) + bx, z = r.nextInt(16) + bz;
				int y = uniform(r, minY + 6, 30);
				if (!biome(g, sp, x, y, z)) return;
				out.add(new Found(Kind.GEODE, sid, x, y, z, true, true));
			}
			case "fossil_upper", "fossil_lower" -> {
				if (!rarity(r, 64)) return;
				int x = r.nextInt(16) + bx, z = r.nextInt(16) + bz;
				int y = sp.id().equals("fossil_upper") ? uniform(r, 0, top) : uniform(r, minY, -8);
				if (!biome(g, sp, x, y, z)) return;
				int fy = Math.max(g.height(x, z, Heightmap.Types.OCEAN_FLOOR_WG) - 20, minY + 10);
				out.add(new Found(Kind.FOSSIL, sid, x, fy, z, false, true));
			}
			case "end_gateway_return" -> {
				if (!rarity(r, 700)) return;
				int x = r.nextInt(16) + bx, z = r.nextInt(16) + bz;
				int h = g.height(x, z, Heightmap.Types.MOTION_BLOCKING);
				if (h <= minY) return;
				int y = h + uniform(r, 3, 9);
				if (!biome(g, sp, x, y, z)) return;
				out.add(new Found(Kind.END_GATEWAY, sid, x, y, z, true, true));
			}
			case "lake_lava_surface" -> {
				if (!rarity(r, 200)) return;
				int x = r.nextInt(16) + bx, z = r.nextInt(16) + bz;
				int y = g.height(x, z, Heightmap.Types.WORLD_SURFACE_WG);
				if (y <= minY + 4 || !biome(g, sp, x, y, z)) return;
				out.add(new Found(Kind.LAVA_POOL, sid, x, y, z, true, true));
			}
			case "monster_room" -> dungeons(g, sp, r, bx, bz, 10, 0, top, sid, out);
			case "monster_room_deep" -> dungeons(g, sp, r, bx, bz, 4, minY + 6, -1, sid, out);
			default -> { }
		}
	}

	private static void dungeons(DimGen g, Spec sp, WorldgenRandom r, int bx, int bz, int count, int lo, int hi, String sid, List<Found> out) {
		for (int i = 0; i < count; i++) {
			int x = r.nextInt(16) + bx;
			int z = r.nextInt(16) + bz;
			int y = uniform(r, lo, hi);
			if (!biome(g, sp, x, y, z)) continue;
			if (!cavePocket(g, x, y, z)) continue;
			out.add(new Found(Kind.DUNGEON, sid, x, y, z, true, true));
		}
	}

	/** Air at y, solid floor below and solid ceiling above: a cave pocket where a dungeon can fit. */
	private static boolean cavePocket(DimGen g, int x, int y, int z) {
		NoiseColumn col = g.column(x, z);
		if (col == null) return false;
		BlockState a = col.getBlock(y);
		if (!a.isAir()) return false;
		BlockState floor = col.getBlock(y - 1);
		if (floor.isAir() || !floor.getFluidState().isEmpty()) return false;
		BlockState ceil = col.getBlock(y + 4);
		return !ceil.isAir() && ceil.getFluidState().isEmpty();
	}
}
