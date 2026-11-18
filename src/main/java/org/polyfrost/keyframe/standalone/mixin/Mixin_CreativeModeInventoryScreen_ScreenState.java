package org.polyfrost.keyframe.standalone.mixin;

import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(CreativeModeInventoryScreen.class)
public interface Mixin_CreativeModeInventoryScreen_ScreenState {
	@Accessor("scrollOffs")
	float keyframe$getScrollOffs();

	@Accessor("scrollOffs")
	void keyframe$setScrollOffs(float scroll);

	@Invoker("refreshSearchResults")
	void keyframe$refreshSearchResults();
}
