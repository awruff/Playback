package org.polyfrost.keyframe.mixin.flashback;

import com.moulberry.flashback.playback.ReplayPlayer;
import org.polyfrost.keyframe.Keyframe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(ReplayPlayer.class)
public abstract class MixinReplayPlayer {

    @Inject(method = "<init>", at = @At("RETURN"))
    private void keyframe$keepSpectating(CallbackInfo ci) {
        ReplayPlayer self = (ReplayPlayer) (Object) this;
        UUID target = Keyframe.RESPECTATE.remove(self.getUUID());
        if (target != null) {
            self.spectatingUuid = target;
            self.spectatingUuidTickCount = 100;
        }
    }

}
