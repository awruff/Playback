package org.polyfrost.keyframe.standalone.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
//? if <1.21.8
/*import net.minecraft.world.entity.player.Player;*/
import org.polyfrost.keyframe.standalone.playback.LocalEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientPacketListener.class)
abstract class Mixin_ClientPacketListener_Audio {
	@WrapOperation(
		method = "handleSoundEvent",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;playSeededSound(Lnet/minecraft/world/entity/Entity;DDDLnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V")
	)
	private void keyframe$markServerSound(ClientLevel level, Entity except, double x, double y, double z, Holder<SoundEvent> sound, SoundSource source, float volume, float pitch, long seed, Operation<Void> original) {
		LocalEvents.asServer(() -> original.call(level, except, x, y, z, sound, source, volume, pitch, seed));
	}

	@WrapOperation(
		method = "handleSoundEntityEvent",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;playSeededSound(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V")
	)
	private void keyframe$markServerEntitySound(ClientLevel level, Entity except, Entity entity, Holder<SoundEvent> sound, SoundSource source, float volume, float pitch, long seed, Operation<Void> original) {
		LocalEvents.asServer(() -> original.call(level, except, entity, sound, source, volume, pitch, seed));
	}
}
