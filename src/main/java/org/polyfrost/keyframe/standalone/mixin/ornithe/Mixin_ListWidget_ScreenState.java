package org.polyfrost.keyframe.standalone.mixin.ornithe;

import net.minecraft.client.gui.widget.ListWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ListWidget.class)
public interface Mixin_ListWidget_ScreenState {
	@Accessor("scrollAmount")
	float keyframe$getScrollAmount();

	@Accessor("scrollAmount")
	void keyframe$setScrollAmount(float amount);
}
