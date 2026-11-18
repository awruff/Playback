package org.polyfrost.keyframe.standalone.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
//? if >=26.2 {
import net.minecraft.client.gui.Hud;
//?} else
/*import net.minecraft.client.gui.Gui;*/
import org.polyfrost.keyframe.standalone.playback.ScreenReplay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

//? if >=26.2 {
@Mixin(Hud.class)
//?} else
/*@Mixin(Gui.class)*/
abstract class Mixin_Hud_ScreenState {
	@ModifyExpressionValue(
		method = "extractTabList",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;isDown()Z")
	)
	private boolean keyframe$replayTabList(boolean down) {
		return ScreenReplay.INSTANCE.tabListDown(down);
	}
}
