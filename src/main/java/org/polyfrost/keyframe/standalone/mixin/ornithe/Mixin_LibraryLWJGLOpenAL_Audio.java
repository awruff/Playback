package org.polyfrost.keyframe.standalone.mixin.ornithe;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.polyfrost.keyframe.standalone.export.AudioCapture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import paulscode.sound.libraries.LibraryLWJGLOpenAL;

@Mixin(value = LibraryLWJGLOpenAL.class, remap = false)
abstract class Mixin_LibraryLWJGLOpenAL_Audio {
	@WrapOperation(method = "init", at = @At(value = "INVOKE", target = "Lorg/lwjgl/openal/AL;create()V"))
	private void keyframe$openLoopback(Operation<Void> original) {
		if (AudioCapture.getArmed()) {
			AudioCapture.open();
		} else {
			original.call();
		}
	}

	@WrapOperation(method = "cleanup", at = @At(value = "INVOKE", target = "Lorg/lwjgl/openal/AL;destroy()V"))
	private void keyframe$closeLoopback(Operation<Void> original) {
		if (!AudioCapture.close()) original.call();
	}
}
