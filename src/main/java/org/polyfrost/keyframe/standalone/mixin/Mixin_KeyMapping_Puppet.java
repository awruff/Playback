package org.polyfrost.keyframe.standalone.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.KeyMapping;
import org.polyfrost.keyframe.standalone.playback.Puppet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(KeyMapping.class)
abstract class Mixin_KeyMapping_Puppet {
	@ModifyReturnValue(method = "isDown", at = @At("RETURN"))
	private boolean keyframe$holdUseKey(boolean down) {
		return Puppet.INSTANCE.isDown((KeyMapping) (Object) this, down);
	}
}
