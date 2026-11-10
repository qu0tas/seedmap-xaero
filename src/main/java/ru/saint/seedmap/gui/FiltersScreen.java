package ru.saint.seedmap.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import ru.saint.seedmap.SeedMapConfig;
import ru.saint.seedmap.gen.Kind;

import java.util.ArrayList;
import java.util.List;

public class FiltersScreen extends Screen {
	private static final int[] ICON_SIZES = {10, 12, 14, 16, 20, 24};

	private record Entry(String id, String langKey, boolean def) {}

	private final Screen parent;
	private final int dimBit;
	private final List<Entry> entries = new ArrayList<>();
	private final List<Button> toggles = new ArrayList<>();

	public FiltersScreen(Screen parent, ResourceKey<Level> dim) {
		super(Component.translatable("seedmapxaero.filters.title"));
		this.parent = parent;
		this.dimBit = Kind.dimBit(dim);
		for (Kind k : Kind.values()) if ((k.dims & dimBit) != 0) entries.add(new Entry(k.layerId(), k.langKey(), k.def));
		if ((dimBit & 1) != 0) {
			entries.add(new Entry("slime", "seedmapxaero.layer.slime", true));
			entries.add(new Entry("spawn", "seedmapxaero.layer.spawn", true));
		}
	}

	private static Component label(Entry e) {
		boolean on = SeedMapConfig.INSTANCE.isOn(e.id(), e.def());
		return Component.literal(on ? "\u2714 " : "\u2718 ").withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED)
			.append(Component.translatable(e.langKey()).withStyle(on ? ChatFormatting.WHITE : ChatFormatting.GRAY));
	}

	private static Component sizeLabel() {
		return Component.translatable("seedmapxaero.filters.icon_size", SeedMapConfig.INSTANCE.iconSize);
	}

	@Override
	protected void init() {
		toggles.clear();
		int rowsFit = Math.max(4, (this.height - 70) / 22);
		int cols = Math.max(2, Math.min(4, (entries.size() + rowsFit - 1) / rowsFit));
		int colW = Math.min(150, (this.width - 20) / cols - 4);
		int gap = 4;
		int totalW = cols * colW + (cols - 1) * gap;
		int x0 = (this.width - totalW) / 2;
		int y0 = 30;
		int rows = (entries.size() + cols - 1) / cols;
		for (int i = 0; i < entries.size(); i++) {
			Entry e = entries.get(i);
			int col = i % cols, row = i / cols;
			Button b = Button.builder(label(e), btn -> {
				SeedMapConfig cfg = SeedMapConfig.INSTANCE;
				cfg.set(e.id(), !cfg.isOn(e.id(), e.def()));
				btn.setMessage(label(e));
			}).bounds(x0 + col * (colW + gap), y0 + row * 22, colW, 20).build();
			toggles.add(b);
			this.addRenderableWidget(b);
		}
		int by = Math.min(y0 + rows * 22 + 8, this.height - 24);
		int bw = Math.min(100, (this.width - 20) / 4 - 4);
		int bx = (this.width - (4 * bw + 3 * gap)) / 2;
		this.addRenderableWidget(Button.builder(Component.translatable("seedmapxaero.filters.all_on"), b -> setAll(true))
			.bounds(bx, by, bw, 20).build());
		this.addRenderableWidget(Button.builder(Component.translatable("seedmapxaero.filters.all_off"), b -> setAll(false))
			.bounds(bx + (bw + gap), by, bw, 20).build());
		this.addRenderableWidget(Button.builder(sizeLabel(), b -> {
			SeedMapConfig cfg = SeedMapConfig.INSTANCE;
			int idx = 0;
			for (int i = 0; i < ICON_SIZES.length; i++) if (ICON_SIZES[i] == cfg.iconSize) idx = i;
			cfg.iconSize = ICON_SIZES[(idx + 1) % ICON_SIZES.length];
			b.setMessage(sizeLabel());
		}).bounds(bx + 2 * (bw + gap), by, bw, 20).build());
		this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
			.bounds(bx + 3 * (bw + gap), by, bw, 20).build());
	}

	private void setAll(boolean on) {
		for (Entry e : entries) SeedMapConfig.INSTANCE.set(e.id(), on);
		for (int i = 0; i < toggles.size(); i++) toggles.get(i).setMessage(label(entries.get(i)));
	}

	@Override
	public void onClose() {
		SeedMapConfig.save();
		this.minecraft.setScreenAndShow(parent);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		super.extractRenderState(g, mouseX, mouseY, delta);
		g.centeredText(this.font, this.title, this.width / 2, 12, 0xFFFFFFFF);
	}
}
