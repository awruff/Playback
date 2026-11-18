package org.polyfrost.keyframe.mixin.ornithe;

import net.minecraft.client.network.handler.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.CameraS2CPacket;
import org.polyfrost.keyframe.player.SpectatedPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class MixinClientPlayNetworkHandlerCamera {

    @Inject(method = "handleCamera", at = @At("TAIL"))
    private void keyframe$retryUnknownCamera(CameraS2CPacket packet, CallbackInfo ci) {
        SpectatedPlayer.setPendingCamera(packet.id);
    }

}
