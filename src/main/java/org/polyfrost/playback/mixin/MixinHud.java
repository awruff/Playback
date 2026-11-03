package org.polyfrost.playback.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
//? if <1.21.8
//import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
//? if >=1.21.4
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import org.polyfrost.playback.ui.ContainerScreenAction;
import org.polyfrost.playback.Playback;
import org.polyfrost.playback.player.RecordedPlayerState;
import org.polyfrost.playback.ui.ScreenCapture;
import org.polyfrost.playback.ui.ScreenReplay;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import net.minecraft.client.gui.screens.ChatScreen;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//? if <1.21.2
//import net.minecraft.world.entity.Entity;

import java.util.Collection;

@Mixin(Hud.class)
public abstract class MixinHud {

    @ModifyExpressionValue(method = "extractCameraOverlays", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isScoping()Z"))
    private boolean playback$isScoping(boolean original) {
        AbstractClientPlayer player = Playback.spectated();
        return player != null ? player.isScoping() : original;
    }

    //? if >=1.21.4 {
    @WrapOperation(method = "extractCameraOverlays", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getItemBySlot(Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack playback$getItemBySlot(LocalPlayer instance, EquipmentSlot slot, Operation<ItemStack> original) {
        ItemStack item = original.call(instance, slot);
        AbstractClientPlayer player = Playback.spectated();
        return player != null ? player.getItemBySlot(slot) : item;
    }
    //?} else {
    /*@ModifyExpressionValue(method = "extractCameraOverlays", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getInventory()Lnet/minecraft/world/entity/player/Inventory;"))
    private Inventory playback$getArmorInventory(Inventory original) {
        AbstractClientPlayer player = Playback.spectated();
        return player != null ? player.getInventory() : original;
    }
    *///?}

    @ModifyExpressionValue(method = "extractCameraOverlays", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getTicksFrozen()I"))
    private int playback$getTicksFrozen(int original) {
        AbstractClientPlayer player = Playback.spectated();
        return player != null ? player.getTicksFrozen() : original;
    }

    @ModifyExpressionValue(method = "extractCameraOverlays", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getPercentFrozen()F"))
    private float playback$getPercentFrozen(float original) {
        AbstractClientPlayer player = Playback.spectated();
        return player != null ? player.getPercentFrozen() : original;
    }

    //? if >=1.21.8 {
    @ModifyExpressionValue(method = "extractCameraOverlays", at = @At(value = "FIELD", target = "Lnet/minecraft/client/player/LocalPlayer;oPortalEffectIntensity:F"))
    private float playback$oPortalEffectIntensity(float original) {
        return Playback.spectated() != null ? Playback.oPortalIntensity() : original;
    }

    @ModifyExpressionValue(method = "extractCameraOverlays", at = @At(value = "FIELD", target = "Lnet/minecraft/client/player/LocalPlayer;portalEffectIntensity:F"))
    private float playback$portalEffectIntensity(float original) {
        return Playback.spectated() != null ? Playback.portalIntensity() : original;
    }

    @WrapOperation(method = "extractCameraOverlays", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getEffectBlendFactor(Lnet/minecraft/core/Holder;F)F"))
    private float playback$getEffectBlendFactor(LocalPlayer instance, Holder<MobEffect> effect, float partialTick, Operation<Float> original) {
        float blend = original.call(instance, effect, partialTick);
        AbstractClientPlayer player = Playback.spectated();
        return player != null ? player.getEffectBlendFactor(effect, partialTick) : blend;
    }
    //?} else {
    /*@ModifyExpressionValue(method = "extractCameraOverlays", at = @At(value = "FIELD", target = "Lnet/minecraft/client/player/LocalPlayer;oSpinningEffectIntensity:F"))
    private float playback$oSpinningEffectIntensity(float original) {
        return Playback.spectated() != null ? Playback.oPortalIntensity() : original;
    }

    @ModifyExpressionValue(method = "extractCameraOverlays", at = @At(value = "FIELD", target = "Lnet/minecraft/client/player/LocalPlayer;spinningEffectIntensity:F"))
    private float playback$spinningEffectIntensity(float original) {
        return Playback.spectated() != null ? Playback.portalIntensity() : original;
    }

    @WrapOperation(method = "extractCameraOverlays", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;hasEffect(Lnet/minecraft/core/Holder;)Z"))
    private boolean playback$hasEffect(LocalPlayer instance, Holder<MobEffect> effect, Operation<Boolean> original) {
        boolean has = original.call(instance, effect);
        AbstractClientPlayer player = Playback.spectated();
        return player != null ? player.hasEffect(effect) : has;
    }
    *///?}

    @ModifyExpressionValue(method = "extractEffects", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getActiveEffects()Ljava/util/Collection;"))
    private Collection<MobEffectInstance> playback$getActiveEffects(Collection<MobEffectInstance> original) {
        AbstractClientPlayer player = Playback.spectated();
        return player != null ? player.getActiveEffects() : original;
    }

    @ModifyExpressionValue(method = "extractItemHotbar", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getAttackStrengthScale(F)F"))
    private float playback$hotbarAttackStrength(float original) {
        AbstractClientPlayer player = Playback.spectated();
        return player != null ? player.getAttackStrengthScale(0.0f) : original;
    }

    @ModifyExpressionValue(method = "extractSelectedItemName", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;canHurtPlayer()Z"))
    private boolean playback$spectatedIsSurvival(boolean original) {
        AbstractClientPlayer player = Playback.spectated();
        //? if >=1.21.8 {
        GameType gameType = player != null ? player.gameMode() : null;
        //?} else {
        /*PlayerInfo info = player != null && Minecraft.getInstance().getConnection() != null ? Minecraft.getInstance().getConnection().getPlayerInfo(player.getUUID()) : null;
        GameType gameType = info != null ? info.getGameMode() : null;
        *///?}
        return gameType != null ? gameType.isSurvival() : original;
    }

    //? if >=1.21.4 {
    @ModifyExpressionValue(method = {"extractPlayerHealth", "extractAirBubbles", "extractFood"}, at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/Hud;tickCount:I", opcode = Opcodes.GETFIELD))
    //?} else {
    /*@ModifyExpressionValue(method = {"extractPlayerHealth", "extractFood"}, at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/Hud;tickCount:I", opcode = Opcodes.GETFIELD))
    *///?}
    private int playback$recordedGuiTicks(int original) {
        int recorded = RecordedPlayerState.guiTicks(Playback.spectated());
        return recorded >= 0 ? recorded : original;
    }

    @WrapMethod(method = "extractRenderState")
    private void playback$captureHud(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, Operation<Void> original) {
        if (!ScreenReplay.hidesHud()) {
            ScreenCapture.captureHud(() -> original.call(graphics, deltaTracker));
        } else {
            //? if >=26.3 {
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.gameRenderer.gameRenderState().guiRenderState.isHudHidden = minecraft.gui.hud.isHidden();
            //?}
        }
        ScreenReplay.render(graphics, deltaTracker.getGameTimeDeltaPartialTick(true));
        ContainerScreenAction.render(graphics, deltaTracker.getGameTimeDeltaPartialTick(true));
    }

    @Inject(method = "extractChat", at = @At("HEAD"), cancellable = true)
    private void playback$hideChat(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (ScreenReplay.hidesChat()) {
            ci.cancel();
        }
    }

    //? if >=26.1 {
    @WrapMethod(method = "extractSpyglassOverlay")
    //?} else
    //@WrapMethod(method = "renderSpyglassOverlay")
    private void playback$captureSpyglass(GuiGraphicsExtractor graphics, float scale, Operation<Void> original) {
        if (ScreenCapture.active(graphics)) {
            ScreenCapture.spyglass(scale, () -> original.call(graphics, scale));
        } else {
            original.call(graphics, scale);
        }
    }

    //? if <1.21.2 {
    /*@WrapMethod(method = "renderCrosshair")
    private void playback$captureCrosshairBlend(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, Operation<Void> original) {
        ScreenCapture.withBlitKind(1, () -> original.call(graphics, deltaTracker));
    }

    @WrapMethod(method = "renderVignette")
    private void playback$captureVignetteBlend(GuiGraphicsExtractor graphics, Entity camera, Operation<Void> original) {
        ScreenCapture.withBlitKind(2, () -> original.call(graphics, camera));
    }
    *///?}

    @WrapMethod(method = "extractChat")
    private void playback$captureChat(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, Operation<Void> original) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean capture = ScreenCapture.recording() && minecraft.gui.screen() instanceof ChatScreen;
        boolean started = capture && !ScreenCapture.active();
        if (capture) {
            ScreenCapture.beginLayer(true);
        }
        try {
            original.call(graphics, deltaTracker);
        } finally {
            if (started) {
                ScreenCapture.endLayer();
            }
        }
    }

    @ModifyExpressionValue(method = "tick()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getInventory()Lnet/minecraft/world/entity/player/Inventory;"))
    private Inventory playback$getInventory(Inventory original) {
        AbstractClientPlayer player = Playback.spectated();
        return player != null ? player.getInventory() : original;
    }

}
