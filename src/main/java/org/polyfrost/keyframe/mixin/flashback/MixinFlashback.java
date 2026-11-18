package org.polyfrost.keyframe.mixin.flashback;

import com.moulberry.flashback.Flashback;
import org.polyfrost.keyframe.shadow.ShadowReplay;
import org.polyfrost.keyframe.shadow.ShadowReplayConfig;
import org.polyfrost.keyframe.ui.ScreenCapture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Flashback.class)
public abstract class MixinFlashback {

    @Inject(method = "startRecordingReplay()V", at = @At("HEAD"))
    private static void keyframe$dropShadowRecorder(CallbackInfo ci) {
        if (ShadowReplay.isShadow(Flashback.RECORDER)) {
            Flashback.RECORDER = null;
        }
    }

    @Inject(method = {"finishRecordingReplay()V", "cancelRecordingReplay()V", "pauseRecordingReplay(Z)V"}, at = @At("HEAD"), cancellable = true)
    private static void keyframe$ignoreShadowReplay(CallbackInfo ci) {
        if (ShadowReplay.isShadow(Flashback.RECORDER)) {
            ci.cancel();
        }
    }

    @Inject(method = "finishRecordingReplay()V", at = @At("HEAD"), cancellable = true)
    private static void keyframe$keepRecordingThroughMenus(CallbackInfo ci) {
        if (ScreenCapture.disconnecting && ShadowReplayConfig.recordMainMenu) {
            ci.cancel();
        }
    }

    @Inject(method = {"startRecordingReplay()V", "finishRecordingReplay()V", "cancelRecordingReplay()V"}, at = @At("RETURN"))
    private static void keyframe$resumeShadowReplay(CallbackInfo ci) {
        ShadowReplay.restore();
    }

}
