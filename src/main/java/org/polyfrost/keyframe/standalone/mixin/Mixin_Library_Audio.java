package org.polyfrost.keyframe.standalone.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
//? if <1.21.10
/*import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;*/
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.audio.Library;
import java.nio.IntBuffer;
import org.polyfrost.keyframe.standalone.export.AudioCapture;
import org.lwjgl.system.MemoryStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Library.class)
abstract class Mixin_Library_Audio {
	//? if >=26.1 {
	@WrapMethod(method = "openDeviceOrFallback")
	private static long keyframe$openLoopback(String preferredDevice, String systemDefaultDevice, Operation<Long> original) {
		return AudioCapture.getArmed() ? AudioCapture.openDevice() : original.call(preferredDevice, systemDefaultDevice);
	}
	//?} else {
	/*@WrapMethod(method = "openDeviceOrFallback")
	private static long keyframe$openLoopback(String preferredDevice, Operation<Long> original) {
		return AudioCapture.getArmed() ? AudioCapture.openDevice() : original.call(preferredDevice);
	}
	*///?}

	@ModifyReturnValue(method = "isCurrentDeviceDisconnected", at = @At("RETURN"))
	private boolean keyframe$forceReopen(boolean disconnected) {
		return disconnected || AudioCapture.getSwitching();
	}

	//? if >=1.21.10 {
	@ModifyReturnValue(method = "createAttributes", at = @At("RETURN"))
	private IntBuffer keyframe$loopbackFormat(IntBuffer attributes, @Local(argsOnly = true) MemoryStack stack) {
		return AudioCapture.getArmed() ? AudioCapture.attributes(stack, attributes) : attributes;
	}
	//?} else {
	/*@WrapOperation(
		method = "init",
		at = @At(value = "INVOKE", target = "Lorg/lwjgl/openal/ALC10;alcCreateContext(JLjava/nio/IntBuffer;)J")
	)
	private long keyframe$loopbackFormat(long device, IntBuffer attributes, Operation<Long> original) {
		if (!AudioCapture.getArmed()) return original.call(device, attributes);
		try (MemoryStack stack = MemoryStack.stackPush()) {
			return original.call(device, AudioCapture.attributes(stack, attributes));
		}
	}
	*///?}
}
