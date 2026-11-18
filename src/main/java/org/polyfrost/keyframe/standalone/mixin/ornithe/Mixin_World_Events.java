package org.polyfrost.keyframe.standalone.mixin.ornithe;

import net.minecraft.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.polyfrost.keyframe.standalone.playback.LocalEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(World.class)
abstract class Mixin_World_Events {
	@Inject(method = "doEvent(Lnet/minecraft/entity/living/player/PlayerEntity;ILnet/minecraft/util/math/BlockPos;I)V", at = @At("HEAD"))
	private void keyframe$recordLevelEvent(PlayerEntity source, int type, BlockPos pos, int data, CallbackInfo ci) {
		if (source != null && source == Minecraft.getInstance().player) LocalEvents.levelEvent(type, pos, data);
	}

	@Inject(method = "updateBlockMiningProgress", at = @At("HEAD"))
	private void keyframe$recordDestroyProgress(int breaker, BlockPos pos, int progress, CallbackInfo ci) {
		PlayerEntity player = Minecraft.getInstance().player;
		if (player != null && breaker == player.getNetworkId()) LocalEvents.destroyProgress(pos, progress);
	}

	@Inject(method = "setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/state/BlockState;I)Z", at = @At("RETURN"))
	private void keyframe$recordLocalBlock(BlockPos pos, BlockState state, int flags, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() && (Object) this instanceof ClientWorld) LocalEvents.localBlock(pos, state, flags);
	}
}
