package ru.saint.seedmap.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import ru.saint.seedmap.SeedMapConfig;
import ru.saint.seedmap.SeedMapState;
import ru.saint.seedmap.gen.Worldgen;

public class SeedScreen extends Screen {
	private final Screen parent;
	private EditBox box;
	private String serverKey = "";

	public SeedScreen(Screen parent) {
		super(Component.translatable("seedmapxaero.seed.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		serverKey = SeedMapState.serverKey();
		int cx = this.width / 2;
		int y = this.height / 2 - 20;
		box = new EditBox(this.font, cx - 110, y, 220, 20, Component.translatable("seedmapxaero.seed.title"));
		box.setMaxLength(64);
		String cur = SeedMapConfig.INSTANCE.seeds.get(serverKey);
		if (cur == null && SeedMapState.isSingleplayer()) {
			Long s = SeedMapState.seed(serverKey);
			if (s != null) cur = String.valueOf(s);
		}
		box.setValue(cur == null ? "" : cur);
		box.setHint(Component.translatable("seedmapxaero.seed.hint"));
		this.addRenderableWidget(box);
		this.setInitialFocus(box);
		this.addRenderableWidget(Button.builder(Component.translatable("seedmapxaero.seed.save"), b -> save())
			.bounds(cx - 110, y + 26, 108, 20).build());
		this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
			.bounds(cx + 2, y + 26, 108, 20).build());
	}

	private void save() {
		String v = box.getValue().trim();
		if (v.isEmpty()) SeedMapConfig.INSTANCE.seeds.remove(serverKey);
		else SeedMapConfig.INSTANCE.seeds.put(serverKey, String.valueOf(SeedMapState.parseSeed(v)));
		SeedMapConfig.save();
		Worldgen.retry();
		onClose();
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == 257 || event.key() == 335) {
			save();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		this.minecraft.setScreenAndShow(parent);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		super.extractRenderState(g, mouseX, mouseY, delta);
		int cx = this.width / 2;
		int y = this.height / 2 - 20;
		g.centeredText(this.font, this.title, cx, y - 34, 0xFFFFFFFF);
		g.centeredText(this.font, Component.translatable("seedmapxaero.seed.server", serverKey), cx, y - 20, 0xFFA0A0A0);
		if (SeedMapState.isSingleplayer()) {
			g.centeredText(this.font, Component.translatable("seedmapxaero.seed.sp"), cx, y + 54, 0xFF80FF80);
		}
	}
}
