package org.polyfrost.polyreplay.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.record.Recorder;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import org.polyfrost.polyreplay.camera.CameraTrackAction;
import org.polyfrost.polyreplay.FirstPersonEffects;
import org.polyfrost.polyreplay.player.RecordedPlayerState;
import org.polyfrost.polyreplay.ui.ContainerScreenAction;
import org.polyfrost.polyreplay.ui.ScreenCapture;
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
    private void polyreplay$sampleCamera(DeltaTracker deltaTracker,
    //? if <26.2
    //boolean tick,
    CallbackInfo ci) {
        Recorder recorder = Flashback.RECORDER;
        if (recorder != null && !recorder.isPaused() && !Flashback.isInReplay() && this.minecraft.player != null) {
            CameraTrackAction.sample(this.minecraft.player, deltaTracker.getGameTimeDeltaPartialTick(true));
        }
    }

    //? if 26.1 {
    /*@WrapOperation(method = "extractGui", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;extractRenderStateWithTooltipAndSubtitles(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"))
    private void polyreplay$captureScreen(Screen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tickDelta, Operation<Void> original) {
        this.polyreplay$capture(screen, graphics, mouseX, mouseY, tickDelta, original);
    }
    *///?}

    //? if >=1.21.10 && <26.1 {
    /*@WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;extractRenderStateWithTooltipAndSubtitles(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"))
    private void polyreplay$captureScreen(Screen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tickDelta, Operation<Void> original) {
        this.polyreplay$capture(screen, graphics, mouseX, mouseY, tickDelta, original);
    }
    *///?}

    //? if <1.21.10 {
    /*@WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;renderWithTooltip(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"))
    private void polyreplay$captureScreen(Screen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tickDelta, Operation<Void> original) {
        this.polyreplay$capture(screen, graphics, mouseX, mouseY, tickDelta, original);
    }
    *///?}

    //? if <26.2 {
    /*private void polyreplay$capture(Screen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tickDelta, Operation<Void> original) {
        boolean capture = !ContainerScreenAction.recordMouse(screen) && ScreenCapture.recording();
        if (capture) {
            ScreenCapture.beginLayer(false);
        }
        original.call(screen, graphics, mouseX, mouseY, tickDelta);
        if (capture) {
            ScreenCapture.endScreen(graphics, this.minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false));
        }
    }
    *///?}

    //? if <26.3 {
    /*@Inject(method = "renderItemInHand", at = @At("HEAD"))
    private void polyreplay$applyRecordedHands(CallbackInfo ci) {
        AbstractClientPlayer player = FirstPersonEffects.spectated();
        if (player != null) {
            RecordedPlayerState.applyHands(player, this.minecraft.gameRenderer.itemInHandRenderer);
        }
    }
    *///?}

    //? if >=1.21.8 && <26.3 {
    /*@ModifyExpressionValue(method = "tick", at = @At(value = "FIELD", target = "Lnet/minecraft/client/player/LocalPlayer;portalEffectIntensity:F"))
    private float polyreplay$tickPortalIntensity(float original) {
        return FirstPersonEffects.spectated() != null ? FirstPersonEffects.portalIntensity() : original;
    }

    @ModifyExpressionValue(method = "renderLevel", at = @At(value = "FIELD", target = "Lnet/minecraft/client/player/LocalPlayer;oPortalEffectIntensity:F"))
    private float polyreplay$oPortalIntensity(float original) {
        return FirstPersonEffects.spectated() != null ? FirstPersonEffects.oPortalIntensity() : original;
    }

    @ModifyExpressionValue(method = "renderLevel", at = @At(value = "FIELD", target = "Lnet/minecraft/client/player/LocalPlayer;portalEffectIntensity:F"))
    private float polyreplay$portalIntensity(float original) {
        return FirstPersonEffects.spectated() != null ? FirstPersonEffects.portalIntensity() : original;
    }

    @WrapOperation(method = {"tick", "renderLevel"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getEffectBlendFactor(Lnet/minecraft/core/Holder;F)F"))
    private float polyreplay$getEffectBlendFactor(LocalPlayer instance, Holder<MobEffect> effect, float partialTick, Operation<Float> original) {
        float blend = original.call(instance, effect, partialTick);
        AbstractClientPlayer player = FirstPersonEffects.spectated();
        return player != null ? player.getEffectBlendFactor(effect, partialTick) : blend;
    }
    *///?}

    //? if <1.21.8 {
    /*@ModifyExpressionValue(method = {"extractRenderState", "renderLevel"}, at = @At(value = "FIELD", target = "Lnet/minecraft/client/player/LocalPlayer;oSpinningEffectIntensity:F"))
    private float polyreplay$oSpinningIntensity(float original) {
        return FirstPersonEffects.spectated() != null ? FirstPersonEffects.oPortalIntensity() : original;
    }

    @ModifyExpressionValue(method = {"extractRenderState", "renderLevel"}, at = @At(value = "FIELD", target = "Lnet/minecraft/client/player/LocalPlayer;spinningEffectIntensity:F"))
    private float polyreplay$spinningIntensity(float original) {
        return FirstPersonEffects.spectated() != null ? FirstPersonEffects.portalIntensity() : original;
    }

    @WrapOperation(method = {"extractRenderState", "renderLevel"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;hasEffect(Lnet/minecraft/core/Holder;)Z"))
    private boolean polyreplay$hasEffect(LocalPlayer instance, Holder<MobEffect> effect, Operation<Boolean> original) {
        boolean has = original.call(instance, effect);
        AbstractClientPlayer player = FirstPersonEffects.spectated();
        return player != null ? player.hasEffect(effect) : has;
    }
    *///?}

}
