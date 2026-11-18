package org.polyfrost.keyframe.standalone.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.client.DeltaTracker;
//? if <26.1
/*import net.minecraft.client.Minecraft;*/
//? if >=26.2 {
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.Hud;
//?} else {
/*import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GameRenderer;
*///?}
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.polyfrost.keyframe.standalone.export.Export;
import org.polyfrost.keyframe.standalone.playback.Keyframe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

//? if >=26.2 {
@Mixin(Gui.class)
//?} else
/*@Mixin(GameRenderer.class)*/
public abstract class Mixin_Gui_Export {
	//? if >=26.2 {
	@WrapWithCondition(
		method = "extractRenderState(Lnet/minecraft/client/DeltaTracker;ZZ)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Hud;extractDebugOverlay(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V")
	)
	private boolean keyframe$extractDebugOverlay(Hud hud, GuiGraphicsExtractor graphics) {
		return Export.renderHud() && !Keyframe.INSTANCE.getPreviewing();
	}
	//?} else {
	/*//? if >=1.21.10 {
	/^@WrapWithCondition(
		method = "extractGui(Lnet/minecraft/client/DeltaTracker;ZZ)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;extractDebugOverlay(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V")
	)
	private boolean keyframe$extractDebugOverlay(Gui gui, GuiGraphicsExtractor graphics) {
		return Export.renderHud() && !Keyframe.INSTANCE.getPreviewing();
	}
	^///?}
	*///?}

	//? if <26.1 {
	/*@WrapWithCondition(
		method = "extractGui(Lnet/minecraft/client/DeltaTracker;ZZ)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;pauseGame(Z)V")
	)
	private boolean keyframe$pauseGame(Minecraft minecraft, boolean suppressPauseMenu) {
		return !Export.getActive();
	}
	*///?}
}
