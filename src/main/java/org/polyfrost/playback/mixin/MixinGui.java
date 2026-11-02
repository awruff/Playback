package org.polyfrost.playback.mixin;

//? if >=26.2 {
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.polyfrost.playback.ui.ContainerScreenAction;
import org.polyfrost.playback.ui.ScreenCapture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Gui.class)
public abstract class MixinGui {

    @WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;extractRenderStateWithTooltipAndSubtitles(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"))
    private void playback$captureScreen(Screen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tickDelta, Operation<Void> original) {
        boolean capture = !ContainerScreenAction.recordMouse(screen) && ScreenCapture.recording();
        if (capture) {
            ScreenCapture.beginLayer(false);
        }
        original.call(screen, graphics, mouseX, mouseY, tickDelta);
        if (capture) {
            ScreenCapture.endScreen(graphics, Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false));
        }
    }

}
//?}
