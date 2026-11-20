package org.polyfrost.keyframe.standalone.mixin.ornithe;

import java.util.concurrent.Executor;
import org.polyfrost.keyframe.standalone.export.AudioCapture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "io.github.moehreag.soundfix.sounds.SoundEngineExecutor", remap = false)
abstract class Mixin_SoundFixExecutor_Audio {
	@Inject(method = "<init>", at = @At("RETURN"))
	private void keyframe$shareExecutor(CallbackInfo ci) {
		AudioCapture.setSoundFixExecutor((Executor) (Object) this);
	}
}
