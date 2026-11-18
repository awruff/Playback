package org.polyfrost.keyframe.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import org.polyfrost.keyframe.ui.ScreenCapture;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(AbstractWidget.class)
public abstract class MixinAbstractWidget {

    @WrapMethod(method = "extractRenderState")
    private void keyframe$captureWidgetGroup(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, Operation<Void> original) {
        AbstractWidget widget = (AbstractWidget) (Object) this;
        ScreenCapture.group(widget.visible && ScreenCapture.active(graphics), graphics, widget.getX(), widget.getY(),
            widget.getX() + widget.getWidth(), widget.getY() + widget.getHeight(), () -> original.call(graphics, mouseX, mouseY, partialTick));
    }

}
