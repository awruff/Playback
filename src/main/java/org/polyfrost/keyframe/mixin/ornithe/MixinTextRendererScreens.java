package org.polyfrost.keyframe.mixin.ornithe;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.render.TextRenderer;
import org.polyfrost.keyframe.ui.ScreenCapture;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(TextRenderer.class)
public abstract class MixinTextRendererScreens {

    @WrapMethod(method = "draw(Ljava/lang/String;FFIZ)I")
    private int keyframe$text(String text, float x, float y, int color, boolean shadow, Operation<Integer> original) {
        if (ScreenCapture.active() && text != null) {
            ScreenCapture.text(text, x, y, color, shadow);
        }
        return ScreenCapture.opaqueValue(() -> original.call(text, x, y, color, shadow));
    }

    @WrapMethod(method = "drawLayerAligned")
    private int keyframe$alignedText(String text, int x, int y, int width, int color, boolean shadow, Operation<Integer> original) {
        if (ScreenCapture.active() && text != null) {
            ScreenCapture.text(text, x, y, color, shadow);
        }
        return ScreenCapture.opaqueValue(() -> original.call(text, x, y, width, color, shadow));
    }

}
