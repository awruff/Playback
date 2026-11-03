package org.polyfrost.playback.mixin.flashback;

import com.moulberry.flashback.playback.ReplayPlayer;
import org.polyfrost.playback.Playback;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(ReplayPlayer.class)
public abstract class MixinReplayPlayer {

    @Inject(method = "<init>", at = @At("RETURN"))
    private void playback$keepSpectating(CallbackInfo ci) {
        ReplayPlayer self = (ReplayPlayer) (Object) this;
        UUID target = Playback.RESPECTATE.remove(self.getUUID());
        if (target != null) {
            self.spectatingUuid = target;
            self.spectatingUuidTickCount = 100;
        }
    }

}
