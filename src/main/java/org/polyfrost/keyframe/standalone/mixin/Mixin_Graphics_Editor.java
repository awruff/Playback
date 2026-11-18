package org.polyfrost.keyframe.standalone.mixin;

//? if >=1.21.8 {
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.polyfrost.keyframe.standalone.playback.ScreenReplay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GuiGraphicsExtractor.class)
abstract class Mixin_Graphics_Editor {
	@WrapWithCondition(
		method = "blurBeforeThisStratum",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/state/gui/GuiRenderState;blurBeforeThisStratum()V")
	)
	private boolean keyframe$skipPreviewBlur(GuiRenderState state) {
		return !ScreenReplay.INSTANCE.getExtractingPreview();
	}
}
//?}
