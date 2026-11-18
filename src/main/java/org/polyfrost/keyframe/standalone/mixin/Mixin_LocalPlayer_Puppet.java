package org.polyfrost.keyframe.standalone.mixin;

import net.minecraft.client.player.LocalPlayer;
import org.polyfrost.keyframe.standalone.playback.Puppet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
abstract class Mixin_LocalPlayer_Puppet {
	@Inject(method = "tick", at = @At("HEAD"))
	private void keyframe$beforeTick(CallbackInfo ci) {
		Puppet.INSTANCE.beforeTick((LocalPlayer) (Object) this);
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void keyframe$afterTick(CallbackInfo ci) {
		Puppet.INSTANCE.afterTick((LocalPlayer) (Object) this);
	}
}
