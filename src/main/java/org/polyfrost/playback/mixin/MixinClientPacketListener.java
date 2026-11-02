package org.polyfrost.playback.mixin;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.world.entity.EntityEvent;
import org.polyfrost.playback.shadow.AutoMarkers;
import org.polyfrost.playback.Playback;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class MixinClientPacketListener {

    //? if >=1.21.10 {
    @Inject(method = "handleEntityEvent", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V", shift = At.Shift.AFTER))
    //?} else {
    /*@Inject(method = "handleEntityEvent", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/util/thread/BlockableEventLoop;)V", shift = At.Shift.AFTER))
    *///?}
    private void playback$spectatedTotem(ClientboundEntityEventPacket packet, CallbackInfo ci) {
        if (packet.getEventId() != EntityEvent.PROTECTED_FROM_DEATH) {
            return;
        }
        AbstractClientPlayer player = Playback.spectated();
        if (player != null && packet.getEntity(player.level()) == player) {
            Playback.activateTotem(player);
        }
    }

    //? if >=1.21.10 {
    @Inject(method = "handleDamageEvent", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V", shift = At.Shift.AFTER))
    //?} else {
    /*@Inject(method = "handleDamageEvent", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/util/thread/BlockableEventLoop;)V", shift = At.Shift.AFTER))
    *///?}
    private void playback$autoMarkDamage(ClientboundDamageEventPacket packet, CallbackInfo ci) {
        AutoMarkers.onDamage(packet.entityId());
    }

}
