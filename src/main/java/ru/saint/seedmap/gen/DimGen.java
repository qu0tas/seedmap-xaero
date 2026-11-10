package ru.saint.seedmap.gen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Everything needed to simulate world generation of one dimension for one seed. */
public final class DimGen {
	public final String key;
	public final ResourceKey<Level> dim;
	public final long seed;
	public final RegistryAccess registryAccess;
	public final ChunkGenerator generator;
	public final BiomeSource biomeSource;
	public final RandomState randomState;
	public final Climate.Sampler sampler;
	public final ChunkGeneratorStructureState structureState;
	public final LevelHeightAccessor heightAccessor;
	public final StructureTemplateManager templates;
	public final boolean exact;
	public final boolean overworld;
	public final int biomeQuartY;
	public final StructureCache structures;
	public final BiomeTiles biomes;
	/** Light decoration features (wells, geodes, fossils, gateways, lava lakes). */
	public final RegionCache features;
	/** Dungeons need terrain columns, so they use small regions and are only shown zoomed in. */
	public final RegionCache dungeons;
	public final BiomeManager biomeManager;
	private volatile List<FeatureFinder.Spec> featureSpecs;
	public volatile boolean disposed;
	private volatile BlockPos spawn;
	private final AtomicBoolean spawnRequested = new AtomicBoolean();

	DimGen(String key, ResourceKey<Level> dim, long seed, RegistryAccess registryAccess, ChunkGenerator generator,
		   RandomState randomState, ChunkGeneratorStructureState structureState, LevelHeightAccessor heightAccessor,
		   StructureTemplateManager templates, boolean exact) {
		this.key = key;
		this.dim = dim;
		this.seed = seed;
		this.registryAccess = registryAccess;
		this.generator = generator;
		this.biomeSource = generator.getBiomeSource();
		this.randomState = randomState;
		this.sampler = randomState.sampler();
		this.structureState = structureState;
		this.heightAccessor = heightAccessor;
		this.templates = templates;
		this.exact = exact;
		this.overworld = Level.OVERWORLD.equals(dim);
		this.biomeQuartY = overworld ? 64 : 16;
		this.structures = new StructureCache(this);
		this.biomes = new BiomeTiles(this);
		this.features = new RegionCache(this, 32, (rx, rz) -> FeatureFinder.find(this, rx, rz, 32, false));
		this.dungeons = new RegionCache(this, 8, (rx, rz) -> FeatureFinder.find(this, rx, rz, 8, true));
		final int qMin = heightAccessor.getMinY() >> 2;
		final int qMax = (heightAccessor.getMinY() + heightAccessor.getHeight() - 1) >> 2;
		this.biomeManager = new BiomeManager((qx, qy, qz) -> this.biomeSource.getNoiseBiome(qx, Math.max(qMin, Math.min(qMax, qy)), qz, this.sampler),
			BiomeManager.obfuscateSeed(seed));
	}

	public List<FeatureFinder.Spec> featureSpecs() {
		List<FeatureFinder.Spec> s = featureSpecs;
		if (s == null) {
			synchronized (this) {
				if (featureSpecs == null) featureSpecs = FeatureFinder.build(this);
				s = featureSpecs;
			}
		}
		return s;
	}

	/** Biome exactly as vanilla features see it (with the biome zoom "fuzz"). */
	public Holder<Biome> zoomedBiome(int x, int y, int z) {
		return biomeManager.getBiome(new BlockPos(x, y, z));
	}

	public int minGenY() {
		return Math.max(heightAccessor.getMinY(), generator.getMinY());
	}

	public int topY() {
		return minGenY() + Math.min(heightAccessor.getHeight(), generator.getGenDepth()) - 1;
	}

	public int height(int x, int z, Heightmap.Types type) {
		try {
			return generator.getBaseHeight(x, z, type, heightAccessor, randomState);
		} catch (Throwable t) {
			return heightAccessor.getMinY();
		}
	}

	public NoiseColumn column(int x, int z) {
		try {
			return generator.getBaseColumn(x, z, heightAccessor, randomState);
		} catch (Throwable t) {
			return null;
		}
	}

	/** Surface biome for the flat seed map (sampled above the terrain, like chunkbase). */
	public Holder<Biome> mapBiome(int x, int z) {
		return biomeSource.getNoiseBiome(x >> 2, biomeQuartY, z >> 2, sampler);
	}

	public Holder<Biome> biomeAt(int x, int y, int z) {
		return biomeSource.getNoiseBiome(x >> 2, y >> 2, z >> 2, sampler);
	}

	public int surfaceHeight(int x, int z) {
		try {
			return generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, heightAccessor, randomState);
		} catch (Throwable t) {
			return 64;
		}
	}

	public BlockPos spawn() {
		if (!overworld) return null;
		BlockPos p = spawn;
		if (p == null && spawnRequested.compareAndSet(false, true)) {
			Exec.submit(() -> spawn = sampler.findSpawnPosition());
		}
		return p;
	}

	/** Render thread only. */
	public void dispose() {
		disposed = true;
		structures.clear();
		features.clear();
		dungeons.clear();
		biomes.dispose();
	}
}
