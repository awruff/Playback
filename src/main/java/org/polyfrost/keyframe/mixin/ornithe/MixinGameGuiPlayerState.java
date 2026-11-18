package org.polyfrost.keyframe.mixin.ornithe;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.entity.living.player.ClientPlayerEntity;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.client.gui.GameGui;
import net.minecraft.entity.living.effect.StatusEffect;
import net.minecraft.entity.living.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import org.objectweb.asm.Opcodes;
import org.polyfrost.keyframe.player.RecordedPlayerState;
import org.polyfrost.keyframe.player.SpectatedPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GameGui.class)
public abstract class MixinGameGuiPlayerState {

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/living/player/PlayerInventory;getArmor(I)Lnet/minecraft/item/ItemStack;"))
    private ItemStack keyframe$getArmor(PlayerInventory instance, int slot, Operation<ItemStack> original) {
        ItemStack item = original.call(instance, slot);
        ClientPlayerEntity player = SpectatedPlayer.get();
        return player != null ? player.inventory.getArmor(slot) : item;
    }

    @ModifyExpressionValue(method = "render", at = @At(value = "FIELD", target = "Lnet/minecraft/client/entity/living/player/LocalClientPlayerEntity;lastPortalTime:F"))
    private float keyframe$oPortalIntensity(float original) {
        return SpectatedPlayer.get() != null ? SpectatedPlayer.oPortalIntensity() : original;
    }

    @ModifyExpressionValue(method = "render", at = @At(value = "FIELD", target = "Lnet/minecraft/client/entity/living/player/LocalClientPlayerEntity;portalTime:F"))
    private float keyframe$portalIntensity(float original) {
        return SpectatedPlayer.get() != null ? SpectatedPlayer.portalIntensity() : original;
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/entity/living/player/LocalClientPlayerEntity;hasStatusEffect(Lnet/minecraft/entity/living/effect/StatusEffect;)Z"))
    private boolean keyframe$hasStatusEffect(LocalClientPlayerEntity instance, StatusEffect effect, Operation<Boolean> original) {
        boolean has = original.call(instance, effect);
        ClientPlayerEntity player = SpectatedPlayer.get();
        return player != null ? player.hasStatusEffect(effect) : has;
    }

    @ModifyExpressionValue(method = "renderStatusBars", at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/GameGui;ticks:I", opcode = Opcodes.GETFIELD))
    private int keyframe$recordedGuiTicks(int original) {
        int recorded = RecordedPlayerState.guiTicks(SpectatedPlayer.get());
        return recorded >= 0 ? recorded : original;
    }

    @ModifyExpressionValue(method = "tick", at = @At(value = "FIELD", target = "Lnet/minecraft/client/entity/living/player/LocalClientPlayerEntity;inventory:Lnet/minecraft/entity/living/player/PlayerInventory;"))
    private PlayerInventory keyframe$getInventory(PlayerInventory original) {
        ClientPlayerEntity player = SpectatedPlayer.get();
        return player != null ? player.inventory : original;
    }

}
