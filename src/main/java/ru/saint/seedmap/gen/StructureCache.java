package ru.saint.seedmap.gen;

import ru.saint.seedmap.SeedMapClient;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public final class StructureCache {
	public static final int REGION_CHUNKS = 32;
	public static final int REGION_BLOCKS = REGION_CHUNKS * 16;

	private final DimGen gen;
	private final ConcurrentHashMap<Long, Region> regions = new ConcurrentHashMap<>();

	private static final class Region {
		volatile List<Found> list;
		volatile boolean queued;
		volatile long wanted;
	}

	StructureCache(DimGen gen) {
		this.gen = gen;
	}

	/** Structures of a 32x32-chunk region, or null while it is still being computed. */
	public List<Found> get(int rx, int rz, long now) {
		Region r = regions.computeIfAbsent(((long) rx << 32) ^ (rz & 0xFFFFFFFFL), k -> new Region());
		r.wanted = now;
		List<Found> l = r.list;
		if (l == null && !r.queued) {
			r.queued = true;
			Exec.submit(() -> compute(r, rx, rz));
		}
		return l;
	}

	private void compute(Region r, int rx, int rz) {
		if (gen.disposed) return;
		if (System.currentTimeMillis() - r.wanted > 2000L) {
			r.queued = false;
			return;
		}
		try {
			r.list = StructureFinder.find(gen, rx, rz);
		} catch (Throwable t) {
			SeedMapClient.LOG.warn("SeedMap structure search failed", t);
			r.list = List.of();
		}
	}

	void clear() {
		regions.clear();
	}
}
