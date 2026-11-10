package ru.saint.seedmap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import xaero.map.gui.GuiMap;

import java.util.Locale;

public final class SeedMapState {
	private SeedMapState() {}

	public static boolean isSingleplayer() {
		return Minecraft.getInstance().getSingleplayerServer() != null;
	}

	public static String serverKey() {
		Minecraft mc = Minecraft.getInstance();
		IntegratedServer sp = mc.getSingleplayerServer();
		if (sp != null) return "sp:" + sp.getWorldData().getLevelName();
		ServerData sd = mc.getCurrentServer();
		if (sd != null && sd.ip != null) return "mp:" + sd.ip.trim().toLowerCase(Locale.ROOT);
		return "unknown";
	}

	public static Long seed(String serverKey) {
		IntegratedServer sp = Minecraft.getInstance().getSingleplayerServer();
		if (sp != null) {
			ServerLevel ow = sp.overworld();
			if (ow != null) return ow.getSeed();
		}
		String s = SeedMapConfig.INSTANCE.seeds.get(serverKey);
		if (s == null) return null;
		try {
			return Long.parseLong(s.trim());
		} catch (NumberFormatException e) {
			return null;
		}
	}

	public static long parseSeed(String text) {
		String t = text.trim();
		try {
			return Long.parseLong(t);
		} catch (NumberFormatException e) {
			return t.hashCode();
		}
	}

	public static ResourceKey<Level> viewedDimension(GuiMap map) {
		try {
			var world = map.getMapProcessor().getMapWorld();
			if (world != null) {
				var d = world.getCurrentDimension();
				if (d != null && d.getDimId() != null) return d.getDimId();
			}
		} catch (Throwable ignored) {
		}
		Minecraft mc = Minecraft.getInstance();
		return mc.level != null ? mc.level.dimension() : Level.OVERWORLD;
	}

	public static String dimId(ResourceKey<Level> dim) {
		return dim.identifier().toString();
	}
}
