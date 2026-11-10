package ru.saint.seedmap.gen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.tags.TagLoader;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.LevelStorageSource;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import ru.saint.seedmap.SeedMapClient;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ForkJoinPool;

/**
 * Builds world generators. Singleplayer: reuses the integrated server's generator (exact, incl. datapacks).
 * Multiplayer: builds a vanilla generator for the entered seed from the built-in vanilla data pack.
 */
public final class Worldgen {
	private record Vanilla(RegistryAccess.Frozen ra, StructureTemplateManager templates) {}

	private static CompletableFuture<Vanilla> vanilla;
	private static DimGen current;
	private static CompletableFuture<DimGen> building;
	private static String buildingKey;
	private static String failedKey;
	private static volatile String lastError;

	private Worldgen() {}

	public static String lastError() {
		return lastError;
	}

	public static void retry() {
		failedKey = null;
		lastError = null;
	}

	/** Render thread. Returns the ready generator or null while it is being prepared. */
	public static DimGen get(String serverKey, long seed, ResourceKey<Level> dim) {
		String key = serverKey + "|" + seed + "|" + dim.identifier();
		if (current != null && current.key.equals(key)) return current;
		if (building != null && building.isDone()) {
			try {
				DimGen g = building.join();
				if (key.equals(buildingKey)) {
					if (current != null) current.dispose();
					current = g;
				} else {
					g.dispose();
				}
			} catch (Throwable t) {
				Throwable c = t instanceof CompletionException && t.getCause() != null ? t.getCause() : t;
				lastError = c.getClass().getSimpleName() + ": " + c.getMessage();
				failedKey = buildingKey;
				SeedMapClient.LOG.error("SeedMap: failed to prepare world generator", c);
			}
			building = null;
			buildingKey = null;
			if (current != null && current.key.equals(key)) return current;
		}
		if (building != null || key.equals(failedKey)) return null;
		start(key, seed, dim);
		return null;
	}

	private static void start(String key, long seed, ResourceKey<Level> dim) {
		lastError = null;
		buildingKey = key;
		IntegratedServer sp = Minecraft.getInstance().getSingleplayerServer();
		if (sp != null) {
			building = CompletableFuture.supplyAsync(() -> fromServer(sp, dim, key), Exec.POOL);
		} else {
			building = vanilla().thenApplyAsync(v -> fromRegistries(v.ra(), v.templates(), seed, dim, key), Exec.POOL);
		}
	}

	private static synchronized CompletableFuture<Vanilla> vanilla() {
		if (vanilla == null || vanilla.isCompletedExceptionally()) {
			vanilla = CompletableFuture.supplyAsync(Worldgen::loadVanilla, Exec.POOL);
		}
		return vanilla;
	}

	private static Vanilla loadVanilla() {
		long t0 = System.currentTimeMillis();
		PackRepository repo = ServerPacksSource.createVanillaTrustedRepository();
		repo.reload();
		repo.setSelected(List.of("vanilla"));
		// The resource manager stays open: structure templates (ruined portals, end ships) are read lazily from it.
		MultiPackResourceManager rm = new MultiPackResourceManager(PackType.SERVER_DATA, repo.openAllSelected());
		try {
			LayeredRegistryAccess<RegistryLayer> layered = RegistryLayer.createRegistryAccess();
			List<Registry.PendingTags<?>> pending = TagLoader.loadTagsForExistingRegistries(rm, layered.getLayer(RegistryLayer.STATIC));
			List<HolderLookup.RegistryLookup<?>> lookups = TagLoader.buildUpdatedLookups(layered.getAccessForLoading(RegistryLayer.WORLDGEN), pending);
			RegistryAccess.Frozen frozen = RegistryDataLoader.load(rm, lookups, RegistryDataLoader.WORLDGEN_REGISTRIES, ForkJoinPool.commonPool()).join();
			SeedMapClient.LOG.info("SeedMap: vanilla worldgen registries loaded in {} ms", System.currentTimeMillis() - t0);
			StructureTemplateManager templates = makeTemplates(rm);
			if (templates == null) rm.close();
			return new Vanilla(frozen, templates);
		} catch (Throwable t) {
			rm.close();
			throw t;
		}
	}

	private static StructureTemplateManager makeTemplates(MultiPackResourceManager rm) {
		try {
			Path dir = Files.createTempDirectory("seedmapxaero");
			dir.toFile().deleteOnExit();
			LevelStorageSource.LevelStorageAccess access = LevelStorageSource.createDefault(dir).createAccess("templates");
			return new StructureTemplateManager(rm, access, DataFixers.getDataFixer(), BuiltInRegistries.BLOCK);
		} catch (Throwable t) {
			SeedMapClient.LOG.warn("SeedMap: structure templates unavailable in multiplayer, using approximations", t);
			return null;
		}
	}

	private static DimGen fromServer(IntegratedServer server, ResourceKey<Level> dim, String key) {
		ServerLevel level = server.getLevel(dim);
		if (level == null) throw new IllegalStateException("Dimension is not loaded: " + dim.identifier());
		ServerChunkCache cs = level.getChunkSource();
		return new DimGen(key, dim, level.getSeed(), server.registryAccess(), cs.getGenerator(), cs.randomState(),
			cs.getGeneratorState(), level, server.getStructureManager(), true);
	}

	private static DimGen fromRegistries(RegistryAccess.Frozen ra, StructureTemplateManager templates, long seed, ResourceKey<Level> dim, String key) {
		WorldDimensions dims = ra.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.NORMAL).value().createWorldDimensions();
		LevelStem stem = dims.get(Registries.levelToLevelStem(dim))
			.orElseThrow(() -> new IllegalStateException("Unsupported dimension: " + dim.identifier()));
		ChunkGenerator gen = stem.generator();
		NoiseGeneratorSettings settings = gen instanceof NoiseBasedChunkGenerator nb ? nb.generatorSettings().value() : NoiseGeneratorSettings.dummy();
		RandomState rs = RandomState.create(settings, ra.lookupOrThrow(Registries.NOISE), seed);
		ChunkGeneratorStructureState ss = gen.createState(ra.lookupOrThrow(Registries.STRUCTURE_SET), rs, seed);
		DimensionType dt = stem.type().value();
		return new DimGen(key, dim, seed, ra, gen, rs, ss, LevelHeightAccessor.create(dt.minY(), dt.height()), templates, false);
	}
}
