package org.polyfrost.keyframe.standalone.mixin;

import net.minecraft.client.Camera;
//? if >=26.1 {
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.DeltaTracker;
//?} else
/*import net.minecraft.client.Minecraft;*/
import org.polyfrost.keyframe.camera.ViewCapture;
import org.polyfrost.keyframe.standalone.playback.Puppet;
import org.polyfrost.keyframe.standalone.recording.Recorder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
abstract class Mixin_Camera_Puppet {
	//? if >=26.1 {
	@Inject(method = "update", at = @At("HEAD"))
	private void keyframe$applyCamera(DeltaTracker deltaTracker, CallbackInfo ci) {
		Puppet.INSTANCE.applyCamera(deltaTracker);
	}

	@ModifyExpressionValue(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;calculateFov(F)F"))
	private float keyframe$fov(float fov) {
		return ViewCapture.fov(fov);
	}

	@Inject(method = "update", at = @At("TAIL"))
	private void keyframe$writeCamera(DeltaTracker deltaTracker, CallbackInfo ci) {
		ViewCapture.capture((Camera) (Object) this, deltaTracker.getGameTimeDeltaPartialTick(true));
		Recorder.INSTANCE.writeCamera(deltaTracker);
	}
	//?} else {
	/*@Inject(method = "setup", at = @At("HEAD"))
	private void keyframe$applyCamera(CallbackInfo ci) {
		Puppet.INSTANCE.applyCamera(Minecraft.getInstance().getDeltaTracker());
	}

	@Inject(method = "setup", at = @At("TAIL"))
	private void keyframe$writeCamera(CallbackInfo ci) {
		ViewCapture.capture((Camera) (Object) this, Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true));
		Recorder.INSTANCE.writeCamera(Minecraft.getInstance().getDeltaTracker());
	}
	*///?}
}
