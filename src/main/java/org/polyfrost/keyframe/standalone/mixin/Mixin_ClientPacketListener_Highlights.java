package org.polyfrost.keyframe.standalone.mixin;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import org.polyfrost.keyframe.standalone.recording.Highlights;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
abstract class Mixin_ClientPacketListener_Highlights {
	@Inject(
		method = "handleDamageEvent",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V", shift = At.Shift.AFTER)
	)
	private void keyframe$onDamage(ClientboundDamageEventPacket packet, CallbackInfo ci) {
		Highlights.INSTANCE.onDamage(packet.entityId());
	}

	@Inject(method = "close", at = @At("HEAD"))
	private void keyframe$resetHighlights(CallbackInfo ci) {
		Highlights.INSTANCE.reset();
	}
}
