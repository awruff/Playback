package org.polyfrost.keyframe.standalone.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.Minecraft;
//? if <26.2
/*import net.minecraft.client.gui.screens.Screen;*/
//? if <1.21.10 {
/*import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.polyfrost.keyframe.standalone.playback.LocalEvents;
*///?}
import org.polyfrost.keyframe.standalone.playback.Keyframe;
import org.polyfrost.keyframe.standalone.recording.Recorder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
abstract class Mixin_Minecraft_Core {
	@Inject(method = "tick", at = @At("HEAD"))
	private void keyframe$tick(CallbackInfo ci) {
		Recorder.INSTANCE.tick();
		Keyframe.INSTANCE.pump();
	}

	@WrapMethod(method = "pauseGame")
	private void keyframe$gatePause(boolean suppressPauseMenu, Operation<Void> original) {
		if (!Keyframe.INSTANCE.getActive()) {
			original.call(suppressPauseMenu);
		}
	}

	//? if <26.2 {
	/*@WrapMethod(method = "setScreen")
	private void keyframe$gateScreen(Screen screen, Operation<Void> original) {
		if (Keyframe.INSTANCE.allowsScreen(screen)) {
			original.call(screen);
		}
	}

	@Inject(method = "setScreen", at = @At("RETURN"))
	private void keyframe$writeScreen(CallbackInfo ci) {
		Recorder.INSTANCE.writeScreen();
	}
	*///?}

	//? if <1.21.10 {
	/*@WrapOperation(
		method = "continueAttack",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/ParticleEngine;crack(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)V")
	)
	private void keyframe$recordBreaking(ParticleEngine particles, BlockPos pos, Direction direction, Operation<Void> original) {
		LocalEvents.breaking(pos, direction, false);
		original.call(particles, pos, direction);
	}
	*///?}
}
