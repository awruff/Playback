package org.polyfrost.keyframe.standalone.mixin;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import java.util.List;
import net.minecraft.network.PacketDecoder;
import net.minecraft.network.ProtocolInfo;
import org.polyfrost.keyframe.standalone.recording.Recorder;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PacketDecoder.class)
abstract class Mixin_PacketDecoder_Core {
	@Shadow
	@Final
	private ProtocolInfo<?> protocolInfo;

	@Inject(method = "decode", at = @At("HEAD"))
	private void keyframe$tap(ChannelHandlerContext ctx, ByteBuf in, List<Object> out, CallbackInfo ci) {
		if (ctx.handler() == (Object) this) {
			Recorder.INSTANCE.tap(protocolInfo, in);
		}
	}
}
