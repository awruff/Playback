package org.polyfrost.keyframe.standalone.mixin.ornithe;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(targets = "net.minecraft.client.gui.screen.game.inventory.CreativeInventoryScreen$CreativePlayerMenu")
public interface Mixin_CreativePlayerMenu_ScreenState {
	@Invoker("scrollItems")
	void keyframe$scrollItems(float position);
}
