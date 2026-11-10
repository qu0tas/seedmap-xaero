package ru.saint.seedmap.render;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.locale.Language;
import ru.saint.seedmap.SeedMapConfig;
import ru.saint.seedmap.gen.Found;
import ru.saint.seedmap.gen.Kind;

public final class Marker {
	public enum Type { STRUCTURE, SPAWN, WAYPOINT }

	public static final int ICON_SPAWN = 22;
	public static final int ICON_WAYPOINT = 23;

	public final Type type;
	public final Kind kind;
	public final int icon;
	public final int x;
	public final int y;
	public final int z;
	public final boolean hasY;
	public final boolean approx;
	public final String structureId;
	public final SeedMapConfig.Waypoint waypoint;

	private Marker(Type type, Kind kind, int icon, int x, int y, int z, boolean hasY, boolean approx, String structureId, SeedMapConfig.Waypoint waypoint) {
		this.type = type;
		this.kind = kind;
		this.icon = icon;
		this.x = x;
		this.y = y;
		this.z = z;
		this.hasY = hasY;
		this.approx = approx;
		this.structureId = structureId;
		this.waypoint = waypoint;
	}

	public static Marker structure(Found f) {
		return new Marker(Type.STRUCTURE, f.kind(), f.kind().icon, f.x(), f.y(), f.z(), f.hasY(), f.approx(), f.structureId(), null);
	}

	public static Marker spawn(BlockPos p) {
		return new Marker(Type.SPAWN, null, ICON_SPAWN, p.getX(), p.getY(), p.getZ(), false, true, null, null);
	}

	public static Marker waypoint(SeedMapConfig.Waypoint w) {
		return new Marker(Type.WAYPOINT, Kind.byId(w.kind), ICON_WAYPOINT, w.x, w.y, w.z, w.hasY, false, w.label, w);
	}

	public boolean same(Marker o) {
		return o != null && o.type == type && o.x == x && o.z == z;
	}

	public MutableComponent title() {
		switch (type) {
			case SPAWN:
				return Component.translatable("seedmapxaero.layer.spawn");
			case WAYPOINT:
				return Component.translatable("seedmapxaero.waypoint").append(": ").append(kindTitle(waypoint.kind, waypoint.label));
			default:
				return kindTitle(kind == null ? null : kind.id, structureId);
		}
	}

	/** "Type: Desert" etc. for structure variants (village_desert, ruined_portal_nether, ...), or null. */
	public MutableComponent variant() {
		if (type != Type.STRUCTURE || structureId == null) return null;
		int i = structureId.indexOf(':');
		String key = "seedmapxaero.variant." + (i >= 0 ? structureId.substring(i + 1) : structureId);
		if (!Language.getInstance().has(key)) return null;
		return Component.translatable("seedmapxaero.info.variant", Component.translatable(key));
	}

	public static MutableComponent kindTitle(String kindId, String label) {
		if ("spawn".equals(kindId)) return Component.translatable("seedmapxaero.layer.spawn");
		Kind k = Kind.byId(kindId);
		if (k == null || k == Kind.OTHER) return Component.literal(label != null ? label : "?");
		return Component.translatable(k.langKey());
	}
}
