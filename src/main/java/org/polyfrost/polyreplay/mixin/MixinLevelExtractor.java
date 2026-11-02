package org.polyfrost.polyreplay.mixin;

//? if >=26.3 {
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.ItemActivation;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import org.polyfrost.polyreplay.FirstPersonEffects;
import org.polyfrost.polyreplay.player.RecordedPlayerState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelExtractor.class)
public abstract class MixinLevelExtractor {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    @Final
    private LevelRenderState levelRenderState;

    @Inject(method = "extract", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/extract/LevelExtractor;extractPlayerState(Lnet/minecraft/client/Camera;Lnet/minecraft/client/DeltaTracker;FLnet/minecraft/client/renderer/state/level/PlayerRenderState;)V", shift = At.Shift.AFTER))
    private void polyreplay$extractSpectatedEffects(DeltaTracker deltaTracker, Camera camera, float worldPartialTicks, CallbackInfo ci) {
        AbstractClientPlayer player = FirstPersonEffects.spectated();
        PlayerRenderState state = this.levelRenderState.playerRenderState;
        if (player == null || !state.hasPlayer) {
            return;
        }

        var hands = state.firstPersonHandsAndItems;
        RecordedPlayerState.applyHands(player, hands);
        hands.yBob = hands.viewYRot - Mth.wrapDegrees(hands.viewYRot - hands.yBob);

        state.portalEffectIntensity = FirstPersonEffects.portalIntensity(worldPartialTicks);
        state.spinningEffectAngle = FirstPersonEffects.spinningAngle(worldPartialTicks);

        ItemActivation activation = FirstPersonEffects.ITEM_ACTIVATION;
        if (activation.isActive()) {
            var activationState = new PlayerRenderState.ItemActivationRenderState(activation.item().copy(), activation.ticks(), activation.offX(), activation.offY());
            this.minecraft.getItemModelResolver().updateForTopItem(activationState.itemState, activationState.item, ItemDisplayContext.FIXED, player.level(), null, 0);
            state.itemActivation = activationState;
        }
    }

}
//?}
