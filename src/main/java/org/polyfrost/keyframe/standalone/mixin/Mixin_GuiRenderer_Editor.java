package org.polyfrost.keyframe.standalone.mixin;

//? if >=1.21.8 {
import net.minecraft.client.gui.render.GuiRenderer;
import org.polyfrost.keyframe.standalone.export.PreviewTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiRenderer.class)
abstract class Mixin_GuiRenderer_Editor {
	@Inject(
		method = "draw",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;processBlurEffect()V")
	)
	private void keyframe$capturePreview(CallbackInfo ci) {
		PreviewTexture.capture();
	}
}
//?}
