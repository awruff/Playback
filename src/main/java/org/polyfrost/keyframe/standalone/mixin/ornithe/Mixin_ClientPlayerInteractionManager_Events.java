package org.polyfrost.keyframe.standalone.mixin.ornithe;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.ClientPlayerInteractionManager;
import net.minecraft.client.sound.instance.SoundInstance;
import net.minecraft.client.sound.system.SoundManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.polyfrost.keyframe.standalone.recording.Highlights;
import org.polyfrost.keyframe.standalone.playback.LocalEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
abstract class Mixin_ClientPlayerInteractionManager_Events {
	@Inject(method = "attackEntity", at = @At("HEAD"))
	private void keyframe$onAttack(PlayerEntity player, Entity target, CallbackInfo ci) {
		Highlights.onAttack(target);
	}

	@Inject(method = "finishMiningBlock", at = @At("HEAD"))
	private void keyframe$onBreak(BlockPos pos, Direction face, CallbackInfoReturnable<Boolean> cir) {
		Highlights.onBreak(pos);
	}

	@WrapOperation(
		method = "tickBlockMining",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/sound/system/SoundManager;play(Lnet/minecraft/client/sound/instance/SoundInstance;)V")
	)
	private void keyframe$recordMiningSound(SoundManager sounds, SoundInstance sound, Operation<Void> original) {
		LocalEvents.sound(sound.getX(), sound.getY(), sound.getZ(), sound.getLocation().toString(), sound.getVolume(), sound.getPitch());
		original.call(sounds, sound);
	}
}
