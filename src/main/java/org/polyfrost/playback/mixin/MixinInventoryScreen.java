package org.polyfrost.playback.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.LivingEntity;
import org.polyfrost.playback.ui.ContainerScreenAction;
import org.polyfrost.playback.Playback;
import org.polyfrost.playback.ui.GuiCompat;
import org.polyfrost.playback.ui.ScreenCapture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InventoryScreen.class)
public abstract class MixinInventoryScreen {

    @Inject(method = "extractEntityInInventoryFollowsMouse", at = @At("HEAD"))
    private static void playback$entity(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int size, float offsetY, float mouseX, float mouseY, LivingEntity entity, CallbackInfo ci) {
        if (ScreenCapture.active(graphics)) {
            ScreenCapture.entity(GuiCompat.pose(graphics), entity.getId(), x0, y0, x1, y1, size, offsetY, mouseX, mouseY);
        }
    }

    @ModifyArg(method = "extractBackground", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/inventory/InventoryScreen;extractEntityInInventoryFollowsMouse(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIIIIFFFLnet/minecraft/world/entity/LivingEntity;)V"), index = 9)
    private LivingEntity playback$replayedEntity(LivingEntity original) {
        LivingEntity spectated = Playback.spectated();
        return ContainerScreenAction.isReplayScreen(this) && spectated != null ? spectated : original;
    }

}
