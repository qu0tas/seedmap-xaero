package ru.saint.seedmap.gen;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import ru.saint.seedmap.SeedMapClient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/** Biome map tiles (64x64 texels) at power-of-two levels of detail, generated off-thread. */
public final class BiomeTiles {
	public static final int SIZE = 64;
	public static final int MIN_SHIFT = 2;
	public static final int MAX_SHIFT = 10;
	private static final int MAX_TEXTURES = 700;
	private static final int UPLOADS_PER_FRAME = 24;

	private final DimGen gen;
	private final ConcurrentHashMap<Long, Tile> tiles = new ConcurrentHashMap<>();
	private int uploads;
	private int textures;

	public static final class Tile {
		public final int shift;
		public final int tx;
		public final int tz;
		volatile int[] pixels;
		volatile boolean queued;
		volatile boolean failed;
		volatile long wanted;
		DynamicTexture texture;
		long lastUsed;

		Tile(int shift, int tx, int tz) {
			this.shift = shift;
			this.tx = tx;
			this.tz = tz;
		}

		public DynamicTexture texture() {
			return texture;
		}
	}

	BiomeTiles(DimGen gen) {
		this.gen = gen;
	}

	public static int shiftFor(double scale) {
		double blocksPerTexel = 2.0 / Math.max(scale, 1e-6);
		int s = MIN_SHIFT;
		while ((1 << s) < blocksPerTexel && s < MAX_SHIFT) s++;
		return s;
	}

	private static long key(int shift, int tx, int tz) {
		return ((long) shift << 58) | (((long) tx & 0x1FFFFFFFL) << 29) | ((long) tz & 0x1FFFFFFFL);
	}

	public void beginFrame() {
		uploads = 0;
	}

	/** Render thread. Gets or creates a tile and uploads its pixels if they are ready. */
	public Tile tile(int shift, int tx, int tz, long now) {
		Tile t = tiles.computeIfAbsent(key(shift, tx, tz), k -> new Tile(shift, tx, tz));
		t.wanted = now;
		t.lastUsed = now;
		if (t.texture == null) {
			int[] px = t.pixels;
			if (px != null && uploads < UPLOADS_PER_FRAME) {
				upload(t, px);
				uploads++;
			}
		}
		return t;
	}

	public Tile peek(int shift, int tx, int tz, long now) {
		Tile t = tiles.get(key(shift, tx, tz));
		if (t == null || t.texture == null) return null;
		t.lastUsed = now;
		return t;
	}

	public void schedule(Tile t) {
		if (t.texture != null || t.pixels != null || t.queued || t.failed) return;
		t.queued = true;
		Exec.submit(() -> compute(t));
	}

	private void compute(Tile t) {
		try {
			if (gen.disposed) return;
			if (System.currentTimeMillis() - t.wanted > 1500L) return;
			int step = 1 << t.shift;
			int half = step >> 1;
			int x0 = t.tx * SIZE * step;
			int z0 = t.tz * SIZE * step;
			int[] px = new int[SIZE * SIZE];
			Holder<Biome> last = null;
			int lastColor = 0xFF000000;
			for (int j = 0; j < SIZE; j++) {
				if (gen.disposed) return;
				for (int i = 0; i < SIZE; i++) {
					Holder<Biome> b = gen.mapBiome(x0 + i * step + half, z0 + j * step + half);
					if (b != last) {
						last = b;
						lastColor = BiomeColors.argb(b);
					}
					px[j * SIZE + i] = lastColor;
				}
			}
			t.pixels = px;
		} catch (Throwable e) {
			t.failed = true;
			SeedMapClient.LOG.warn("SeedMap biome tile failed", e);
		} finally {
			t.queued = false;
		}
	}

	private void upload(Tile t, int[] px) {
		NativeImage img = new NativeImage(SIZE, SIZE, false);
		for (int j = 0; j < SIZE; j++) {
			for (int i = 0; i < SIZE; i++) img.setPixel(i, j, px[j * SIZE + i]);
		}
		t.texture = new DynamicTexture(() -> "seedmapxaero biome tile", img);
		t.pixels = null;
		textures++;
	}

	/** Render thread. Frees least recently used textures. */
	public void endFrame(long now) {
		if (textures <= MAX_TEXTURES && tiles.size() <= MAX_TEXTURES * 4) return;
		List<Tile> list = new ArrayList<>(tiles.values());
		list.sort(Comparator.comparingLong(t -> t.lastUsed));
		int freeTextures = Math.max(0, textures - MAX_TEXTURES * 3 / 4);
		int dropEntries = Math.max(0, tiles.size() - MAX_TEXTURES * 3);
		for (Tile t : list) {
			if (freeTextures <= 0 && dropEntries <= 0) break;
			if (t.lastUsed >= now) break;
			if (t.queued) continue;
			if (t.texture != null) {
				if (freeTextures <= 0) continue;
				t.texture.close();
				t.texture = null;
				textures--;
				freeTextures--;
			}
			tiles.remove(key(t.shift, t.tx, t.tz));
			dropEntries--;
		}
	}

	/** Render thread. */
	void dispose() {
		for (Tile t : tiles.values()) {
			if (t.texture != null) {
				t.texture.close();
				t.texture = null;
			}
		}
		tiles.clear();
		textures = 0;
	}
}
