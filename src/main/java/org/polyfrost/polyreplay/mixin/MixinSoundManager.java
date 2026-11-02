package org.polyfrost.polyreplay.mixin;

import net.minecraft.client.resources.sounds.SoundInstance;
//? if >=1.21.6
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import org.polyfrost.polyreplay.ui.UiSoundAction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
//? if >=1.21.6 {
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//?} else
//import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundManager.class)
public abstract class MixinSoundManager {

    @Inject(method = "play", at = @At("RETURN"))
    //? if >=1.21.6 {
    private void polyreplay$captureUiSound(SoundInstance instance, CallbackInfoReturnable<SoundEngine.PlayResult> cir) {
    //?} else
    //private void polyreplay$captureUiSound(SoundInstance instance, CallbackInfo ci) {
        UiSoundAction.capture(instance);
    }

}
