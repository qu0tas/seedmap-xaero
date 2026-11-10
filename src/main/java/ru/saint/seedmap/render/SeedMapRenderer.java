package ru.saint.seedmap.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import org.joml.Matrix4f;
import ru.saint.seedmap.SeedMapClient;
import ru.saint.seedmap.SeedMapConfig;
import ru.saint.seedmap.SeedMapState;
import ru.saint.seedmap.ViewMode;
import ru.saint.seedmap.gen.BiomeTiles;
import ru.saint.seedmap.gen.DimGen;
import ru.saint.seedmap.gen.Found;
import ru.saint.seedmap.gen.Kind;
import ru.saint.seedmap.gen.RegionCache;
import ru.saint.seedmap.gen.StructureCache;
import ru.saint.seedmap.gen.Worldgen;
import ru.saint.seedmap.mixin.GuiMapAccessor;
import xaero.map.MapProcessor;
import xaero.map.WorldMap;
import xaero.map.graphics.CustomRenderTypes;
import xaero.map.graphics.renderer.multitexture.MultiTextureRenderTypeRenderer;
import xaero.map.graphics.renderer.multitexture.MultiTextureRenderTypeRendererProvider;
import xaero.map.gui.GuiMap;
import xaero.map.region.MapRegion;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class SeedMapRenderer {
	private static final Identifier ICONS = Identifier.fromNamespaceAndPath(SeedMapClient.MOD_ID, "textures/gui/icons.png");
	private static final int MAX_MARKERS = 6000;
	private static final int MAX_REGIONS = 1600;
	private static final int MAX_TILES = 2500;
	/** Dungeons need terrain columns: only computed when zoomed in (window pixels per block). */
	private static final double DUNGEON_MIN_SCALE = 1.0;
	private static final int MAX_DUNGEON_REGIONS = 120;
	private static final float WHITE_U = (7 * 32 + 16) / 256f;
	private static final float WHITE_V = (3 * 32 + 16) / 256f;

	private static Marker hovered;
	private static Marker selected;
	private static Component status;
	private static boolean renderErrorLogged;
	private static boolean guiErrorLogged;

	private SeedMapRenderer() {}

	// ---------------------------------------------------------------- map space

	public static void renderInMap(GuiMap map) {
		hovered = null;
		status = null;
		PoseStack ps = WorldMap.worldMapClientOnly.getMapScreenPoseStack();
		ps.pushPose();
		try {
			render(map, ps);
		} catch (Throwable t) {
			if (!renderErrorLogged) {
				renderErrorLogged = true;
				SeedMapClient.LOG.error("SeedMap render failed", t);
			}
			status = Component.translatable("seedmapxaero.status.error", t.getClass().getSimpleName());
		} finally {
			ps.popPose();
		}
	}

	private static void render(GuiMap map, PoseStack ps) {
		Minecraft mc = Minecraft.getInstance();
		SeedMapConfig cfg = SeedMapConfig.INSTANCE;
		ViewMode mode = cfg.mode();
		GuiMapAccessor acc = (GuiMapAccessor) (Object) map;
		double camX = acc.seedmap$getCameraX();
		double camZ = acc.seedmap$getCameraZ();
		double scale = acc.seedmap$getScale();
		double screenScale = acc.seedmap$getScreenScale();
		if (!(scale > 0)) return;

		int winW = mc.getWindow().getWidth();
		int winH = mc.getWindow().getHeight();
		double halfW = winW / 2.0 / scale;
		double halfH = winH / 2.0 / scale;
		double minX = camX - halfW, maxX = camX + halfW;
		double minZ = camZ - halfH, maxZ = camZ + halfH;
		double mouseX = (mc.mouseHandler.xpos() - winW / 2.0) / scale + camX;
		double mouseZ = (mc.mouseHandler.ypos() - winH / 2.0) / scale + camZ;

		float iconHalf = (float) (cfg.iconSize * screenScale / 2.0 / scale);
		double margin = iconHalf * 2.0 + 1.0;

		ResourceKey<Level> dim = SeedMapState.viewedDimension(map);
		String server = SeedMapState.serverKey();
		String dimId = SeedMapState.dimId(dim);
		long now = System.currentTimeMillis();

		int ox = (int) Math.floor(camX);
		int oz = (int) Math.floor(camZ);
		ps.translate((float) -(camX - ox), (float) -(camZ - oz), 0f);
		Matrix4f m = ps.last().pose();
		MultiTextureRenderTypeRendererProvider rp = map.getMapProcessor().getMultiTextureRenderTypeRenderers();
		AbstractTexture atlas = mc.getTextureManager().getTexture(ICONS);

		List<Marker> markers = new ArrayList<>();
		if (mode != ViewMode.XAERO) {
			Long seed = SeedMapState.seed(server);
			if (seed == null) {
				status = Component.translatable("seedmapxaero.status.noseed");
			} else {
				DimGen g = Worldgen.get(server, seed, dim);
				if (g == null) {
					String err = Worldgen.lastError();
					status = err != null ? Component.translatable("seedmapxaero.status.error", err) : Component.translatable("seedmapxaero.status.loading");
				} else {
					if (mode == ViewMode.SEEDMAP) drawBiomes(g, rp, m, ox, oz, minX, maxX, minZ, maxZ, scale, now);
					if (g.overworld && cfg.isOn("slime", true) && scale >= 0.75) drawSlime(g, rp, m, atlas, ox, oz, minX, maxX, minZ, maxZ);
					collectStructures(map, g, mode, markers, minX - margin, maxX + margin, minZ - margin, maxZ + margin, camX, camZ, now);
					collectFeatures(map, g, mode, markers, minX - margin, maxX + margin, minZ - margin, maxZ + margin, camX, camZ, now, scale);
					if (g.overworld && cfg.isOn("spawn", true)) {
						BlockPos sp = g.spawn();
						if (sp != null) markers.add(Marker.spawn(sp));
					}
				}
			}
		}
		for (SeedMapConfig.Waypoint w : cfg.waypoints) {
			if (server.equals(w.server) && dimId.equals(w.dim)) {
				if (w.x >= minX - margin && w.x <= maxX + margin && w.z >= minZ - margin && w.z <= maxZ + margin) markers.add(Marker.waypoint(w));
			}
		}
		if (!markers.isEmpty()) drawMarkers(rp, m, atlas, ox, oz, markers, iconHalf, mouseX, mouseZ);
	}

	private static void drawBiomes(DimGen g, MultiTextureRenderTypeRendererProvider rp, Matrix4f m, int ox, int oz,
								   double minX, double maxX, double minZ, double maxZ, double scale, long now) {
		BiomeTiles tiles = g.biomes;
		tiles.beginFrame();
		int shift = BiomeTiles.shiftFor(scale);
		int tileBlocks = BiomeTiles.SIZE << shift;
		int tx0 = Math.floorDiv((int) Math.floor(minX), tileBlocks), tx1 = Math.floorDiv((int) Math.ceil(maxX), tileBlocks);
		int tz0 = Math.floorDiv((int) Math.floor(minZ), tileBlocks), tz1 = Math.floorDiv((int) Math.ceil(maxZ), tileBlocks);
		if ((long) (tx1 - tx0 + 1) * (tz1 - tz0 + 1) > MAX_TILES) return;
		List<BiomeTiles.Tile> missing = new ArrayList<>();
		MultiTextureRenderTypeRenderer r = rp.getRenderer(CustomRenderTypes.GUI_NEAREST);
		try {
			for (int tz = tz0; tz <= tz1; tz++) {
				for (int tx = tx0; tx <= tx1; tx++) {
					BiomeTiles.Tile t = tiles.tile(shift, tx, tz, now);
					if (t.texture() != null) {
						drawTile(r, m, t, ox, oz, tx * tileBlocks, tz * tileBlocks, tileBlocks, 0f, 0f, 1f, 1f);
						continue;
					}
					missing.add(t);
					for (int k = 1; k <= 4 && shift + k <= BiomeTiles.MAX_SHIFT; k++) {
						BiomeTiles.Tile p = tiles.peek(shift + k, tx >> k, tz >> k, now);
						if (p == null) continue;
						int n = 1 << k;
						float u0 = (float) (tx - ((tx >> k) << k)) / n;
						float v0 = (float) (tz - ((tz >> k) << k)) / n;
						drawTile(r, m, p, ox, oz, tx * tileBlocks, tz * tileBlocks, tileBlocks, u0, v0, u0 + 1f / n, v0 + 1f / n);
						break;
					}
				}
			}
		} finally {
			rp.draw(r);
		}
		double cx = (minX + maxX) / 2.0, cz = (minZ + maxZ) / 2.0;
		missing.sort(Comparator.comparingDouble((BiomeTiles.Tile t) -> {
			double dx = (t.tx + 0.5) * tileBlocks - cx, dz = (t.tz + 0.5) * tileBlocks - cz;
			return -(dx * dx + dz * dz);
		}));
		for (BiomeTiles.Tile t : missing) tiles.schedule(t);
		tiles.endFrame(now);
	}

	private static void drawTile(MultiTextureRenderTypeRenderer r, Matrix4f m, BiomeTiles.Tile t, int ox, int oz,
								 int x, int z, int size, float u0, float v0, float u1, float v1) {
		VertexConsumer vc = r.begin(t.texture().getTextureView());
		float x0 = x - ox, z0 = z - oz;
		quad(vc, m, x0, z0, x0 + size, z0 + size, u0, v0, u1, v1, 0xFFFFFFFF);
	}

	private static void drawSlime(DimGen g, MultiTextureRenderTypeRendererProvider rp, Matrix4f m, AbstractTexture atlas, int ox, int oz,
								  double minX, double maxX, double minZ, double maxZ) {
		int cx0 = Math.floorDiv((int) Math.floor(minX), 16), cx1 = Math.floorDiv((int) Math.ceil(maxX), 16);
		int cz0 = Math.floorDiv((int) Math.floor(minZ), 16), cz1 = Math.floorDiv((int) Math.ceil(maxZ), 16);
		if ((long) (cx1 - cx0 + 1) * (cz1 - cz0 + 1) > 60000) return;
		MultiTextureRenderTypeRenderer r = rp.getRenderer(CustomRenderTypes.GUI_NEAREST);
		try {
			VertexConsumer vc = null;
			for (int cz = cz0; cz <= cz1; cz++) {
				for (int cx = cx0; cx <= cx1; cx++) {
					if (WorldgenRandom.seedSlimeChunk(cx, cz, g.seed, 987234911L).nextInt(10) != 0) continue;
					if (vc == null) vc = r.begin(atlas.getTextureView());
					float x0 = cx * 16 - ox, z0 = cz * 16 - oz;
					quad(vc, m, x0, z0, x0 + 16, z0 + 16, WHITE_U, WHITE_V, WHITE_U, WHITE_V, 0x5A40E040);
				}
			}
		} finally {
			rp.draw(r);
		}
	}

	private static void collectStructures(GuiMap map, DimGen g, ViewMode mode, List<Marker> out,
										  double minX, double maxX, double minZ, double maxZ, double camX, double camZ, long now) {
		int rs = StructureCache.REGION_BLOCKS;
		int rx0 = Math.floorDiv((int) Math.floor(minX), rs), rx1 = Math.floorDiv((int) Math.ceil(maxX), rs);
		int rz0 = Math.floorDiv((int) Math.floor(minZ), rs), rz1 = Math.floorDiv((int) Math.ceil(maxZ), rs);
		if ((long) (rx1 - rx0 + 1) * (rz1 - rz0 + 1) > MAX_REGIONS) {
			status = Component.translatable("seedmapxaero.status.zoomin");
			return;
		}
		List<int[]> regs = new ArrayList<>();
		for (int rz = rz0; rz <= rz1; rz++) for (int rx = rx0; rx <= rx1; rx++) regs.add(new int[]{rx, rz});
		// LIFO pool: request far regions first so the centre is processed first
		regs.sort(Comparator.comparingDouble((int[] a) -> {
			double dx = (a[0] + 0.5) * rs - camX, dz = (a[1] + 0.5) * rs - camZ;
			return -(dx * dx + dz * dz);
		}));
		MapProcessor mp = map.getMapProcessor();
		int cave = mp.getCurrentCaveLayer();
		int pending = 0;
		for (int[] a : regs) {
			List<Found> l = g.structures.get(a[0], a[1], now);
			if (l == null) {
				pending++;
				continue;
			}
			for (Found f : l) {
				if (out.size() >= MAX_MARKERS) break;
				if (f.x() < minX || f.x() > maxX || f.z() < minZ || f.z() > maxZ) continue;
				if (!f.kind().enabled()) continue;
				if (mode == ViewMode.OVERLAY && !explored(mp, cave, f.x(), f.z())) continue;
				out.add(Marker.structure(f));
			}
		}
		if (pending > 0 && status == null) status = Component.translatable("seedmapxaero.status.generating", pending);
	}

	private static void collectFeatures(GuiMap map, DimGen g, ViewMode mode, List<Marker> out,
										double minX, double maxX, double minZ, double maxZ, double camX, double camZ, long now, double scale) {
		int bit = Kind.dimBit(g.dim);
		boolean light = false;
		for (Kind k : Kind.LIGHT_FEATURES) if ((k.dims & bit) != 0 && k.enabled()) light = true;
		if (light) collectFrom(map, g.features, MAX_REGIONS, mode, out, minX, maxX, minZ, maxZ, camX, camZ, now);
		if (g.overworld && Kind.DUNGEON.enabled()) {
			boolean ok = scale >= DUNGEON_MIN_SCALE
				&& collectFrom(map, g.dungeons, MAX_DUNGEON_REGIONS, mode, out, minX, maxX, minZ, maxZ, camX, camZ, now);
			if (!ok && status == null) status = Component.translatable("seedmapxaero.status.zoomin_dungeons");
		}
	}

	/** @return false if the view is too large for this cache. */
	private static boolean collectFrom(GuiMap map, RegionCache cache, int maxRegions, ViewMode mode, List<Marker> out,
									   double minX, double maxX, double minZ, double maxZ, double camX, double camZ, long now) {
		int rs = cache.regionBlocks;
		int rx0 = Math.floorDiv((int) Math.floor(minX), rs), rx1 = Math.floorDiv((int) Math.ceil(maxX), rs);
		int rz0 = Math.floorDiv((int) Math.floor(minZ), rs), rz1 = Math.floorDiv((int) Math.ceil(maxZ), rs);
		if ((long) (rx1 - rx0 + 1) * (rz1 - rz0 + 1) > maxRegions) return false;
		List<int[]> regs = new ArrayList<>();
		for (int rz = rz0; rz <= rz1; rz++) for (int rx = rx0; rx <= rx1; rx++) regs.add(new int[]{rx, rz});
		regs.sort(Comparator.comparingDouble((int[] a) -> {
			double dx = (a[0] + 0.5) * rs - camX, dz = (a[1] + 0.5) * rs - camZ;
			return -(dx * dx + dz * dz);
		}));
		MapProcessor mp = map.getMapProcessor();
		int cave = mp.getCurrentCaveLayer();
		int pending = 0;
		for (int[] a : regs) {
			List<Found> l = cache.get(a[0], a[1], now);
			if (l == null) {
				pending++;
				continue;
			}
			for (Found f : l) {
				if (out.size() >= MAX_MARKERS) break;
				if (f.x() < minX || f.x() > maxX || f.z() < minZ || f.z() > maxZ) continue;
				if (!f.kind().enabled()) continue;
				if (mode == ViewMode.OVERLAY && !explored(mp, cave, f.x(), f.z())) continue;
				out.add(Marker.structure(f));
			}
		}
		if (pending > 0 && status == null) status = Component.translatable("seedmapxaero.status.generating", pending);
		return true;
	}

	private static boolean explored(MapProcessor mp, int cave, int x, int z) {
		MapRegion reg = mp.getLeafMapRegion(cave, x >> 9, z >> 9, false);
		if (reg == null) return false;
		return reg.getChunk((x >> 6) & 7, (z >> 6) & 7) != null;
	}

	private static double hoverZ(Marker mk, float half) {
		return mk.type == Marker.Type.WAYPOINT ? mk.z + 0.5 - half : mk.z + 0.5;
	}

	private static void drawMarkers(MultiTextureRenderTypeRendererProvider rp, Matrix4f m, AbstractTexture atlas, int ox, int oz,
									List<Marker> markers, float half, double mouseX, double mouseZ) {
		Marker best = null;
		double bestD = Double.MAX_VALUE;
		for (Marker mk : markers) {
			double dx = Math.abs(mk.x + 0.5 - mouseX), dz = Math.abs(hoverZ(mk, half) - mouseZ);
			if (dx <= half && dz <= half) {
				double d = dx * dx + dz * dz;
				if (d < bestD) {
					bestD = d;
					best = mk;
				}
			}
		}
		hovered = best;
		MultiTextureRenderTypeRenderer r = rp.getRenderer(CustomRenderTypes.GUI_BILINEAR);
		try {
			VertexConsumer vc = r.begin(atlas.getTextureView());
			Marker sel = null;
			for (Marker mk : markers) {
				if (mk == best) continue;
				if (mk.same(selected)) {
					sel = mk;
					continue;
				}
				icon(vc, m, mk, ox, oz, half);
			}
			if (sel != null) icon(vc, m, sel, ox, oz, half * 1.25f);
			if (best != null) icon(vc, m, best, ox, oz, half * 1.35f);
		} finally {
			rp.draw(r);
		}
	}

	private static void icon(VertexConsumer vc, Matrix4f m, Marker mk, int ox, int oz, float half) {
		float cx = mk.x - ox + 0.5f, cz = mk.z - oz + 0.5f;
		int col = mk.icon % 8, row = mk.icon / 8;
		float u0 = (col * 32 + 0.5f) / 256f, v0 = (row * 32 + 0.5f) / 256f;
		float u1 = (col * 32 + 31.5f) / 256f, v1 = (row * 32 + 31.5f) / 256f;
		if (mk.type == Marker.Type.WAYPOINT) {
			quad(vc, m, cx - half, cz - 2 * half, cx + half, cz, u0, v0, u1, v1, 0xFFFFFFFF);
		} else {
			quad(vc, m, cx - half, cz - half, cx + half, cz + half, u0, v0, u1, v1, 0xFFFFFFFF);
		}
	}

	static void quad(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1,
					 float u0, float v0, float u1, float v1, int argb) {
		vc.addVertex(m, x0, y1, 0f).setColor(argb).setUv(u0, v1);
		vc.addVertex(m, x1, y1, 0f).setColor(argb).setUv(u1, v1);
		vc.addVertex(m, x1, y0, 0f).setColor(argb).setUv(u1, v0);
		vc.addVertex(m, x0, y0, 0f).setColor(argb).setUv(u0, v0);
	}

	// ---------------------------------------------------------------- clicks

	/** @return true if the click was consumed by SeedMap. */
	public static boolean onMapClicked(GuiMap map, int button) {
		Marker h = hovered;
		if (h == null) {
			if (button == 0) selected = null;
			return false;
		}
		if (button == 0) {
			selected = h;
			return true;
		}
		if (button == 1) {
			toggleWaypoint(map, h);
			return true;
		}
		return false;
	}

	private static SeedMapConfig.Waypoint findWaypoint(String server, String dim, int x, int z) {
		for (SeedMapConfig.Waypoint w : SeedMapConfig.INSTANCE.waypoints) {
			if (server.equals(w.server) && dim.equals(w.dim) && Math.abs(w.x - x) <= 2 && Math.abs(w.z - z) <= 2) return w;
		}
		return null;
	}

	private static void toggleWaypoint(GuiMap map, Marker h) {
		SeedMapConfig cfg = SeedMapConfig.INSTANCE;
		String server = SeedMapState.serverKey();
		String dim = SeedMapState.dimId(SeedMapState.viewedDimension(map));
		if (h.type == Marker.Type.WAYPOINT) {
			cfg.waypoints.remove(h.waypoint);
			if (h.same(selected)) selected = null;
		} else {
			SeedMapConfig.Waypoint existing = findWaypoint(server, dim, h.x, h.z);
			if (existing != null) {
				cfg.waypoints.remove(existing);
			} else {
				SeedMapConfig.Waypoint w = new SeedMapConfig.Waypoint();
				w.server = server;
				w.dim = dim;
				w.x = h.x;
				w.y = h.y;
				w.z = h.z;
				w.hasY = h.hasY;
				w.kind = h.type == Marker.Type.SPAWN ? "spawn" : (h.kind == null ? null : h.kind.id);
				w.label = h.structureId;
				cfg.waypoints.add(w);
			}
		}
		SeedMapConfig.save();
	}

	// ---------------------------------------------------------------- GUI space

	public static void renderGui(GuiMap map, GuiGraphicsExtractor g) {
		try {
			gui(map, g);
		} catch (Throwable t) {
			if (!guiErrorLogged) {
				guiErrorLogged = true;
				SeedMapClient.LOG.error("SeedMap GUI render failed", t);
			}
		}
	}

	private static void gui(GuiMap map, GuiGraphicsExtractor g) {
		Minecraft mc = Minecraft.getInstance();
		Font font = mc.font;
		Screen screen = map;
		int right = screen.width - 4;
		int y = 48;
		if (SeedMapConfig.INSTANCE.mode() != ViewMode.XAERO) {
			Long seed = SeedMapState.seed(SeedMapState.serverKey());
			if (seed != null) {
				Component line = Component.translatable("seedmapxaero.status.seed", String.valueOf(seed));
				g.text(font, line, right - font.width(line), y, 0xFFFFFFFF, true);
				y += 11;
			}
			Component st = status;
			if (st != null) {
				g.text(font, st, right - font.width(st), y, 0xFFFFD060, true);
				y += 11;
			}
		}
		Marker sel = selected;
		if (sel != null) drawInfo(g, font, map, sel, right, y + 4);
		Marker h = hovered;
		if (h != null) {
			double gx = mc.mouseHandler.xpos() * g.guiWidth() / (double) mc.getWindow().getWidth();
			double gy = mc.mouseHandler.ypos() * g.guiHeight() / (double) mc.getWindow().getHeight();
			g.setComponentTooltipForNextFrame(font, tooltip(h), (int) gx, (int) gy);
		}
	}

	private static String yText(Marker mk) {
		return mk.hasY ? String.valueOf(mk.y) : "~";
	}

	private static List<Component> tooltip(Marker mk) {
		List<Component> l = new ArrayList<>();
		l.add(mk.title().withStyle(ChatFormatting.YELLOW));
		Component variant = mk.variant();
		if (variant != null) l.add(mk.variant().withStyle(ChatFormatting.GOLD));
		l.add(Component.translatable("seedmapxaero.info.coords", mk.x, yText(mk), mk.z).withStyle(ChatFormatting.GRAY));
		if (mk.approx && mk.type == Marker.Type.STRUCTURE) l.add(Component.translatable("seedmapxaero.info.approx").withStyle(ChatFormatting.DARK_GRAY));
		l.add(Component.translatable("seedmapxaero.info.hint").withStyle(ChatFormatting.DARK_AQUA));
		return l;
	}

	private static void drawInfo(GuiGraphicsExtractor g, Font font, GuiMap map, Marker mk, int right, int y) {
		Minecraft mc = Minecraft.getInstance();
		List<Component> lines = new ArrayList<>();
		lines.add(mk.title());
		Component variant = mk.variant();
		if (variant != null) lines.add(variant);
		lines.add(Component.translatable("seedmapxaero.info.coords", mk.x, yText(mk), mk.z));
		lines.add(Component.translatable("seedmapxaero.info.chunk", mk.x >> 4, mk.z >> 4));
		if (mc.player != null) {
			double dx = mc.player.getX() - mk.x, dz = mc.player.getZ() - mk.z;
			lines.add(Component.translatable("seedmapxaero.info.distance", (int) Math.sqrt(dx * dx + dz * dz)));
		}
		if (mk.approx && mk.type == Marker.Type.STRUCTURE) lines.add(Component.translatable("seedmapxaero.info.approx"));
		boolean hasWp = mk.type == Marker.Type.WAYPOINT
			|| findWaypoint(SeedMapState.serverKey(), SeedMapState.dimId(SeedMapState.viewedDimension(map)), mk.x, mk.z) != null;
		lines.add(Component.translatable(hasWp ? "seedmapxaero.info.has_waypoint" : "seedmapxaero.info.rmb"));
		int w = 0;
		for (Component c : lines) w = Math.max(w, font.width(c));
		int x0 = right - w - 8;
		g.fill(x0, y, right, y + lines.size() * 11 + 6, 0xB0000000);
		int yy = y + 4;
		for (int i = 0; i < lines.size(); i++) {
			g.text(font, lines.get(i), x0 + 4, yy, i == 0 ? 0xFFFFFF55 : 0xFFE0E0E0, false);
			yy += 11;
		}
	}
}
