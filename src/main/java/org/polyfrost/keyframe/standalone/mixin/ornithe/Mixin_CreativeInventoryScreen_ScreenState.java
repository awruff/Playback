package org.polyfrost.keyframe.standalone.mixin.ornithe;

import net.minecraft.client.gui.screen.game.inventory.CreativeInventoryScreen;
import net.minecraft.item.CreativeModeTab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(CreativeInventoryScreen.class)
public interface Mixin_CreativeInventoryScreen_ScreenState {
	@Accessor("selectedTab")
	static int keyframe$getSelectedTab() {
		throw new AssertionError();
	}

	@Invoker("setSelectedTab")
	void keyframe$setSelectedTab(CreativeModeTab tab);

	@Accessor("scrollPosition")
	float keyframe$getScrollPosition();

	@Accessor("scrollPosition")
	void keyframe$setScrollPosition(float position);
}
