package org.polyfrost.keyframe.standalone.mixin.ornithe;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.options.GameOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GameOptions.class)
abstract class Mixin_GameOptions_Transport {
	@ModifyReturnValue(method = "shouldUseNativeTransport", at = @At("RETURN"))
	private boolean keyframe$useNio(boolean original) {
		return false;
	}
}
