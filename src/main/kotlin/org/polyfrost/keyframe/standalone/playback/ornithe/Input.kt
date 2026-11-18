package org.polyfrost.keyframe.standalone.playback

import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import net.minecraft.network.NetworkProtocol
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.PacketFlow
import net.minecraft.network.packet.Packet
import net.minecraft.network.packet.s2c.play.CustomPayloadS2CPacket
import org.lwjgl.input.Keyboard
import org.polyfrost.keyframe.standalone.Standalone
import org.polyfrost.keyframe.standalone.recording.Recorder
import org.slf4j.LoggerFactory

object InputGate {
	private var stopRequested = false

	@JvmStatic
	fun mouse(next: Operation<Boolean>): Boolean {
		if (!Keyframe.blocksInput) return next.call()
		while (next.call()) continue
		return false
	}

	@JvmStatic
	fun keyboard(next: Operation<Boolean>): Boolean {
		if (!Keyframe.blocksInput) return next.call()
		while (next.call()) {
			if (Keyboard.getEventKeyState() && Keyboard.getEventKey() == Keyboard.KEY_ESCAPE) stopRequested = true
		}
		return false
	}

	@JvmStatic
	fun flush() {
		if (stopRequested) Keyframe.stop()
		stopRequested = false
	}
}

object PacketTap {
	private val LOGGER = LoggerFactory.getLogger(Standalone.MOD_ID)

	@JvmStatic
	fun frame(frame: ByteBuf) {
		if (!Recorder.writing || !frame.isReadable) return
		Recorder.tap(ByteArray(frame.readableBytes()).also { frame.getBytes(frame.readerIndex(), it) })
	}

	@JvmStatic
	fun local(packet: Packet<*>) {
		if (!Recorder.writing) return
		val id = NetworkProtocol.PLAY.getPacketId(PacketFlow.CLIENTBOUND, packet) ?: return
		val buffer = PacketByteBuf(Unpooled.buffer())
		val payload = (packet as? CustomPayloadS2CPacket)?.data
		payload?.markReaderIndex()
		try {
			buffer.writeVarInt(id)
			packet.write(buffer)
			Recorder.tap(ByteArray(buffer.readableBytes()).also(buffer::readBytes))
		} catch (e: Exception) {
			LOGGER.warn("Could not record {}", packet.javaClass.name, e)
		} finally {
			payload?.resetReaderIndex()
			buffer.release()
		}
	}
}
