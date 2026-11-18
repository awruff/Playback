package org.polyfrost.keyframe.standalone.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.DeltaTracker;
//? if >=26.2 {
import net.minecraft.client.gui.Gui;
//?} else
/*import net.minecraft.client.renderer.GameRenderer;*/
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.polyfrost.keyframe.standalone.playback.CursorSample;
import org.polyfrost.keyframe.standalone.recording.Recorder;
import org.polyfrost.keyframe.standalone.playback.ScreenReplay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//? if >=26.2 {
@Mixin(Gui.class)
//?} else
/*@Mixin(GameRenderer.class)*/
abstract class Mixin_Gui_Cursor {
	//? if >=26.2 {
	@Inject(method = "setScreen", at = @At("RETURN"))
	private void keyframe$writeScreen(CallbackInfo ci) {
		Recorder.INSTANCE.writeScreen();
	}
	//?}

	@WrapOperation(
		//? if >=26.2 {
		method = "extractRenderState(Lnet/minecraft/client/DeltaTracker;ZZ)V",
		//?} else
		/*method = "extractGui(Lnet/minecraft/client/DeltaTracker;ZZ)V",*/
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;extractRenderStateWithTooltipAndSubtitles(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V")
	)
	private void keyframe$replayCursor(Screen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, Operation<Void> original, @Local(argsOnly = true) DeltaTracker deltaTracker) {
		Recorder.INSTANCE.writeCursor(deltaTracker);
		CursorSample cursor = ScreenReplay.INSTANCE.cursor(deltaTracker, graphics);
		if (cursor == null) {
			ScreenReplay.INSTANCE.extractPreview(graphics, deltaTracker);
			ScreenReplay.INSTANCE.extractPreviewDebugOverlay(graphics);
			original.call(screen, graphics, mouseX, mouseY, delta);
		} else {
			try {
				original.call(screen, graphics, cursor.getMouseX(), cursor.getMouseY(), delta);
			} catch (RuntimeException e) {
				ScreenReplay.INSTANCE.drop(e);
			}
			ScreenReplay.INSTANCE.drawCursor(graphics, cursor);
		}
	}
}
