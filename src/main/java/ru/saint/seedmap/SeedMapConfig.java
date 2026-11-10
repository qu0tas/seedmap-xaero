package ru.saint.seedmap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SeedMapConfig {
	public static SeedMapConfig INSTANCE = new SeedMapConfig();
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	public String mode = "XAERO";
	public int iconSize = 14;
	public Map<String, Boolean> layers = new LinkedHashMap<>();
	public Map<String, String> seeds = new LinkedHashMap<>();
	public List<Waypoint> waypoints = new ArrayList<>();

	public static final class Waypoint {
		public String server;
		public String dim;
		public int x;
		public int y;
		public int z;
		public boolean hasY;
		public String kind;
		public String label;
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("seedmapxaero").resolve("config.json");
	}

	public static void load() {
		try {
			Path f = file();
			if (Files.exists(f)) {
				SeedMapConfig c = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), SeedMapConfig.class);
				if (c != null) INSTANCE = c;
			}
		} catch (Exception e) {
			SeedMapClient.LOG.warn("Failed to read SeedMap config", e);
		}
		INSTANCE.fix();
	}

	public static void save() {
		try {
			Path f = file();
			Files.createDirectories(f.getParent());
			Files.writeString(f, GSON.toJson(INSTANCE), StandardCharsets.UTF_8);
		} catch (Exception e) {
			SeedMapClient.LOG.warn("Failed to save SeedMap config", e);
		}
	}

	private void fix() {
		if (mode == null) mode = "XAERO";
		if (layers == null) layers = new LinkedHashMap<>();
		if (seeds == null) seeds = new LinkedHashMap<>();
		if (waypoints == null) waypoints = new ArrayList<>();
		waypoints.removeIf(w -> w == null || w.server == null || w.dim == null);
		if (iconSize < 8 || iconSize > 32) iconSize = 14;
	}

	public boolean isOn(String id, boolean def) {
		Boolean b = layers.get(id);
		return b == null ? def : b;
	}

	public void set(String id, boolean value) {
		layers.put(id, value);
	}

	public ViewMode mode() {
		try {
			return ViewMode.valueOf(mode);
		} catch (Exception e) {
			return ViewMode.XAERO;
		}
	}

	public void setMode(ViewMode m) {
		mode = m.name();
		save();
	}
}
