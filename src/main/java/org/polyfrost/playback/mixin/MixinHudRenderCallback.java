package org.polyfrost.playback.mixin;

//? if <1.21.6 {
/*import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.polyfrost.playback.ui.ScreenCapture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = HudRenderCallback.class, remap = false)
public interface MixinHudRenderCallback {

    @WrapOperation(method = "lambda$static$0", at = @At(value = "INVOKE", target = "Lnet/fabricmc/fabric/api/client/rendering/v1/HudRenderCallback;onHudRender"), require = 0)
    private static void playback$groupListener(HudRenderCallback listener, GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, Operation<Void> original) {
        ScreenCapture.captureHud(() -> original.call(listener, graphics, deltaTracker));
    }

}
*///?}
