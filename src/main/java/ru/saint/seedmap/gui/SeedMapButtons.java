package ru.saint.seedmap.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import ru.saint.seedmap.SeedMapConfig;
import ru.saint.seedmap.SeedMapState;
import ru.saint.seedmap.ViewMode;
import xaero.map.gui.GuiMap;

import java.util.ArrayList;
import java.util.List;

public final class SeedMapButtons {
	private SeedMapButtons() {}

	public static Component modeLabel() {
		return Component.translatable(SeedMapConfig.INSTANCE.mode().langKey());
	}

	public static List<Button> create(GuiMap map) {
		Screen screen = map;
		Minecraft mc = Minecraft.getInstance();
		int x = screen.width - 124;
		List<Button> list = new ArrayList<>();
		list.add(Button.builder(modeLabel(), b -> {
			SeedMapConfig cfg = SeedMapConfig.INSTANCE;
			ViewMode next = cfg.mode().next();
			cfg.setMode(next);
			b.setMessage(modeLabel());
			if (next != ViewMode.XAERO && SeedMapState.seed(SeedMapState.serverKey()) == null) mc.setScreenAndShow(new SeedScreen(screen));
		}).bounds(x, 2, 122, 20).build());
		list.add(Button.builder(Component.translatable("seedmapxaero.button.seed"), b -> mc.setScreenAndShow(new SeedScreen(screen)))
			.bounds(x, 24, 60, 20).build());
		list.add(Button.builder(Component.translatable("seedmapxaero.button.filters"),
				b -> mc.setScreenAndShow(new FiltersScreen(screen, SeedMapState.viewedDimension(map))))
			.bounds(x + 62, 24, 60, 20).build());
		return list;
	}
}
