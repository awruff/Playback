package org.polyfrost.keyframe.standalone.mixin;

//? if >=26.2
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.DeltaTracker;
//? if >=26.2 {
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.screens.Screen;
//?} else {
/*import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GameRenderer;
*///?}
import net.minecraft.client.gui.GuiGraphicsExtractor;
//? if >=1.21.4 {
import net.minecraft.client.gui.components.toasts.ToastManager;
//?} else
/*import net.minecraft.client.gui.components.toasts.ToastComponent;*/
import org.polyfrost.keyframe.standalone.export.Export;
import org.polyfrost.keyframe.standalone.playback.Keyframe;
import org.polyfrost.keyframe.standalone.export.PreviewFrame;
import org.polyfrost.keyframe.standalone.editor.TitleOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

//? if >=26.2 {
@Mixin(Gui.class)
//?} else
/*@Mixin(GameRenderer.class)*/
abstract class Mixin_Gui_Editor {
	//? if >=26.2 {
	@WrapMethod(method = "setScreen")
	private void keyframe$gateScreen(Screen screen, Operation<Void> original) {
		if (Keyframe.INSTANCE.allowsScreen(screen)) {
			original.call(screen);
		}
	}

	//?}
	//? if >=26.2 {
	@WrapOperation(
		method = "extractRenderState(Lnet/minecraft/client/DeltaTracker;ZZ)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Hud;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V")
	)
	private void keyframe$fitHud(Hud hud, GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, Operation<Void> original) {
		PreviewFrame.INSTANCE.fitGui(graphics, () -> {
			if (Export.renderHud()) original.call(hud, graphics, deltaTracker);
			TitleOverlay.extract(graphics);
		});
	}
	//?} else {
	/*@WrapOperation(
		method = "extractGui(Lnet/minecraft/client/DeltaTracker;ZZ)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V")
	)
	private void keyframe$fitHud(Gui gui, GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, Operation<Void> original) {
		PreviewFrame.INSTANCE.fitGui(graphics, () -> {
			if (Export.renderHud()) original.call(gui, graphics, deltaTracker);
			TitleOverlay.extract(graphics);
		});
	}
	*///?}

	//? if >=26.2 {
	@WrapOperation(
		method = "extractRenderState(Lnet/minecraft/client/DeltaTracker;ZZ)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/toasts/ToastManager;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V")
	)
	private void keyframe$fitToasts(ToastManager toasts, GuiGraphicsExtractor graphics, Operation<Void> original) {
	//?} elif >=26.1 {
	/*@WrapOperation(
		method = "extractGui(Lnet/minecraft/client/DeltaTracker;ZZ)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/toasts/ToastManager;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V")
	)
	private void keyframe$fitToasts(ToastManager toasts, GuiGraphicsExtractor graphics, Operation<Void> original) {
	*///?} elif >=1.21.4 {
	/*@WrapOperation(
		method = "extractGui(Lnet/minecraft/client/DeltaTracker;ZZ)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/toasts/ToastManager;render(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V")
	)
	private void keyframe$fitToasts(ToastManager toasts, GuiGraphicsExtractor graphics, Operation<Void> original) {
	*///?} else {
	/*@WrapOperation(
		method = "extractGui(Lnet/minecraft/client/DeltaTracker;ZZ)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/toasts/ToastComponent;render(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V")
	)
	private void keyframe$fitToasts(ToastComponent toasts, GuiGraphicsExtractor graphics, Operation<Void> original) {
	*///?}
		PreviewFrame.INSTANCE.fitGui(graphics, () -> {
			if (Export.renderHud()) original.call(toasts, graphics);
		});
	}
}
