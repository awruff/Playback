package org.polyfrost.keyframe.standalone.mixin.ornithe;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.world.World;
import org.polyfrost.keyframe.standalone.playback.LocalEvents;
import org.polyfrost.keyframe.standalone.playback.Keyframe;
import org.polyfrost.keyframe.standalone.playback.Puppet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalClientPlayerEntity.class)
abstract class Mixin_LocalClientPlayerEntity_Puppet {
	@Inject(method = "tick", at = @At("HEAD"))
	private void keyframe$beforeTick(CallbackInfo ci) {
		Puppet.beforeTick((LocalClientPlayerEntity) (Object) this);
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void keyframe$afterTick(CallbackInfo ci) {
		Puppet.afterTick((LocalClientPlayerEntity) (Object) this);
	}

	@WrapWithCondition(
		method = "playSound",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;playSound(DDDLjava/lang/String;FFZ)V")
	)
	private boolean keyframe$recordOwnSound(World world, double x, double y, double z, String sound, float volume, float pitch, boolean ignoreDistance) {
		LocalEvents.sound(x, y, z, sound, volume, pitch);
		return !Keyframe.INSTANCE.getActive();
	}
}
