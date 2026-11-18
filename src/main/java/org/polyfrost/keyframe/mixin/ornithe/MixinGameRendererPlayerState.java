package org.polyfrost.keyframe.mixin.ornithe;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.record.Recorder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.ClientPlayerEntity;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.entity.living.effect.StatusEffect;
import org.polyfrost.keyframe.camera.CameraTrackAction;
import org.polyfrost.keyframe.player.RecordedPlayerState;
import org.polyfrost.keyframe.player.SpectatedPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class MixinGameRendererPlayerState {

    @Shadow
    private Minecraft minecraft;

    @Inject(method = "renderWorld", at = @At("HEAD"))
    private void keyframe$sampleCamera(float tickDelta, long renderTimeLimit, CallbackInfo ci) {
        Recorder recorder = Flashback.RECORDER;
        if (recorder != null && !recorder.isPaused() && !Flashback.isInReplay() && this.minecraft.player != null) {
            CameraTrackAction.sample(this.minecraft.player, tickDelta);
        }
    }

    @Inject(method = "renderItemInHand", at = @At("HEAD"))
    private void keyframe$applyRecordedHands(CallbackInfo ci) {
        ClientPlayerEntity player = SpectatedPlayer.get();
        if (player != null) {
            RecordedPlayerState.applyHands(player, (ItemInHandRendererAccessor) ((GameRenderer) (Object) this).itemInHandRenderer);
        }
    }

    @ModifyExpressionValue(method = "setupCamera", at = @At(value = "FIELD", target = "Lnet/minecraft/client/entity/living/player/LocalClientPlayerEntity;lastPortalTime:F"))
    private float keyframe$oPortalIntensity(float original) {
        return SpectatedPlayer.get() != null ? SpectatedPlayer.oPortalIntensity() : original;
    }

    @ModifyExpressionValue(method = "setupCamera", at = @At(value = "FIELD", target = "Lnet/minecraft/client/entity/living/player/LocalClientPlayerEntity;portalTime:F"))
    private float keyframe$portalIntensity(float original) {
        return SpectatedPlayer.get() != null ? SpectatedPlayer.portalIntensity() : original;
    }

    @WrapOperation(method = "setupCamera", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/entity/living/player/LocalClientPlayerEntity;hasStatusEffect(Lnet/minecraft/entity/living/effect/StatusEffect;)Z"))
    private boolean keyframe$hasStatusEffect(LocalClientPlayerEntity instance, StatusEffect effect, Operation<Boolean> original) {
        boolean has = original.call(instance, effect);
        ClientPlayerEntity player = SpectatedPlayer.get();
        return player != null ? player.hasStatusEffect(effect) : has;
    }

}
