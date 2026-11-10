package ru.saint.seedmap.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.saint.seedmap.gui.SeedMapButtons;
import ru.saint.seedmap.render.SeedMapRenderer;
import xaero.map.gui.GuiMap;

@Mixin(value = GuiMap.class, remap = false)
public abstract class GuiMapMixin extends Screen {
	protected GuiMapMixin(Component title) {
		super(title);
	}

	@Inject(method = "init()V", at = @At("TAIL"))
	private void seedmap$init(CallbackInfo ci) {
		for (Button b : SeedMapButtons.create((GuiMap) (Object) this)) this.addRenderableWidget(b);
	}

	@Inject(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lxaero/map/element/MapElementRenderHandler;render", ordinal = 0))
	private void seedmap$renderMap(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		SeedMapRenderer.renderInMap((GuiMap) (Object) this);
	}

	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void seedmap$renderGui(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		SeedMapRenderer.renderGui((GuiMap) (Object) this, g);
	}

	@Inject(method = "mapClicked(III)V", at = @At("HEAD"), cancellable = true)
	private void seedmap$mapClicked(int button, int x, int y, CallbackInfo ci) {
		if (SeedMapRenderer.onMapClicked((GuiMap) (Object) this, button)) ci.cancel();
	}
}
