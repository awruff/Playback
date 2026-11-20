package org.polyfrost.keyframe.standalone.mixin.ornithe;

import org.polyfrost.keyframe.standalone.playback.ScreenReplay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "org.polyfrost.oneconfig.api.platform.v1.internal.ScreenPlatformImpl", remap = false)
abstract class Mixin_ScreenPlatformImpl_ScreenState {
	@Inject(method = "current", at = @At("HEAD"), cancellable = true, require = 0)
	private void keyframe$previewedScreen(CallbackInfoReturnable<Object> cir) {
		if (ScreenReplay.getDrawingPreview() != null) cir.setReturnValue(ScreenReplay.getDrawingPreview());
	}
}
