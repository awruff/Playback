package org.polyfrost.playback.mixin.flashback;

import com.moulberry.flashback.playback.ReplayGamePacketHandler;
import net.minecraft.network.protocol.game.ClientboundRemoveMobEffectPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ReplayGamePacketHandler.class)
public abstract class MixinReplayGamePacketHandler {

    @Shadow
    private Entity getEntityOrPending(int entityId) {
        throw new AssertionError();
    }

    @Shadow
    public abstract ServerLevel level();

    @Inject(method = "handleUpdateMobEffect", at = @At("TAIL"))
    private void playback$sendEffectToViewers(ClientboundUpdateMobEffectPacket packet, CallbackInfo ci) {
        if (this.getEntityOrPending(packet.getEntityId()) instanceof LivingEntity entity) {
            this.level().getChunkSource().sendToTrackingPlayers(entity, packet);
        }
    }

    @Inject(method = "handleRemoveMobEffect", at = @At("TAIL"))
    private void playback$sendEffectRemovalToViewers(ClientboundRemoveMobEffectPacket packet, CallbackInfo ci) {
        if (this.getEntityOrPending(packet.entityId()) instanceof LivingEntity entity) {
            this.level().getChunkSource().sendToTrackingPlayers(entity, packet);
        }
    }

}
