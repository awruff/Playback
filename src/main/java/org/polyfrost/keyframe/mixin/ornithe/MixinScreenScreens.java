package org.polyfrost.keyframe.mixin.ornithe;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.gui.screen.Screen;
import org.polyfrost.keyframe.ui.ScreenCapture;
import org.spongepowered.asm.mixin.Mixin;

import java.util.List;

@Mixin(Screen.class)
public abstract class MixinScreenScreens {

    @WrapMethod(method = "renderTooltip(Ljava/util/List;II)V")
    private void keyframe$captureTooltipGroup(List<String> lines, int x, int y, Operation<Void> original) {
        ScreenCapture.group(ScreenCapture.active(), ScreenCapture.GROUP_TOOLTIP, () -> original.call(lines, x, y));
    }

}
