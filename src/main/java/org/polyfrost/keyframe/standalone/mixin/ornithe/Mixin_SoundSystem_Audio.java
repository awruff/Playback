package org.polyfrost.keyframe.standalone.mixin.ornithe;

import org.polyfrost.keyframe.standalone.export.AudioCapture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import paulscode.sound.CommandObject;
import paulscode.sound.Library;
import paulscode.sound.SoundSystem;

import java.util.List;

@Mixin(value = SoundSystem.class, remap = false)
abstract class Mixin_SoundSystem_Audio {
	@Shadow
	protected List<CommandObject> commandQueue;

	@Shadow
	protected Library soundLibrary;

	@Inject(method = "CommandQueue", at = @At("HEAD"))
	private void keyframe$shareQueue(CommandObject command, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
		AudioCapture.attach(this.commandQueue, this.soundLibrary);
	}
}
