package org.polyfrost.keyframe.standalone.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
//? if <1.21.8
/*import net.minecraft.world.entity.player.Player;*/
import org.polyfrost.keyframe.standalone.playback.LocalEvents;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
abstract class Mixin_ClientLevel_Audio {
	@Shadow
	@Final
	private Minecraft minecraft;

	@Inject(
		method = "playSeededSound(Lnet/minecraft/world/entity/Entity;DDDLnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V",
		at = @At("HEAD")
	)
	private void keyframe$recordSound(@Nullable Entity except, double x, double y, double z, Holder<SoundEvent> sound, SoundSource source, float volume, float pitch, long seed, CallbackInfo ci) {
		if (except != null && except == this.minecraft.player) LocalEvents.sound(x, y, z, sound.value(), source, volume, pitch, seed);
	}

	@Inject(
		method = "playSeededSound(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V",
		at = @At("HEAD")
	)
	private void keyframe$recordEntitySound(@Nullable Entity except, Entity entity, Holder<SoundEvent> sound, SoundSource source, float volume, float pitch, long seed, CallbackInfo ci) {
		if (except != null && except == this.minecraft.player) LocalEvents.entitySound(entity.getId(), sound.value(), source, volume, pitch, seed);
	}

	@Inject(method = "levelEvent", at = @At("HEAD"))
	private void keyframe$recordLevelEvent(@Nullable Entity source, int type, BlockPos pos, int data, CallbackInfo ci) {
		if (source != null && source == this.minecraft.player) LocalEvents.levelEvent(type, pos, data);
	}

	@Inject(method = "destroyBlockProgress", at = @At("HEAD"))
	private void keyframe$recordDestroyProgress(int breaker, BlockPos pos, int progress, CallbackInfo ci) {
		if (this.minecraft.player != null && breaker == this.minecraft.player.getId()) LocalEvents.destroyProgress(pos, progress);
	}

	//? if >=26.3 {
	@Inject(method = "addBreakingBlockEffects", at = @At("HEAD"))
	private void keyframe$recordBreaking(BlockPos pos, Direction direction, boolean playSound, CallbackInfo ci) {
		LocalEvents.breaking(pos, direction, playSound);
	}
	//?} elif >=1.21.10 {
	/*@Inject(method = "addBreakingBlockEffect", at = @At("HEAD"))
	private void keyframe$recordBreaking(BlockPos pos, Direction direction, CallbackInfo ci) {
		LocalEvents.breaking(pos, direction, false);
	}
	*///?}
}
