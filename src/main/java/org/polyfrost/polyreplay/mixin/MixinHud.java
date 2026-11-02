package org.polyfrost.polyreplay.mixin;

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
import org.polyfrost.polyreplay.ui.ContainerScreenAction;
import org.polyfrost.polyreplay.FirstPersonEffects;
import org.polyfrost.polyreplay.player.RecordedPlayerState;
import org.polyfrost.polyreplay.ui.ScreenCapture;
import org.polyfrost.polyreplay.ui.ScreenReplay;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import net.minecraft.client.gui.screens.ChatScreen;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;

@Mixin(Hud.class)
public abstract class MixinHud {

    @ModifyExpressionValue(method = "extractCameraOverlays", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isScoping()Z"))
    private boolean polyreplay$isScoping(boolean original) {
        AbstractClientPlayer player = FirstPersonEffects.spectated();
        return player != null ? player.isScoping() : original;
    }

    //? if >=1.21.4 {
    @WrapOperation(method = "extractCameraOverlays", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getItemBySlot(Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack polyreplay$getItemBySlot(LocalPlayer instance, EquipmentSlot slot, Operation<ItemStack> original) {
        ItemStack item = original.call(instance, slot);
        AbstractClientPlayer player = FirstPersonEffects.spectated();
        return player != null ? player.getItemBySlot(slot) : item;
    }
    //?} else {
    /*@ModifyExpressionValue(method = "extractCameraOverlays", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getInventory()Lnet/minecraft/world/entity/player/Inventory;"))
    private Inventory polyreplay$getArmorInventory(Inventory original) {
        AbstractClientPlayer player = FirstPersonEffects.spectated();
        return player != null ? player.getInventory() : original;
    }
    *///?}

    @ModifyExpressionValue(method = "extractCameraOverlays", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getTicksFrozen()I"))
    private int polyreplay$getTicksFrozen(int original) {
        AbstractClientPlayer player = FirstPersonEffects.spectated();
        return player != null ? player.getTicksFrozen() : original;
    }

    @ModifyExpressionValue(method = "extractCameraOverlays", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getPercentFrozen()F"))
    private float polyreplay$getPercentFrozen(float original) {
        AbstractClientPlayer player = FirstPersonEffects.spectated();
        return player != null ? player.getPercentFrozen() : original;
    }

    //? if >=1.21.8 {
    @ModifyExpressionValue(method = "extractCameraOverlays", at = @At(value = "FIELD", target = "Lnet/minecraft/client/player/LocalPlayer;oPortalEffectIntensity:F"))
    private float polyreplay$oPortalEffectIntensity(float original) {
        return FirstPersonEffects.spectated() != null ? FirstPersonEffects.oPortalIntensity() : original;
    }

    @ModifyExpressionValue(method = "extractCameraOverlays", at = @At(value = "FIELD", target = "Lnet/minecraft/client/player/LocalPlayer;portalEffectIntensity:F"))
    private float polyreplay$portalEffectIntensity(float original) {
        return FirstPersonEffects.spectated() != null ? FirstPersonEffects.portalIntensity() : original;
    }

    @WrapOperation(method = "extractCameraOverlays", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getEffectBlendFactor(Lnet/minecraft/core/Holder;F)F"))
    private float polyreplay$getEffectBlendFactor(LocalPlayer instance, Holder<MobEffect> effect, float partialTick, Operation<Float> original) {
        float blend = original.call(instance, effect, partialTick);
        AbstractClientPlayer player = FirstPersonEffects.spectated();
        return player != null ? player.getEffectBlendFactor(effect, partialTick) : blend;
    }
    //?} else {
    /*@ModifyExpressionValue(method = "extractCameraOverlays", at = @At(value = "FIELD", target = "Lnet/minecraft/client/player/LocalPlayer;oSpinningEffectIntensity:F"))
    private float polyreplay$oSpinningEffectIntensity(float original) {
        return FirstPersonEffects.spectated() != null ? FirstPersonEffects.oPortalIntensity() : original;
    }

    @ModifyExpressionValue(method = "extractCameraOverlays", at = @At(value = "FIELD", target = "Lnet/minecraft/client/player/LocalPlayer;spinningEffectIntensity:F"))
    private float polyreplay$spinningEffectIntensity(float original) {
        return FirstPersonEffects.spectated() != null ? FirstPersonEffects.portalIntensity() : original;
    }

    @WrapOperation(method = "extractCameraOverlays", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;hasEffect(Lnet/minecraft/core/Holder;)Z"))
    private boolean polyreplay$hasEffect(LocalPlayer instance, Holder<MobEffect> effect, Operation<Boolean> original) {
        boolean has = original.call(instance, effect);
        AbstractClientPlayer player = FirstPersonEffects.spectated();
        return player != null ? player.hasEffect(effect) : has;
    }
    *///?}

    @ModifyExpressionValue(method = "extractEffects", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getActiveEffects()Ljava/util/Collection;"))
    private Collection<MobEffectInstance> polyreplay$getActiveEffects(Collection<MobEffectInstance> original) {
        AbstractClientPlayer player = FirstPersonEffects.spectated();
        return player != null ? player.getActiveEffects() : original;
    }

    @ModifyExpressionValue(method = "extractItemHotbar", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getAttackStrengthScale(F)F"))
    private float polyreplay$hotbarAttackStrength(float original) {
        AbstractClientPlayer player = FirstPersonEffects.spectated();
        return player != null ? player.getAttackStrengthScale(0.0f) : original;
    }

    @ModifyExpressionValue(method = "extractSelectedItemName", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;canHurtPlayer()Z"))
    private boolean polyreplay$spectatedIsSurvival(boolean original) {
        AbstractClientPlayer player = FirstPersonEffects.spectated();
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
    private int polyreplay$recordedGuiTicks(int original) {
        int recorded = RecordedPlayerState.guiTicks(FirstPersonEffects.spectated());
        return recorded >= 0 ? recorded : original;
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void polyreplay$extractRecordedScreen(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        ScreenReplay.render(graphics, deltaTracker.getGameTimeDeltaPartialTick(true));
        ContainerScreenAction.render(graphics, deltaTracker.getGameTimeDeltaPartialTick(true));
    }

    @Inject(method = "extractChat", at = @At("HEAD"), cancellable = true)
    private void polyreplay$hideChat(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (ScreenReplay.hidesChat()) {
            ci.cancel();
        }
    }

    @WrapMethod(method = "extractChat")
    private void polyreplay$captureChat(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, Operation<Void> original) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean capture = ScreenCapture.recording() && minecraft.gui.screen() instanceof ChatScreen;
        if (capture) {
            ScreenCapture.beginLayer(true);
        }
        original.call(graphics, deltaTracker);
        if (capture) {
            ScreenCapture.endLayer();
        }
    }

    @ModifyExpressionValue(method = "tick()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getInventory()Lnet/minecraft/world/entity/player/Inventory;"))
    private Inventory polyreplay$getInventory(Inventory original) {
        AbstractClientPlayer player = FirstPersonEffects.spectated();
        return player != null ? player.getInventory() : original;
    }

}
