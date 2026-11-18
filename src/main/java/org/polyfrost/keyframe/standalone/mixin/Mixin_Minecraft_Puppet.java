package org.polyfrost.keyframe.standalone.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import org.polyfrost.keyframe.standalone.playback.Puppet;
import org.polyfrost.keyframe.standalone.recording.Recorder;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
abstract class Mixin_Minecraft_Puppet {
	@Inject(method = "tick", at = @At("TAIL"))
	private void keyframe$writeSnapshot(CallbackInfo ci) {
		Recorder.INSTANCE.writeSnapshot();
	}

	@WrapOperation(
		method = "runTick",
		at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;pause:Z", opcode = Opcodes.PUTFIELD)
	)
	private void keyframe$holdPause(Minecraft minecraft, boolean pause, Operation<Void> original) {
		original.call(minecraft, pause || Puppet.INSTANCE.getPaused());
	}
}
