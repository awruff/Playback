package org.polyfrost.keyframe.standalone.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
//? if >=26.2 {
import net.minecraft.client.gui.Gui;
//?} elif >=1.21.4 {
/*import net.minecraft.client.Minecraft;
*///?}
//? if >=1.21.4 {
import net.minecraft.client.gui.components.toasts.ToastManager;
//?} else {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.client.renderer.GameRenderer;
*///?}
import org.polyfrost.keyframe.standalone.playback.LocalEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

//? if >=26.2 {
@Mixin(Gui.class)
//?} elif >=1.21.4 {
/*@Mixin(Minecraft.class)
*///?} else
/*@Mixin(GameRenderer.class)*/
abstract class Mixin_Gui_Audio {
	//? if >=1.21.4 {
	@WrapOperation(
		//? if >=26.2 {
		method = "update",
		//?} else
		/*method = "runTick",*/
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/toasts/ToastManager;update()V")
	)
	private void keyframe$markToastSounds(ToastManager toasts, Operation<Void> original) {
		LocalEvents.muted(() -> original.call(toasts));
	}
	//?} else {
	/*@WrapOperation(
		method = "extractGui(Lnet/minecraft/client/DeltaTracker;ZZ)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/toasts/ToastComponent;render(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V")
	)
	private void keyframe$markToastSounds(ToastComponent toasts, GuiGraphicsExtractor graphics, Operation<Void> original) {
		LocalEvents.muted(() -> original.call(toasts, graphics));
	}
	*///?}
}
