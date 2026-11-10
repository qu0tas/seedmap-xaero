package ru.saint.seedmap;

import java.util.Locale;

public enum ViewMode {
	XAERO, SEEDMAP, OVERLAY;

	public ViewMode next() {
		return values()[(ordinal() + 1) % values().length];
	}

	public String langKey() {
		return "seedmapxaero.mode." + name().toLowerCase(Locale.ROOT);
	}
}
