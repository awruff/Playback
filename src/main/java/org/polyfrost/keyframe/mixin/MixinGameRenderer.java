package org.polyfrost.keyframe.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import org.polyfrost.keyframe.camera.CameraTrackAction;
import org.polyfrost.keyframe.Keyframe;
import org.polyfrost.keyframe.player.RecordedPlayerState;
import org.polyfrost.keyframe.ui.ScreenCapture;
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

    //? if >=1.21.2 && <26.1 {
    /*@ModifyExpressionValue(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;getFov(Lnet/minecraft/client/Camera;FZ)F", ordinal = 0))
    private float keyframe$spectatedFov(float fov) {
        boolean firstPerson = this.minecraft.options.getCameraType().isFirstPerson();
        return CameraTrackAction.fov(fov, firstPerson ? Keyframe.spectated() : null, this.minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(true));
    }
    *///?}

    //? if <1.21.2 {
    /*@ModifyExpressionValue(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;getFov(Lnet/minecraft/client/Camera;FZ)D", ordinal = 0))
    private double keyframe$spectatedFov(double fov) {
        boolean firstPerson = this.minecraft.options.getCameraType().isFirstPerson();
        return CameraTrackAction.fov((float) fov, firstPerson ? Keyframe.spectated() : null, this.minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(true));
    }
    *///?}

    //? if 26.1 {
    /*@WrapOperation(method = "extractGui", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;extractRenderStateWithTooltipAndSubtitles(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"))
    private void keyframe$captureScreen(Screen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tickDelta, Operation<Void> original) {
        ScreenCapture.captureScreen(screen, graphics, () -> original.call(screen, graphics, mouseX, mouseY, tickDelta));
    }
    *///?}

    //? if <26.1 {
    /*@WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;extractRenderStateWithTooltipAndSubtitles(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"))
    private void keyframe$captureScreen(Screen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tickDelta, Operation<Void> original) {
        ScreenCapture.captureScreen(screen, graphics, () -> original.call(screen, graphics, mouseX, mouseY, tickDelta));
    }
    *///?}

    //? if <26.3 {
    /*@Inject(method = "renderItemInHand", at = @At("HEAD"))
    private void keyframe$applyRecordedHands(CallbackInfo ci) {
        AbstractClientPlayer player = Keyframe.spectated();
        if (player != null) {
            RecordedPlayerState.applyHands(player, this.minecraft.gameRenderer.itemInHandRenderer);
        }
    }
    *///?}

    //? if >=1.21.8 && <26.3 {
    /*@ModifyExpressionValue(method = "tick", at = @At(value = "FIELD", target = "Lnet/minecraft/client/player/LocalPlayer;portalEffectIntensity:F"))
    private float keyframe$tickPortalIntensity(float original) {
        return Keyframe.spectated() != null ? Keyframe.portalIntensity() : original;
    }

    @ModifyExpressionValue(method = "renderLevel", at = @At(value = "FIELD", target = "Lnet/minecraft/client/player/LocalPlayer;oPortalEffectIntensity:F"))
    private float keyframe$oPortalIntensity(float original) {
        return Keyframe.spectated() != null ? Keyframe.oPortalIntensity() : original;
    }

    @ModifyExpressionValue(method = "renderLevel", at = @At(value = "FIELD", target = "Lnet/minecraft/client/player/LocalPlayer;portalEffectIntensity:F"))
    private float keyframe$portalIntensity(float original) {
        return Keyframe.spectated() != null ? Keyframe.portalIntensity() : original;
    }

    @WrapOperation(method = {"tick", "renderLevel"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getEffectBlendFactor(Lnet/minecraft/core/Holder;F)F"))
    private float keyframe$getEffectBlendFactor(LocalPlayer instance, Holder<MobEffect> effect, float partialTick, Operation<Float> original) {
        float blend = original.call(instance, effect, partialTick);
        AbstractClientPlayer player = Keyframe.spectated();
        return player != null ? player.getEffectBlendFactor(effect, partialTick) : blend;
    }
    *///?}

    //? if <1.21.8 {
    /*@ModifyExpressionValue(method = {"extractRenderState", "renderLevel"}, at = @At(value = "FIELD", target = "Lnet/minecraft/client/player/LocalPlayer;oSpinningEffectIntensity:F"))
    private float keyframe$oSpinningIntensity(float original) {
        return Keyframe.spectated() != null ? Keyframe.oPortalIntensity() : original;
    }

    @ModifyExpressionValue(method = {"extractRenderState", "renderLevel"}, at = @At(value = "FIELD", target = "Lnet/minecraft/client/player/LocalPlayer;spinningEffectIntensity:F"))
    private float keyframe$spinningIntensity(float original) {
        return Keyframe.spectated() != null ? Keyframe.portalIntensity() : original;
    }

    @WrapOperation(method = {"extractRenderState", "renderLevel"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;hasEffect(Lnet/minecraft/core/Holder;)Z"))
    private boolean keyframe$hasEffect(LocalPlayer instance, Holder<MobEffect> effect, Operation<Boolean> original) {
        boolean has = original.call(instance, effect);
        AbstractClientPlayer player = Keyframe.spectated();
        return player != null ? player.hasEffect(effect) : has;
    }
    *///?}

}
