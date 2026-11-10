package ru.saint.seedmap.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import xaero.map.gui.GuiMap;

@Mixin(value = GuiMap.class, remap = false)
public interface GuiMapAccessor {
	@Accessor("cameraX")
	double seedmap$getCameraX();

	@Accessor("cameraZ")
	double seedmap$getCameraZ();

	@Accessor("scale")
	double seedmap$getScale();

	@Accessor("screenScale")
	double seedmap$getScreenScale();
}
