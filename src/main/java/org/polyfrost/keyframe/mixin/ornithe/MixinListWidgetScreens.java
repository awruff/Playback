package org.polyfrost.keyframe.mixin.ornithe;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.widget.ListWidget;
import org.polyfrost.keyframe.ui.ScreenCapture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ListWidget.class)
public abstract class MixinListWidgetScreens {

    @WrapOperation(method = "renderList", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/widget/ListWidget;renderEntry(IIIIII)V"))
    private void keyframe$captureEntryGroup(ListWidget list, int index, int x, int y, int height, int mouseX, int mouseY, Operation<Void> original) {
        ScreenCapture.group(ScreenCapture.active(), x, y, x + list.getRowWidth(), y + height,
            () -> original.call(list, index, x, y, height, mouseX, mouseY));
    }

}
