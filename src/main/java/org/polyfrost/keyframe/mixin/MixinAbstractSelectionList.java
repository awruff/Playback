package org.polyfrost.keyframe.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSelectionList;
import org.polyfrost.keyframe.ui.ScreenCapture;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(AbstractSelectionList.class)
public abstract class MixinAbstractSelectionList {

    //? if >=1.21.10 {
    @WrapMethod(method = "extractItem")
    private void keyframe$captureEntryGroup(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, AbstractSelectionList.Entry<?> entry, Operation<Void> original) {
        ScreenCapture.group(ScreenCapture.active(graphics), graphics, entry.getX(), entry.getY(), entry.getX() + entry.getWidth(), entry.getY() + entry.getHeight(),
            () -> original.call(graphics, mouseX, mouseY, partialTick, entry));
    }
    //?} else {
    /*@WrapMethod(method = "extractItem")
    private void keyframe$captureEntryGroup(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, int index, int left, int top, int width, int height, Operation<Void> original) {
        ScreenCapture.group(ScreenCapture.active(graphics), graphics, left, top, left + width, top + height,
            () -> original.call(graphics, mouseX, mouseY, partialTick, index, left, top, width, height));
    }
    *///?}

}
