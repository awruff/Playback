package org.polyfrost.keyframe.mixin;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundSetCameraPacket;
import net.minecraft.world.entity.EntityEvent;
import org.polyfrost.keyframe.shadow.AutoMarkers;
import org.polyfrost.keyframe.Keyframe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class MixinClientPacketListener {

    @Inject(method = "handleEntityEvent", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V", shift = At.Shift.AFTER))
    private void keyframe$spectatedTotem(ClientboundEntityEventPacket packet, CallbackInfo ci) {
        if (packet.getEventId() != EntityEvent.PROTECTED_FROM_DEATH) {
            return;
        }
        AbstractClientPlayer player = Keyframe.spectated();
        if (player != null && packet.getEntity(player.level()) == player) {
            Keyframe.activateTotem(player);
        }
    }

    @Inject(method = "handleDamageEvent", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V", shift = At.Shift.AFTER))
    private void keyframe$autoMarkDamage(ClientboundDamageEventPacket packet, CallbackInfo ci) {
        AutoMarkers.onDamage(packet.entityId());
    }

    @Inject(method = "handleSetCamera", at = @At("TAIL"))
    private void keyframe$retryUnknownCamera(ClientboundSetCameraPacket packet, CallbackInfo ci) {
        Keyframe.setPendingCamera(packet.cameraId);
    }

}
