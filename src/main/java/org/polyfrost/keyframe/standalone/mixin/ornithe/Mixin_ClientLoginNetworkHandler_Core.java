package org.polyfrost.keyframe.standalone.mixin.ornithe;

import net.minecraft.client.network.handler.ClientLoginNetworkHandler;
import net.minecraft.network.Connection;
import net.minecraft.network.packet.s2c.login.LoginSuccessS2CPacket;
import org.polyfrost.keyframe.standalone.recording.Recorder;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLoginNetworkHandler.class)
abstract class Mixin_ClientLoginNetworkHandler_Core {
	@Shadow
	@Final
	private Connection connection;

	@Inject(method = "handleLoginSuccess", at = @At("HEAD"))
	private void keyframe$openRecording(LoginSuccessS2CPacket packet, CallbackInfo ci) {
		Recorder.INSTANCE.open(this.connection, packet.getProfile());
	}
}
