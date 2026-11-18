package org.polyfrost.keyframe.standalone.mixin.ornithe;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.block.state.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import org.polyfrost.keyframe.standalone.playback.LocalEvents;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ClientWorld.class)
abstract class Mixin_ClientWorld_Events {
	@WrapMethod(method = "setBlockStateFromPacket")
	private boolean keyframe$markPacket(BlockPos pos, BlockState state, Operation<Boolean> original) {
		return LocalEvents.asPacket(() -> original.call(pos, state));
	}
}
