package org.polyfrost.keyframe.mixin.ornithe;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.gui.GuiElement;
import org.polyfrost.keyframe.ui.ScreenCapture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(GuiElement.class)
public abstract class MixinGuiElementScreens {

    @Shadow
    protected float drawOffset;

    @WrapMethod(method = "fill(IIIII)V")
    private static void keyframe$fill(int x0, int y0, int x1, int y1, int color, Operation<Void> original) {
        if (ScreenCapture.active()) {
            ScreenCapture.fill(0.0f, x0, y0, x1, y1, color, color);
        }
        ScreenCapture.opaque(() -> original.call(x0, y0, x1, y1, color));
    }

    @WrapMethod(method = "fillGradient(IIIIII)V")
    private void keyframe$fillGradient(int x0, int y0, int x1, int y1, int color1, int color2, Operation<Void> original) {
        if (ScreenCapture.active()) {
            ScreenCapture.fill(this.drawOffset, x0, y0, x1, y1, color1, color2);
        }
        ScreenCapture.opaque(() -> original.call(x0, y0, x1, y1, color1, color2));
    }

    @WrapMethod(method = "drawTexture(IIIIII)V")
    private void keyframe$blit(int x, int y, int u, int v, int width, int height, Operation<Void> original) {
        if (ScreenCapture.active()) {
            ScreenCapture.blit(this.drawOffset, x, x + width, y, y + height, u / 256.0f, (u + width) / 256.0f, v / 256.0f, (v + height) / 256.0f);
        }
        ScreenCapture.opaque(() -> original.call(x, y, u, v, width, height));
    }

    @WrapMethod(method = "drawTexture(IIFFIIFF)V")
    private static void keyframe$blitSized(int x, int y, float u, float v, int width, int height, float textureWidth, float textureHeight, Operation<Void> original) {
        if (ScreenCapture.active()) {
            ScreenCapture.blit(0.0f, x, x + width, y, y + height, u / textureWidth, (u + width) / textureWidth, v / textureHeight, (v + height) / textureHeight);
        }
        ScreenCapture.opaque(() -> original.call(x, y, u, v, width, height, textureWidth, textureHeight));
    }

    @WrapMethod(method = "drawTexture(IIFFIIIIFF)V")
    private static void keyframe$blitScaled(int x, int y, float u, float v, int regionWidth, int regionHeight, int width, int height, float textureWidth, float textureHeight, Operation<Void> original) {
        if (ScreenCapture.active()) {
            ScreenCapture.blit(0.0f, x, x + width, y, y + height, u / textureWidth, (u + regionWidth) / textureWidth, v / textureHeight, (v + regionHeight) / textureHeight);
        }
        ScreenCapture.opaque(() -> original.call(x, y, u, v, regionWidth, regionHeight, width, height, textureWidth, textureHeight));
    }

}
