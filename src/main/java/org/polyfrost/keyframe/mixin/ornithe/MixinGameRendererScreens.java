package org.polyfrost.keyframe.mixin.ornithe;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.GameRenderer;
import org.polyfrost.keyframe.ui.ScreenCapture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class MixinGameRendererScreens {

    @Inject(method = "render(FJ)V", at = @At("HEAD"))
    private void keyframe$partial(float partial, long nanoTime, CallbackInfo ci) {
        ScreenCapture.partial = partial;
    }

    @WrapOperation(method = "render(FJ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/Screen;render(IIF)V"))
    private void keyframe$captureScreen(Screen screen, int mouseX, int mouseY, float partial, Operation<Void> original) {
        ScreenCapture.captureScreen(screen, () -> original.call(screen, mouseX, mouseY, partial));
    }

}
