package org.polyfrost.keyframe.mixin.ornithe;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.render.entity.ItemRenderer;
import net.minecraft.item.ItemStack;
import org.polyfrost.keyframe.ui.ScreenCapture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ItemRenderer.class)
public abstract class MixinItemRendererScreens {

    @Shadow
    public float zOffset;

    @WrapMethod(method = "renderGuiItemModel")
    private void keyframe$item(ItemStack stack, int x, int y, Operation<Void> original) {
        if (ScreenCapture.active() && stack != null && stack.getItem() != null) {
            ScreenCapture.item(this.zOffset, stack, x, y);
        }
        ScreenCapture.opaque(() -> original.call(stack, x, y));
    }

}
