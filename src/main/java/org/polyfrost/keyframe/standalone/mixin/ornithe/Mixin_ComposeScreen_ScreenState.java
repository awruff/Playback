package org.polyfrost.keyframe.standalone.mixin.ornithe;

import org.polyfrost.keyframe.standalone.playback.ScreenReplay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "org.polyfrost.oneconfig.internal.ui.compose.ComposeScreen", remap = false)
abstract class Mixin_ComposeScreen_ScreenState {
	@Inject(method = "handleInputs", at = @At("HEAD"), cancellable = true, require = 0)
	private void keyframe$keepEditorInput(CallbackInfo ci) {
		if (ScreenReplay.getDrawingPreview() == (Object) this) ci.cancel();
	}
}
