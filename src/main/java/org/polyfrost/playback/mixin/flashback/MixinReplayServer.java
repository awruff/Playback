package org.polyfrost.playback.mixin.flashback;

import com.moulberry.flashback.playback.ReplayPlayer;
import com.moulberry.flashback.playback.ReplayServer;
import net.minecraft.server.level.ServerPlayer;
import org.polyfrost.playback.Playback;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ReplayServer.class)
public abstract class MixinReplayServer {

    @Inject(method = "updateRegistry", at = @At("HEAD"))
    private void playback$rememberSpectating(CallbackInfo ci) {
        Playback.RESPECTATE.clear();
        for (ServerPlayer player : ((ReplayServer) (Object) this).getPlayerList().getPlayers()) {
            if (player instanceof ReplayPlayer viewer && viewer.spectatingUuid != null) {
                Playback.RESPECTATE.put(viewer.getUUID(), viewer.spectatingUuid);
            }
        }
    }

}
