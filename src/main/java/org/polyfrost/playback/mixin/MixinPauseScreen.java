package org.polyfrost.playback.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.moulberry.flashback.Flashback;
import net.minecraft.client.gui.screens.PauseScreen;
import org.polyfrost.playback.shadow.ShadowReplay;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(PauseScreen.class)
public abstract class MixinPauseScreen {

    @WrapMethod(method = "createPauseMenu")
    private void playback$hideShadowReplay(Operation<Void> original) {
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
