package ru.saint.seedmap.gen;

import ru.saint.seedmap.SeedMapClient;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Small LIFO worker pool: the newest requests (what the user looks at now) run first. */
public final class Exec {
	private static final AtomicInteger PENDING = new AtomicInteger();
	public static final ExecutorService POOL;

	static {
		int n = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() - 2));
		LinkedBlockingDeque<Runnable> queue = new LinkedBlockingDeque<>() {
			@Override
			public boolean offer(Runnable r) {
				return offerFirst(r);
			}
		};
		AtomicInteger ids = new AtomicInteger();
		POOL = new ThreadPoolExecutor(n, n, 30L, TimeUnit.SECONDS, queue, r -> {
			Thread t = new Thread(r, "SeedMap-Worker-" + ids.incrementAndGet());
			t.setDaemon(true);
			t.setPriority(Thread.NORM_PRIORITY - 2);
			return t;
		});
	}

	private Exec() {}

	public static void submit(Runnable task) {
		PENDING.incrementAndGet();
		POOL.execute(() -> {
			try {
				task.run();
			} catch (Throwable t) {
				SeedMapClient.LOG.warn("SeedMap task failed", t);
			} finally {
				PENDING.decrementAndGet();
			}
		});
	}

	public static int pending() {
		return PENDING.get();
	}
}
