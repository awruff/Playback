package org.polyfrost.keyframe.mixin.ornithe;

import net.minecraft.client.sound.instance.SoundInstance;
import net.minecraft.client.sound.system.SoundManager;
import org.polyfrost.keyframe.ui.UiSoundAction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundManager.class)
public abstract class MixinSoundManagerScreens {

    @Inject(method = "play(Lnet/minecraft/client/sound/instance/SoundInstance;)V", at = @At("RETURN"))
    private void keyframe$captureUiSound(SoundInstance instance, CallbackInfo ci) {
        UiSoundAction.capture(instance);
    }

}
