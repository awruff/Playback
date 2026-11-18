package org.polyfrost.keyframe.standalone.mixin;

import net.minecraft.client.resources.sounds.SoundInstance;
//? if >=1.21.10
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import org.polyfrost.keyframe.standalone.playback.LocalEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
//? if >=1.21.10 {
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//?} else
/*import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;*/

@Mixin(SoundManager.class)
abstract class Mixin_SoundManager_Audio {
	@Inject(method = "play", at = @At("RETURN"))
	//? if >=1.21.10 {
	private void keyframe$recordUiSound(SoundInstance instance, CallbackInfoReturnable<SoundEngine.PlayResult> cir) {
	//?} else
	/*private void keyframe$recordUiSound(SoundInstance instance, CallbackInfo ci) {*/
		LocalEvents.uiSound(instance);
	}
}
