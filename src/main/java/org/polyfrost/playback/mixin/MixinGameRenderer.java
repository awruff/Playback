package org.polyfrost.playback.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import org.polyfrost.playback.camera.CameraTrackAction;
import org.polyfrost.playback.Playback;
import org.polyfrost.playback.player.RecordedPlayerState;
import org.polyfrost.playback.ui.ScreenCapture;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class MixinGameRenderer {

    @Shadow
    @Final
    Minecraft minecraft;

    //? if >=26.1 {
    @Inject(method = "update", at = @At("HEAD"))
    //?} else
    //@Inject(method = "extractRenderState", at = @At("HEAD"))
    private void playback$sampleCamera(DeltaTracker deltaTracker,
    //? if <26.2
    //boolean tick,
    CallbackInfo ci) {
        if (ScreenCapture.recording() && this.minecraft.player != null) {
            CameraTrackAction.sample(this.minecraft.player, deltaTracker.getGameTimeDeltaPartialTick(true));
        }
    }

    //? if 26.1 {
    /*@WrapOperation(method = "extractGui", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;extractRenderStateWithTooltipAndSubtitles(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"))
    private void playback$captureScreen(Screen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tickDelta, Operation<Void> original) {
        ScreenCapture.captureScreen(screen, graphics, () -> original.call(screen, graphics, mouseX, mouseY, tickDelta));
    }
    *///?}

    //? if >=1.21.10 && <26.1 {
    /*@WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;extractRenderStateWithTooltipAndSubtitles(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"))
    private void playback$captureScreen(Screen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tickDelta, Operation<Void> original) {
        ScreenCapture.captureScreen(screen, graphics, () -> original.call(screen, graphics, mouseX, mouseY, tickDelta));
    }
    *///?}

    //? if <1.21.10 {
    /*@WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;renderWithTooltip(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"))
    private void playback$captureScreen(Screen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tickDelta, Operation<Void> original) {
        ScreenCapture.captureScreen(screen, graphics, () -> original.call(screen, graphics, mouseX, mouseY, tickDelta));
    }
    *///?}

    //? if <26.3 {
    /*@Inject(method = "renderItemInHand", at = @At("HEAD"))
    private void playback$applyRecordedHands(CallbackInfo ci) {
        AbstractClientPlayer player = Playback.spectated();
        if (player != null) {
            RecordedPlayerState.applyHands(player, this.minecraft.gameRenderer.itemInHandRenderer);
        }
    }
    *///?}

    //? if >=1.21.8 && <26.3 {
    /*@ModifyExpressionValue(method = "tick", at = @At(value = "FIELD", target = "Lnet/minecraft/client/player/LocalPlayer;portalEffectIntensity:F"))
    private float playback$tickPortalIntensity(float original) {
        return Playback.spectated() != null ? Playback.portalIntensity() : original;
    }

    @ModifyExpressionValue(method = "renderLevel", at = @At(value = "FIELD", target = "Lnet/minecraft/client/player/LocalPlayer;oPortalEffectIntensity:F"))
    private float playback$oPortalIntensity(float original) {
        return Playback.spectated() != null ? Playback.oPortalIntensity() : original;
    }

    @ModifyExpressionValue(method = "renderLevel", at = @At(value = "FIELD", target = "Lnet/minecraft/client/player/LocalPlayer;portalEffectIntensity:F"))
    private float playback$portalIntensity(float original) {
        return Playback.spectated() != null ? Playback.portalIntensity() : original;
    }

    @WrapOperation(method = {"tick", "renderLevel"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getEffectBlendFactor(Lnet/minecraft/core/Holder;F)F"))
    private float playback$getEffectBlendFactor(LocalPlayer instance, Holder<MobEffect> effect, float partialTick, Operation<Float> original) {
        float blend = original.call(instance, effect, partialTick);
        AbstractClientPlayer player = Playback.spectated();
        return player != null ? player.getEffectBlendFactor(effect, partialTick) : blend;
    }
    *///?}

    //? if <1.21.8 {
    /*@ModifyExpressionValue(method = {"extractRenderState", "renderLevel"}, at = @At(value = "FIELD", target = "Lnet/minecraft/client/player/LocalPlayer;oSpinningEffectIntensity:F"))
    private float playback$oSpinningIntensity(float original) {
        return Playback.spectated() != null ? Playback.oPortalIntensity() : original;
    }

    @ModifyExpressionValue(method = {"extractRenderState", "renderLevel"}, at = @At(value = "FIELD", target = "Lnet/minecraft/client/player/LocalPlayer;spinningEffectIntensity:F"))
    private float playback$spinningIntensity(float original) {
        return Playback.spectated() != null ? Playback.portalIntensity() : original;
    }

    @WrapOperation(method = {"extractRenderState", "renderLevel"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;hasEffect(Lnet/minecraft/core/Holder;)Z"))
    private boolean playback$hasEffect(LocalPlayer instance, Holder<MobEffect> effect, Operation<Boolean> original) {
        boolean has = original.call(instance, effect);
        AbstractClientPlayer player = Playback.spectated();
        return player != null ? player.hasEffect(effect) : has;
    }
    *///?}

}
