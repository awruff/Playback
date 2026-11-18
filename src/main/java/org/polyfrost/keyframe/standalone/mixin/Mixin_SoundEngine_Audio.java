package org.polyfrost.keyframe.standalone.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundEngineExecutor;
import org.polyfrost.keyframe.standalone.export.AudioCapture;
import org.polyfrost.keyframe.standalone.playback.LocalEvents;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundEngine.class)
abstract class Mixin_SoundEngine_Audio {
	@Shadow
	@Final
	private SoundEngineExecutor executor;

	@Inject(method = "loadLibrary", at = @At("HEAD"))
	private void keyframe$shareExecutor(CallbackInfo ci) {
		AudioCapture.setExecutor(this.executor);
	}

	@ModifyReturnValue(method = "shouldChangeDevice", at = @At("RETURN"))
	private boolean keyframe$holdLoopback(boolean change) {
		return change && !AudioCapture.getArmed();
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void keyframe$drainLoopback(boolean paused, CallbackInfo ci) {
		AudioCapture.tick();
	}

	@ModifyExpressionValue(
		method = "play",
		at = @At(value = "INVOKE", target = "Ljava/util/concurrent/CompletableFuture;thenAccept(Ljava/util/function/Consumer;)Ljava/util/concurrent/CompletableFuture;")
	)
	private CompletableFuture<?> keyframe$trackLoad(CompletableFuture<?> load) {
		return AudioCapture.trackLoad(load);
	}

	@ModifyExpressionValue(
		method = "play",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resources/sounds/SoundInstance;canPlaySound()Z")
	)
	private boolean keyframe$muteDoubledUi(boolean canPlay, @Local(argsOnly = true) SoundInstance instance) {
		return canPlay && !LocalEvents.mutes(instance);
	}
}
