package org.polyfrost.playback.mixin;

//? if >=1.21.11 {
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.TextAlignment;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import org.polyfrost.playback.ui.ScreenCapture;
import org.joml.Matrix3x2fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.gui.GuiGraphicsExtractor$RenderingTextCollector")
public abstract class MixinRenderingTextCollector {

    @Inject(method = "accept(Lnet/minecraft/client/gui/TextAlignment;IILnet/minecraft/client/gui/ActiveTextCollector$Parameters;Lnet/minecraft/util/FormattedCharSequence;)V", at = @At("HEAD"))
    private void playback$text(TextAlignment alignment, int anchorX, int y, ActiveTextCollector.Parameters parameters, FormattedCharSequence text, CallbackInfo ci) {
        if (ScreenCapture.active() && ARGB.as8BitChannel(parameters.opacity()) != 0) {
            Matrix3x2fc pose = parameters.pose();
            float[] flat = {pose.m00(), pose.m01(), pose.m10(), pose.m11(), pose.m20(), pose.m21(), 0.0f};
            ScreenCapture.text(flat, text, alignment.calculateLeft(anchorX, Minecraft.getInstance().font, text), y, ARGB.white(parameters.opacity()), true);
        }
    }

}
//?}
