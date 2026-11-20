package org.polyfrost.keyframe.standalone.playback

import com.mojang.authlib.GameProfile
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelOutboundHandlerAdapter
import io.netty.channel.ChannelPromise
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.util.ReferenceCountUtil
import net.minecraft.client.Minecraft
import net.minecraft.client.network.handler.ClientPlayNetworkHandler
import net.minecraft.network.Connection
import net.minecraft.network.NetworkProtocol
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.PacketFlow
import net.minecraft.network.packet.Packet
import net.minecraft.network.packet.s2c.play.DisconnectS2CPacket
import net.minecraft.network.packet.s2c.play.LoginS2CPacket
import net.minecraft.network.packet.s2c.play.ParticleS2CPacket
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket
import net.minecraft.network.packet.s2c.play.ResourcePackS2CPacket
import net.minecraft.network.packet.s2c.play.CompressionThresholdS2CPacket
import net.minecraft.network.packet.s2c.play.SoundEventS2CPacket
import net.minecraft.network.packet.s2c.play.WorldEventS2CPacket
import org.polyfrost.keyframe.standalone.compat.*
import org.polyfrost.keyframe.standalone.recording.ReplayHeader
import java.util.BitSet

private const val MAX_PACKET_ID = 256

object Keyframe : KeyframeCore() {
	override val blockedConfig: BitSet? = null
	override val blockedPlay by lazy {
		playIds { it is ResourcePackS2CPacket || it is DisconnectS2CPacket || it is CompressionThresholdS2CPacket }
	}
	override val loginPlay by lazy { playIds { it is LoginS2CPacket } }
	override val worldChangePlay by lazy { playIds { it is PlayerRespawnS2CPacket } }
	override val noisyPlay by lazy { playIds { it is SoundEventS2CPacket || it is ParticleS2CPacket || it is WorldEventS2CPacket } }

	override val rollsInMenu = true

	override fun openChannel(header: ReplayHeader): Pair<EmbeddedChannel, Connection> {
		val connection = Connection(PacketFlow.CLIENTBOUND)
		val channel = EmbeddedChannel(connection, OutboundDropper())
		connection.setProtocol(NetworkProtocol.PLAY)
		connection.setListener(ClientPlayNetworkHandler(Minecraft.getInstance(), null, connection, GameProfile(header.uuid, header.name)))
		return channel to connection
	}

	override fun frame(payload: ByteArray, skips: (Int) -> Boolean): Any? {
		val frame = PacketByteBuf(Unpooled.wrappedBuffer(payload))
		val id = frame.readVarInt()
		if (skips(id)) return null
		val packet = NetworkProtocol.PLAY.createPacket(PacketFlow.CLIENTBOUND, id) ?: error("Unknown packet $id")
		packet.read(frame)
		return packet
	}

	override fun clearWorld(level: ClientLevel, player: LocalPlayer) {
		level.entities.toList().filter { it !== player }.forEach { level.removeEntity(it.networkId) }
		player.inventory.clear()
		val scoreboard = level.scoreboard
		scoreboard.objectives.toList().forEach(scoreboard::removeObjective)
		scoreboard.teams.toList().forEach(scoreboard::removeTeam)
		player.clearStatusEffects()
		val gui = Minecraft.getInstance().gui
		gui.resetPlayerTabOverlay()
		gui.chat.clear()
	}

	override fun playRate(session: KeyframeCore.Session) = session.clipSpeed

	override fun started() = ArgentumCompat.forceUnbatchedFont(true)

	override fun stopped() = ArgentumCompat.forceUnbatchedFont(false)

	private fun playIds(match: (Packet<*>) -> Boolean): BitSet {
		val ids = BitSet()
		for (id in 0 until MAX_PACKET_ID) {
			val packet = NetworkProtocol.PLAY.createPacket(PacketFlow.CLIENTBOUND, id) ?: continue
			if (match(packet)) ids.set(id)
		}
		return ids
	}

	private class OutboundDropper : ChannelOutboundHandlerAdapter() {
		override fun write(ctx: ChannelHandlerContext, msg: Any, promise: ChannelPromise) {
			ReferenceCountUtil.release(msg)
			promise.setSuccess()
		}
	}
}
