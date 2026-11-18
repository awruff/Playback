package org.polyfrost.keyframe.standalone.mixin;

import net.minecraft.client.multiplayer.ClientPacketListener;
import org.polyfrost.keyframe.standalone.recording.Recorder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
abstract class Mixin_ClientPacketListener_Core {
	@Inject(method = "close", at = @At("HEAD"))
	private void keyframe$closeRecording(CallbackInfo ci) {
		Recorder.INSTANCE.close(((ClientPacketListener) (Object) this).getConnection());
	}
}
