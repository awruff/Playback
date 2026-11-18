package org.polyfrost.keyframe.mixin.flashback;

import com.moulberry.flashback.playback.ReplayPlayer;
import com.moulberry.flashback.playback.ReplayServer;
import net.minecraft.server.level.ServerPlayer;
import org.polyfrost.keyframe.Keyframe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ReplayServer.class)
public abstract class MixinReplayServer {

    @Inject(method = "updateRegistry", at = @At("HEAD"))
    private void keyframe$rememberSpectating(CallbackInfo ci) {
        Keyframe.RESPECTATE.clear();
        for (ServerPlayer player : ((ReplayServer) (Object) this).getPlayerList().getPlayers()) {
            if (player instanceof ReplayPlayer viewer && viewer.spectatingUuid != null) {
                Keyframe.RESPECTATE.put(viewer.getUUID(), viewer.spectatingUuid);
            }
        }
    }

}
