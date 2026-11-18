package org.polyfrost.keyframe.mixin.ornithe;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ToastGui;
import org.polyfrost.keyframe.ui.ScreenCapture;
import org.polyfrost.keyframe.ui.ScreenReplay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Minecraft.class)
public abstract class MixinMinecraftScreens {

    @WrapOperation(method = "runGame", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/ToastGui;render()V"))
    private void keyframe$captureToastsAndEndFrame(ToastGui toasts, Operation<Void> original) {
        if (!ScreenReplay.hidesHud()) {
            ScreenCapture.captureHud(() -> original.call(toasts));
        }
        if (ScreenCapture.recording()) {
            ScreenCapture.endFrame();
        }
    }

}
