package org.polyfrost.keyframe.mixin.ornithe;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.entity.living.player.ClientPlayerEntity;
import net.minecraft.client.gui.screen.game.inventory.SurvivalInventoryScreen;
import net.minecraft.entity.living.LivingEntity;
import org.polyfrost.keyframe.Keyframe;
import org.polyfrost.keyframe.ui.ContainerScreenAction;
import org.polyfrost.keyframe.ui.ScreenCapture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(SurvivalInventoryScreen.class)
public abstract class MixinSurvivalInventoryScreenScreens {

    @WrapMethod(method = "renderEntity")
    private static void keyframe$entity(int x, int y, int size, float mouseX, float mouseY, LivingEntity entity, Operation<Void> original) {
        if (ScreenCapture.active()) {
            ScreenCapture.entity(entity.getNetworkId(), x, y, size, mouseX, mouseY);
        }
        ScreenCapture.opaque(() -> original.call(x, y, size, mouseX, mouseY, entity));
    }

    @ModifyArg(method = "renderMenuBackground", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/game/inventory/SurvivalInventoryScreen;renderEntity(IIIFFLnet/minecraft/entity/living/LivingEntity;)V"), index = 5)
    private LivingEntity keyframe$replayedEntity(LivingEntity original) {
        ClientPlayerEntity spectated = Keyframe.spectated();
        return ContainerScreenAction.isReplayScreen(this) && spectated != null ? spectated : original;
    }

    @ModifyExpressionValue(method = "init", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/ClientPlayerInteractionManager;hasCreativeInventory()Z"))
    private boolean keyframe$keepReplayScreen(boolean original) {
        return original && !ContainerScreenAction.isReplayScreen(this);
    }

}
