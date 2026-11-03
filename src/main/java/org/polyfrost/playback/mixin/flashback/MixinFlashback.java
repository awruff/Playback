package org.polyfrost.playback.mixin.flashback;

import com.moulberry.flashback.Flashback;
import org.polyfrost.playback.shadow.ShadowReplay;
import org.polyfrost.playback.shadow.ShadowReplayConfig;
import org.polyfrost.playback.ui.ScreenCapture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Flashback.class)
public abstract class MixinFlashback {

    @Inject(method = "startRecordingReplay()V", at = @At("HEAD"))
    private static void playback$dropShadowRecorder(CallbackInfo ci) {
        if (ShadowReplay.isShadow(Flashback.RECORDER)) {
            Flashback.RECORDER = null;
        }
    }

    @Inject(method = {"finishRecordingReplay()V", "cancelRecordingReplay()V", "pauseRecordingReplay(Z)V"}, at = @At("HEAD"), cancellable = true)
    private static void playback$ignoreShadowReplay(CallbackInfo ci) {
        if (ShadowReplay.isShadow(Flashback.RECORDER)) {
            ci.cancel();
        }
    }

    @Inject(method = "finishRecordingReplay()V", at = @At("HEAD"), cancellable = true)
    private static void playback$keepRecordingThroughMenus(CallbackInfo ci) {
        if (ScreenCapture.disconnecting && ShadowReplayConfig.recordMainMenu) {
            ci.cancel();
        }
    }

    @Inject(method = {"startRecordingReplay()V", "finishRecordingReplay()V", "cancelRecordingReplay()V"}, at = @At("RETURN"))
    private static void playback$resumeShadowReplay(CallbackInfo ci) {
        ShadowReplay.restore();
    }

}
