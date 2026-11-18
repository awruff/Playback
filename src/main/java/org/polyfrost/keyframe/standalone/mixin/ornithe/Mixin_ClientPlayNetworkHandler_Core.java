package org.polyfrost.keyframe.standalone.mixin.ornithe;

import net.minecraft.client.network.handler.ClientPlayNetworkHandler;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.s2c.play.ChatMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityEventS2CPacket;
import org.polyfrost.keyframe.standalone.recording.Highlights;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
abstract class Mixin_ClientPlayNetworkHandler_Core {
	@Unique
	private static final int HURT_EVENT = 2;

	@Unique
	private static final byte ACTION_BAR = 2;

	@Shadow
	private ClientWorld world;

	@Inject(method = "closeWorld", at = @At("HEAD"))
	private void keyframe$resetHighlights(CallbackInfo ci) {
		Highlights.reset();
	}

	@Inject(
		method = "handleEntityEvent",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/network/packet/s2c/play/EntityEventS2CPacket;getEntity(Lnet/minecraft/world/World;)Lnet/minecraft/entity/Entity;")
	)
	private void keyframe$onDamage(EntityEventS2CPacket packet, CallbackInfo ci) {
		Entity entity = packet.getEntity(this.world);
		if (entity != null && packet.getEvent() == HURT_EVENT) Highlights.onDamage(entity.getNetworkId());
	}

	@Inject(
		method = "handleChatMessage",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/network/packet/s2c/play/ChatMessageS2CPacket;getType()B", ordinal = 0)
	)
	private void keyframe$onMessage(ChatMessageS2CPacket packet, CallbackInfo ci) {
		if (packet.getType() != ACTION_BAR) Highlights.onMessage(packet.getMessage().getString());
	}
}
