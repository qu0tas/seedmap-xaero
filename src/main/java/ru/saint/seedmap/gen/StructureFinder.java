package ru.saint.seedmap.gen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import ru.saint.seedmap.SeedMapClient;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Re-implements vanilla structure placement (ChunkGenerator#createStructures) without generating chunks. */
public final class StructureFinder {
	private StructureFinder() {}

	private static final Field TEMPLATE_NAME = templateNameField();

	private static Field templateNameField() {
		try {
			Field f = TemplateStructurePiece.class.getDeclaredField("templateName");
			f.setAccessible(true);
			return f;
		} catch (Throwable t) {
			SeedMapClient.LOG.warn("SeedMap: end ship detection unavailable", t);
			return null;
		}
	}

	/** Builds the end city pieces (needs templates) and checks whether one of them is the ship. */
	private static boolean hasShip(DimGen g, Holder<Structure> holder, ChunkPos cp, long seed) {
		if (TEMPLATE_NAME == null || g.templates == null) return false;
		try {
			Structure s = holder.value();
			StructureStart st = s.generate(holder, g.dim, g.registryAccess, g.generator, g.biomeSource, g.randomState,
				g.templates, seed, cp, 0, g.heightAccessor, s.biomes()::contains);
			if (!st.isValid()) return false;
			for (StructurePiece piece : st.getPieces()) {
				if (piece instanceof TemplateStructurePiece tp) {
					Object n = TEMPLATE_NAME.get(tp);
					if (n != null && n.toString().endsWith("ship")) return true;
				}
			}
		} catch (Throwable t) {
			SeedMapClient.LOG.debug("end ship check failed", t);
		}
		return false;
	}

	public static List<Found> find(DimGen g, int rx, int rz) {
		final int cx0 = rx * StructureCache.REGION_CHUNKS;
		final int cz0 = rz * StructureCache.REGION_CHUNKS;
		final int cx1 = cx0 + StructureCache.REGION_CHUNKS;
		final int cz1 = cz0 + StructureCache.REGION_CHUNKS;
		ChunkGeneratorStructureState ss = g.structureState;
		long seed = ss.getLevelSeed();
		List<Found> out = new ArrayList<>();
		for (Holder<StructureSet> setHolder : ss.possibleStructureSets()) {
			if (g.disposed) return out;
			StructureSet set = setHolder.value();
			StructurePlacement placement = set.placement();
			try {
				if (placement instanceof RandomSpreadStructurePlacement rsp) {
					int sp = rsp.spacing();
					int gx0 = Math.floorDiv(cx0, sp), gx1 = Math.floorDiv(cx1 - 1, sp);
					int gz0 = Math.floorDiv(cz0, sp), gz1 = Math.floorDiv(cz1 - 1, sp);
					for (int gx = gx0; gx <= gx1; gx++) {
						for (int gz = gz0; gz <= gz1; gz++) {
							ChunkPos cp = rsp.getPotentialStructureChunk(seed, gx * sp, gz * sp);
							if (cp.x() < cx0 || cp.x() >= cx1 || cp.z() < cz0 || cp.z() >= cz1) continue;
							if (!placement.isStructureChunk(ss, cp.x(), cp.z())) continue;
							select(g, set, cp, seed, out);
						}
					}
				} else if (placement instanceof ConcentricRingsStructurePlacement crp) {
					List<ChunkPos> ring = ss.getRingPositionsFor(crp);
					if (ring == null) continue;
					for (ChunkPos cp : ring) {
						if (cp.x() < cx0 || cp.x() >= cx1 || cp.z() < cz0 || cp.z() >= cz1) continue;
						if (!placement.isStructureChunk(ss, cp.x(), cp.z())) continue;
						select(g, set, cp, seed, out);
					}
				} else {
					for (int cx = cx0; cx < cx1; cx++) {
						for (int cz = cz0; cz < cz1; cz++) {
							if (placement.isStructureChunk(ss, cx, cz)) select(g, set, new ChunkPos(cx, cz), seed, out);
						}
					}
				}
			} catch (Throwable t) {
				SeedMapClient.LOG.debug("Structure set failed", t);
			}
		}
		return out;
	}

	private static void select(DimGen g, StructureSet set, ChunkPos cp, long seed, List<Found> out) {
		List<StructureSet.StructureSelectionEntry> entries = set.structures();
		if (entries.isEmpty()) return;
		if (entries.size() == 1) {
			Found f = test(g, entries.get(0).structure(), cp, seed);
			if (f != null) out.add(f);
			return;
		}
		List<StructureSet.StructureSelectionEntry> list = new ArrayList<>(entries);
		WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
		random.setLargeFeatureSeed(seed, cp.x(), cp.z());
		int total = 0;
		for (StructureSet.StructureSelectionEntry e : list) total += e.weight();
		while (!list.isEmpty() && total > 0) {
			int r = random.nextInt(total);
			int idx = 0;
			for (StructureSet.StructureSelectionEntry e : list) {
				r -= e.weight();
				if (r < 0) break;
				idx++;
			}
			if (idx >= list.size()) idx = list.size() - 1;
			StructureSet.StructureSelectionEntry chosen = list.get(idx);
			Found f = test(g, chosen.structure(), cp, seed);
			if (f != null) {
				out.add(f);
				return;
			}
			list.remove(idx);
			total -= chosen.weight();
		}
	}

	private static Found test(DimGen g, Holder<Structure> holder, ChunkPos cp, long seed) {
		Structure s = holder.value();
		Identifier rid = holder.unwrapKey().map(ResourceKey::identifier).orElse(null);
		String id = rid == null ? "unknown" : rid.toString();
		Kind kind = Kind.fromStructure(rid);
		boolean jigsaw = s.type() == StructureType.JIGSAW;
		boolean needsTemplates = jigsaw || kind == Kind.RUINED_PORTAL;
		if (!needsTemplates || (g.templates != null && !jigsaw)) {
			try {
				Structure.GenerationContext ctx = new Structure.GenerationContext(g.registryAccess, g.generator, g.biomeSource,
					g.randomState, g.templates, seed, cp, g.heightAccessor, s.biomes()::contains);
				Optional<Structure.GenerationStub> stub = s.findValidGenerationPoint(ctx);
				if (stub.isEmpty()) return null;
				BlockPos p = stub.get().position();
				Kind k = kind;
				if (kind == Kind.END_CITY && hasShip(g, holder, cp, seed)) k = Kind.END_CITY_SHIP;
				return new Found(k, id, p.getX(), p.getY(), p.getZ(), true, false);
			} catch (Throwable ignored) {
				// fall back to a biome check below
			}
		}
		int x = cp.getMinBlockX() + 8;
		int z = cp.getMinBlockZ() + 8;
		int y = kind.fixedY != Integer.MIN_VALUE ? kind.fixedY : g.surfaceHeight(x, z);
		Holder<Biome> biome = g.biomeAt(x, y, z);
		if (!s.biomes().contains(biome)) return null;
		return new Found(kind, id, x, y, z, true, true);
	}
}
