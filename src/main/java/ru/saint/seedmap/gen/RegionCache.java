package ru.saint.seedmap.gen;

import ru.saint.seedmap.SeedMapClient;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/** Generic async cache of markers per square region of chunks. */
public final class RegionCache {
	public interface Computer {
		List<Found> compute(int rx, int rz);
	}

	public final int regionChunks;
	public final int regionBlocks;
	private final DimGen gen;
	private final Computer computer;
	private final ConcurrentHashMap<Long, Region> regions = new ConcurrentHashMap<>();

	private static final class Region {
		volatile List<Found> list;
		volatile boolean queued;
		volatile long wanted;
	}

	RegionCache(DimGen gen, int regionChunks, Computer computer) {
		this.gen = gen;
		this.regionChunks = regionChunks;
		this.regionBlocks = regionChunks * 16;
		this.computer = computer;
	}

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
			r.list = computer.compute(rx, rz);
		} catch (Throwable t) {
			SeedMapClient.LOG.warn("SeedMap feature search failed", t);
			r.list = List.of();
		}
	}

	void clear() {
		regions.clear();
	}
}
