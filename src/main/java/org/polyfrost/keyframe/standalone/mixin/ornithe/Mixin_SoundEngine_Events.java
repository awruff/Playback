package org.polyfrost.keyframe.standalone.mixin.ornithe;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.sound.instance.SoundInstance;
import net.minecraft.client.sound.system.SoundEngine;
import org.polyfrost.keyframe.standalone.export.AudioCapture;
import org.polyfrost.keyframe.standalone.playback.LocalEvents;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundEngine.class)
abstract class Mixin_SoundEngine_Events {
	@Inject(method = "tick", at = @At("TAIL"))
	private void keyframe$drainLoopback(CallbackInfo ci) {
		AudioCapture.tick();
	}

	@ModifyExpressionValue(
		method = "play",
		at = @At(value = "FIELD", target = "Lnet/minecraft/client/sound/system/SoundEngine;started:Z", opcode = Opcodes.GETFIELD)
	)
	private boolean keyframe$muteDoubledUi(boolean started, SoundInstance instance) {
		return started && !LocalEvents.mutes(instance);
	}
}
