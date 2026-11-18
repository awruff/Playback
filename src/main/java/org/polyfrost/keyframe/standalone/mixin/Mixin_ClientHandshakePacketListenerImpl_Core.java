package org.polyfrost.keyframe.standalone.mixin;

import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.login.ClientboundLoginFinishedPacket;
import org.polyfrost.keyframe.standalone.recording.Recorder;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientHandshakePacketListenerImpl.class)
abstract class Mixin_ClientHandshakePacketListenerImpl_Core {
	@Shadow
	@Final
	private Connection connection;

	@Inject(method = "handleLoginFinished", at = @At("HEAD"))
	private void keyframe$openRecording(ClientboundLoginFinishedPacket packet, CallbackInfo ci) {
		Recorder.INSTANCE.open(connection, packet.gameProfile());
	}
}
