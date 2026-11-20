package org.polyfrost.keyframe.standalone.mixin.ornithe;

import java.nio.IntBuffer;
import org.lwjgl.system.MemoryStack;
import org.polyfrost.keyframe.standalone.export.AudioCapture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "io.github.moehreag.soundfix.b3d_audio.Library", remap = false)
abstract class Mixin_SoundFixLibrary_Audio {
	@Inject(method = "openDeviceOrFallback", at = @At("HEAD"), cancellable = true)
	private static void keyframe$openLoopback(String preferredDevice, String systemDefault, CallbackInfoReturnable<Long> cir) {
		if (AudioCapture.getArmed()) cir.setReturnValue(AudioCapture.openSoundFix());
	}

	@Inject(method = "createAttributes", at = @At("RETURN"), cancellable = true)
	private void keyframe$loopbackFormat(MemoryStack stack, boolean enableHrtf, CallbackInfoReturnable<IntBuffer> cir) {
		if (AudioCapture.getArmed()) cir.setReturnValue(AudioCapture.attributes(stack, cir.getReturnValue()));
	}
}
