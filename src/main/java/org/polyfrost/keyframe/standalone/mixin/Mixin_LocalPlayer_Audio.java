package org.polyfrost.keyframe.standalone.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import org.polyfrost.keyframe.standalone.playback.LocalEvents;
import org.polyfrost.keyframe.standalone.playback.Keyframe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LocalPlayer.class)
abstract class Mixin_LocalPlayer_Audio {
	@WrapWithCondition(
		method = "playSound(Lnet/minecraft/sounds/SoundEvent;FF)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;playLocalSound(DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FFZ)V")
	)
	private boolean keyframe$recordOwnSound(Level level, double x, double y, double z, SoundEvent sound, SoundSource source, float volume, float pitch, boolean distanceDelay) {
		LocalEvents.sound(x, y, z, sound, source, volume, pitch, level.getRandom().nextLong());
		return !Keyframe.INSTANCE.getActive();
	}
}
