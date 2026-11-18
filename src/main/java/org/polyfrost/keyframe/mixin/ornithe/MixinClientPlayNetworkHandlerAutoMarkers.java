package org.polyfrost.keyframe.mixin.ornithe;

import net.minecraft.client.Minecraft;
import net.minecraft.client.network.handler.ClientPlayNetworkHandler;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.s2c.play.ChatMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityEventS2CPacket;
import org.polyfrost.keyframe.shadow.AutoMarkers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class MixinClientPlayNetworkHandlerAutoMarkers {

    @Inject(method = "handleEntityEvent", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/PacketUtils;ensureOnSameThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/handler/PacketHandler;Lnet/minecraft/util/BlockableEventLoop;)V", shift = At.Shift.AFTER))
    private void keyframe$autoMarkDamage(EntityEventS2CPacket packet, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        Entity entity = minecraft.world == null ? null : packet.getEntity(minecraft.world);
        if (packet.getEvent() == 2 && entity != null) {
            AutoMarkers.onDamage(entity.getNetworkId());
        }
    }

    @Inject(method = "handleChatMessage", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/PacketUtils;ensureOnSameThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/handler/PacketHandler;Lnet/minecraft/util/BlockableEventLoop;)V", shift = At.Shift.AFTER))
    private void keyframe$autoMarkMessage(ChatMessageS2CPacket packet, CallbackInfo ci) {
        if (packet.getType() != 2) {
            AutoMarkers.onSystemMessage(packet.getMessage());
        }
    }

}
