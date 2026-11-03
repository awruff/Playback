package org.polyfrost.playback.mixin;

//? if >=26.2 {
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.gui.screens.Screen;
import org.polyfrost.playback.ui.ScreenCapture;
import org.polyfrost.playback.ui.ScreenReplay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if >=26.3
import com.llamalad7.mixinextras.sugar.Local;

@Mixin(Gui.class)
public abstract class MixinGui {

    @WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;extractRenderStateWithTooltipAndSubtitles(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"))
    private void playback$captureScreen(Screen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tickDelta, Operation<Void> original) {
        ScreenCapture.captureScreen(screen, graphics, () -> original.call(screen, graphics, mouseX, mouseY, tickDelta));
    }

    //? if >=26.3 {
    @Inject(method = "extractRenderState", at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/Gui;overlay:Lnet/minecraft/client/gui/screens/Overlay;", ordinal = 0))
    private void playback$extractRecordedScreenWithoutHud(DeltaTracker deltaTracker, boolean shouldRenderLevel, boolean resourcesLoaded, CallbackInfo ci, @Local GuiGraphicsExtractor graphics) {
        if (!shouldRenderLevel) {
            ScreenReplay.render(graphics, deltaTracker.getGameTimeDeltaPartialTick(true));
        }
    }
    //?}

    @WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Hud;extractSavingIndicator(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V"))
    private void playback$captureSavingIndicator(Hud hud, GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, Operation<Void> original) {
        if (!ScreenReplay.hidesHud()) {
            ScreenCapture.captureHud(() -> original.call(hud, graphics, deltaTracker));
        }
    }

    @WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/toasts/ToastManager;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V"))
    private void playback$captureToasts(ToastManager toasts, GuiGraphicsExtractor graphics, Operation<Void> original) {
        if (!ScreenReplay.hidesHud()) {
            ScreenCapture.captureHud(() -> original.call(toasts, graphics));
        }
    }

    @WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Hud;extractDebugOverlay(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V"))
    private void playback$captureDebugOverlay(Hud hud, GuiGraphicsExtractor graphics, Operation<Void> original) {
        if (!ScreenReplay.hidesHud()) {
            ScreenCapture.captureHud(() -> original.call(hud, graphics));
        }
    }

    @WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Hud;extractDeferredSubtitles()V"))
    private void playback$captureSubtitles(Hud hud, Operation<Void> original) {
        if (!ScreenReplay.hidesHud()) {
            ScreenCapture.captureHud(() -> original.call(hud));
        }
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void playback$endFrame(CallbackInfo ci) {
        if (ScreenCapture.recording()) {
            ScreenCapture.endFrame();
        }
    }

}
//?}
