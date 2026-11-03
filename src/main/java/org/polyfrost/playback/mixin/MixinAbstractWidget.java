package org.polyfrost.playback.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import org.polyfrost.playback.ui.GuiCompat;
import org.polyfrost.playback.ui.ScreenCapture;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(AbstractWidget.class)
public abstract class MixinAbstractWidget {

    @WrapMethod(method = "extractRenderState")
    private void playback$captureWidgetGroup(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, Operation<Void> original) {
        AbstractWidget widget = (AbstractWidget) (Object) this;
        boolean group = widget.visible && ScreenCapture.active(graphics);
        if (group) {
            ScreenCapture.beginGroup(ScreenCapture.GROUP_PLAIN, GuiCompat.pose(graphics), widget.getX(), widget.getY(),
                widget.getX() + widget.getWidth(), widget.getY() + widget.getHeight());
        }
        try {
            original.call(graphics, mouseX, mouseY, partialTick);
        } finally {
            if (group) {
                ScreenCapture.endGroup();
            }
        }
    }

}
