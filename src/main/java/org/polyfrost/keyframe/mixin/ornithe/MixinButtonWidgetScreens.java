package org.polyfrost.keyframe.mixin.ornithe;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.widget.ButtonWidget;
import org.polyfrost.keyframe.ui.ScreenCapture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ButtonWidget.class)
public abstract class MixinButtonWidgetScreens {

    @Shadow
    protected int width;

    @Shadow
    protected int height;

    @Shadow
    public int x;

    @Shadow
    public int y;

    @Shadow
    public boolean visible;

    @WrapMethod(method = "render")
    private void keyframe$captureWidgetGroup(Minecraft minecraft, int mouseX, int mouseY, Operation<Void> original) {
        ScreenCapture.group(this.visible && ScreenCapture.active(), this.x, this.y, this.x + this.width, this.y + this.height,
            () -> original.call(minecraft, mouseX, mouseY));
    }

}
