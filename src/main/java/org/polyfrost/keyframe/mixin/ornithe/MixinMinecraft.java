package org.polyfrost.keyframe.mixin.ornithe;

import net.minecraft.client.Minecraft;
import org.polyfrost.keyframe.Keyframe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MixinMinecraft {

    @Inject(method = "tick", at = @At("TAIL"))
    private void keyframe$tick(CallbackInfo ci) {
        Keyframe.tick();
    }

}
