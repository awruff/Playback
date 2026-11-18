package org.polyfrost.keyframe.mixin.ornithe;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.moulberry.flashback.Flashback;
import net.minecraft.client.gui.screen.menu.GameMenuScreen;
import org.polyfrost.keyframe.shadow.ShadowReplay;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(GameMenuScreen.class)
public abstract class MixinGameMenuScreenShadowReplay {

    @WrapMethod(method = "init")
    private void keyframe$hideShadowReplay(Operation<Void> original) {
        boolean hidden = ShadowReplay.isShadow(Flashback.RECORDER);
        if (hidden) {
            Flashback.RECORDER = null;
        }
        try {
            original.call();
        } finally {
            if (hidden && Flashback.RECORDER == null) {
                ShadowReplay.restore();
            }
        }
    }

}
