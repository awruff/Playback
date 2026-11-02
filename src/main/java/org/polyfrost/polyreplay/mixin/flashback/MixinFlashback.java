package org.polyfrost.polyreplay.mixin.flashback;

import com.moulberry.flashback.Flashback;
import org.polyfrost.polyreplay.shadow.ShadowReplay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Flashback.class)
public abstract class MixinFlashback {

    @Inject(method = "startRecordingReplay()V", at = @At("HEAD"))
    private static void polyreplay$dropShadowRecorder(CallbackInfo ci) {
        if (ShadowReplay.isShadow(Flashback.RECORDER)) {
            Flashback.RECORDER = null;
        }
    }

    @Inject(method = {"finishRecordingReplay()V", "cancelRecordingReplay()V", "pauseRecordingReplay(Z)V"}, at = @At("HEAD"), cancellable = true)
    private static void polyreplay$ignoreShadowReplay(CallbackInfo ci) {
        if (ShadowReplay.isShadow(Flashback.RECORDER)) {
            ci.cancel();
        }
    }

    @Inject(method = {"startRecordingReplay()V", "finishRecordingReplay()V", "cancelRecordingReplay()V"}, at = @At("RETURN"))
    private static void polyreplay$resumeShadowReplay(CallbackInfo ci) {
        ShadowReplay.restore();
    }

}
