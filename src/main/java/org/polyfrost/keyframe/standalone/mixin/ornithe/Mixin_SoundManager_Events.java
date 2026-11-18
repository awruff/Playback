package org.polyfrost.keyframe.standalone.mixin.ornithe;

import net.minecraft.client.sound.instance.SoundInstance;
import net.minecraft.client.sound.system.SoundManager;
import org.polyfrost.keyframe.standalone.playback.LocalEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundManager.class)
abstract class Mixin_SoundManager_Events {
	@Inject(method = "play(Lnet/minecraft/client/sound/instance/SoundInstance;)V", at = @At("RETURN"))
	private void keyframe$recordUiSound(SoundInstance instance, CallbackInfo ci) {
		LocalEvents.uiSound(instance);
	}
}
