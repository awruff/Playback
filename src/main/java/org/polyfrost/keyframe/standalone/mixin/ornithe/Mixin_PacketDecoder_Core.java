package org.polyfrost.keyframe.standalone.mixin.ornithe;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Connection;
import net.minecraft.network.NetworkProtocol;
import net.minecraft.network.PacketDecoder;
import net.minecraft.network.PacketFlow;
import org.polyfrost.keyframe.standalone.playback.PacketTap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(PacketDecoder.class)
abstract class Mixin_PacketDecoder_Core {
	@Shadow
	@Final
	private PacketFlow flow;

	@Inject(method = "decode", at = @At("HEAD"))
	private void keyframe$tap(ChannelHandlerContext ctx, ByteBuf in, List<Object> out, CallbackInfo ci) {
		if (this.flow == PacketFlow.CLIENTBOUND && ctx.channel().attr(Connection.PROTOCOL).get() == NetworkProtocol.PLAY) {
			PacketTap.frame(in);
		}
	}
}
