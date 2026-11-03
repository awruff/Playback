package org.polyfrost.playback.mixin;

//? if >=1.21.6 {
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.polyfrost.playback.ui.ScreenCapture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.fabricmc.fabric.impl.client.rendering.hud.HudElementRegistryImpl$RootLayer", remap = false)
public abstract class MixinHudRootLayer {

    //? if >=26.1 {
    @WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/fabricmc/fabric/api/client/rendering/v1/hud/HudElement;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V"), require = 0)
    //?} else
    //@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/fabricmc/fabric/api/client/rendering/v1/hud/HudElement;render"), require = 0)
    private void playback$groupLayerWithDelta(HudElement element, GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, Operation<Void> original) {
        ScreenCapture.captureHud(() -> original.call(element, graphics, deltaTracker));
    }

    //? if >=26.3 {
    @WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/fabricmc/fabric/api/client/rendering/v1/hud/HudElement;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V"), require = 0)
    private void playback$groupLayer(HudElement element, GuiGraphicsExtractor graphics, Operation<Void> original) {
        ScreenCapture.captureHud(() -> original.call(element, graphics));
    }
    //?}

}
//?}
