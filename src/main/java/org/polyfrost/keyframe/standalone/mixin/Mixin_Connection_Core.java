package org.polyfrost.keyframe.standalone.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Connection;
import org.polyfrost.keyframe.standalone.playback.Keyframe;
import org.polyfrost.keyframe.standalone.recording.Recorder;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
abstract class Mixin_Connection_Core {
	@Inject(
		method = "handleDisconnection",
		at = @At(value = "FIELD", target = "Lnet/minecraft/network/Connection;disconnectionHandled:Z", opcode = Opcodes.PUTFIELD)
	)
	private void keyframe$closeRecording(CallbackInfo ci) {
		Recorder.INSTANCE.close((Connection) (Object) this);
	}

	@WrapMethod(method = "exceptionCaught")
	private void keyframe$logDuringKeyframe(ChannelHandlerContext ctx, Throwable cause, Operation<Void> original) {
		if (Keyframe.INSTANCE.getActive()) {
			Keyframe.INSTANCE.onChannelException(cause);
		} else {
			original.call(ctx, cause);
		}
	}
}
