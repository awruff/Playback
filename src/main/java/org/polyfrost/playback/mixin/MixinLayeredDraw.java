package org.polyfrost.playback.mixin;

//? if <1.21.6 {
/*import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.LayeredDraw;
import org.polyfrost.playback.ui.ScreenCapture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LayeredDraw.class)
public abstract class MixinLayeredDraw {

    @WrapOperation(method = "renderInner", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/LayeredDraw$Layer;render(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V"))
    private void playback$groupLayer(LayeredDraw.Layer layer, GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, Operation<Void> original) {
        ScreenCapture.captureHud(() -> original.call(layer, graphics, deltaTracker));
    }

}
*///?}
