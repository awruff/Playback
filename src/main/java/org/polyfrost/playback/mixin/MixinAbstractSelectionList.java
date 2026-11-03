package org.polyfrost.playback.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSelectionList;
import org.polyfrost.playback.ui.GuiCompat;
import org.polyfrost.playback.ui.ScreenCapture;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(AbstractSelectionList.class)
public abstract class MixinAbstractSelectionList {

    //? if >=1.21.10 {
    @WrapMethod(method = "extractItem")
    private void playback$captureEntryGroup(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, AbstractSelectionList.Entry<?> entry, Operation<Void> original) {
        boolean group = ScreenCapture.active(graphics);
        if (group) {
            ScreenCapture.beginGroup(ScreenCapture.GROUP_PLAIN, GuiCompat.pose(graphics), entry.getX(), entry.getY(),
                entry.getX() + entry.getWidth(), entry.getY() + entry.getHeight());
        }
        try {
            original.call(graphics, mouseX, mouseY, partialTick, entry);
        } finally {
            if (group) {
                ScreenCapture.endGroup();
            }
        }
    }
    //?} else {
    /*@WrapMethod(method = "extractItem")
    private void playback$captureEntryGroup(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, int index, int left, int top, int width, int height, Operation<Void> original) {
        boolean group = ScreenCapture.active(graphics);
        if (group) {
            ScreenCapture.beginGroup(ScreenCapture.GROUP_PLAIN, GuiCompat.pose(graphics), left, top, left + width, top + height);
        }
        try {
            original.call(graphics, mouseX, mouseY, partialTick, index, left, top, width, height);
        } finally {
            if (group) {
                ScreenCapture.endGroup();
            }
        }
    }
    *///?}

}
