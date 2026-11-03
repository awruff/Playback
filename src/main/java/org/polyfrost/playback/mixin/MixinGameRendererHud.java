package org.polyfrost.playback.mixin;

//? if <26.2 {
/*import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.GameRenderer;
import org.polyfrost.playback.ui.ScreenCapture;
import org.polyfrost.playback.ui.ScreenReplay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if >=1.21.2 {
import net.minecraft.client.gui.components.toasts.ToastManager;
//?} else
//import net.minecraft.client.gui.components.toasts.ToastComponent;

@Mixin(GameRenderer.class)
public abstract class MixinGameRendererHud {

    //? if >=26.1 {
    @WrapOperation(method = "extractGui", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;extractSavingIndicator(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V"))
    //?} else
    //@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;renderSavingIndicator(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V"))
    private void playback$captureSavingIndicator(Gui gui, GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, Operation<Void> original) {
        if (!ScreenReplay.hidesHud()) {
            ScreenCapture.captureHud(() -> original.call(gui, graphics, deltaTracker));
        }
    }

    //? if >=26.1 {
    @WrapOperation(method = "extractGui", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/toasts/ToastManager;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V"))
    private void playback$captureToasts(ToastManager toasts, GuiGraphicsExtractor graphics, Operation<Void> original) {
    //?} elif >=1.21.2 {
    /^@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/toasts/ToastManager;render(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V"))
    private void playback$captureToasts(ToastManager toasts, GuiGraphicsExtractor graphics, Operation<Void> original) {
    ^///?} else {
    /^@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/toasts/ToastComponent;render(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V"))
    private void playback$captureToasts(ToastComponent toasts, GuiGraphicsExtractor graphics, Operation<Void> original) {
    ^///?}
        if (!ScreenReplay.hidesHud()) {
            ScreenCapture.captureHud(() -> original.call(toasts, graphics));
        }
    }

    //? if >=1.21.10 {
    //? if >=26.1 {
    @WrapOperation(method = "extractGui", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;extractDebugOverlay(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V"))
    //?} else
    //@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;renderDebugOverlay(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V"))
    private void playback$captureDebugOverlay(Gui gui, GuiGraphicsExtractor graphics, Operation<Void> original) {
        if (!ScreenReplay.hidesHud()) {
            ScreenCapture.captureHud(() -> original.call(gui, graphics));
        }
    }

    //? if >=26.1 {
    @WrapOperation(method = "extractGui", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;extractDeferredSubtitles()V"))
    //?} else
    //@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;renderDeferredSubtitles()V"))
    private void playback$captureSubtitles(Gui gui, Operation<Void> original) {
        if (!ScreenReplay.hidesHud()) {
            ScreenCapture.captureHud(() -> original.call(gui));
        }
    }
    //?}

    //? if <1.21.2 {
    /^@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;renderConfusionOverlay(Lnet/minecraft/client/gui/GuiGraphicsExtractor;F)V"))
    private void playback$captureConfusion(GameRenderer renderer, GuiGraphicsExtractor graphics, float strength, Operation<Void> original) {
        if (!ScreenReplay.hidesHud()) {
            ScreenCapture.captureHud(() -> ScreenCapture.withBlitKind(3, () -> original.call(renderer, graphics, strength)));
        }
    }
    ^///?}

    //? if >=26.1 {
    @Inject(method = "extractGui", at = @At("TAIL"))
    //?} else
    //@Inject(method = "render", at = @At("TAIL"))
    private void playback$endFrame(CallbackInfo ci) {
        if (ScreenCapture.recording()) {
            ScreenCapture.endFrame();
        }
    }

}
*///?}
