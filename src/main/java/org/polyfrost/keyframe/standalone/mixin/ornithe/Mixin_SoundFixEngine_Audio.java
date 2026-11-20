package org.polyfrost.keyframe.standalone.mixin.ornithe;

import org.polyfrost.keyframe.standalone.export.AudioCapture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "io.github.moehreag.soundfix.sounds.SoundEngine", remap = false)
abstract class Mixin_SoundFixEngine_Audio {
	@Inject(method = "tick", at = @At("TAIL"))
	private void keyframe$drainLoopback(boolean paused, CallbackInfo ci) {
		AudioCapture.tick();
	}

	@Inject(method = "shouldChangeDevice", at = @At("RETURN"), cancellable = true)
	private void keyframe$holdLoopback(CallbackInfoReturnable<Boolean> cir) {
		if (AudioCapture.getArmed()) cir.setReturnValue(false);
	}
}
