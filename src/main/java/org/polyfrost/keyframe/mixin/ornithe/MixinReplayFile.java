package org.polyfrost.keyframe.mixin.ornithe;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.network.PacketByteBuf;
import org.polyfrost.keyframe.Keyframe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "org.polyfrost.flashbacklegacy.keyframe.ReplayFile")
public abstract class MixinReplayFile {

    @Inject(method = "next", at = @At(value = "INVOKE", target = "Ljava/lang/String;hashCode()I"))
    private void keyframe$dispatch(CallbackInfoReturnable<?> cir, @Local(ordinal = 0) String action, @Local PacketByteBuf body) {
        Keyframe.dispatch(action, body);
    }

}
