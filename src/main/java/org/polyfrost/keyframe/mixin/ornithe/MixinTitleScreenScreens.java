package org.polyfrost.keyframe.mixin.ornithe;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.gui.screen.menu.TitleScreen;
import org.polyfrost.keyframe.ui.ScreenCapture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(TitleScreen.class)
public abstract class MixinTitleScreenScreens {

    @Shadow
    private int time;

    @WrapMethod(method = "drawBackground")
    private void keyframe$capturePanorama(int mouseX, int mouseY, float delta, Operation<Void> original) {
        if (ScreenCapture.active()) {
            ScreenCapture.panorama(this.time + delta);
        }
        ScreenCapture.opaque(() -> original.call(mouseX, mouseY, delta));
    }

}
