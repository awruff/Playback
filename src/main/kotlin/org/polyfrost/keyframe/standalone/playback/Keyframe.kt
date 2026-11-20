package org.polyfrost.keyframe.standalone.playback

import com.mojang.authlib.GameProfile
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelOutboundHandlerAdapter
import io.netty.channel.ChannelPromise
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.flow.FlowControlHandler
import io.netty.util.ReferenceCountUtil
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.multiplayer.ClientConfigurationPacketListenerImpl
import net.minecraft.client.multiplayer.ClientRegistryLayer
import net.minecraft.client.multiplayer.CommonListenerCookie
//? if >=1.21.10
import net.minecraft.client.multiplayer.LevelLoadTracker
import net.minecraft.client.player.LocalPlayer
import net.minecraft.client.telemetry.TelemetryEventSender
import net.minecraft.client.telemetry.WorldSessionTelemetryManager
import net.minecraft.network.Connection
import net.minecraft.network.ProtocolInfo
import net.minecraft.network.UnconfiguredPipelineHandler
import net.minecraft.network.VarInt
import net.minecraft.network.protocol.PacketFlow
import net.minecraft.network.protocol.PacketType
import net.minecraft.network.protocol.common.CommonPacketTypes
import net.minecraft.network.protocol.configuration.ConfigurationPacketTypes
import net.minecraft.network.protocol.configuration.ConfigurationProtocols
import net.minecraft.network.protocol.cookie.CookiePacketTypes
import net.minecraft.network.protocol.game.GamePacketTypes
import net.minecraft.network.protocol.game.GameProtocols
import net.minecraft.server.ServerLinks
import net.minecraft.world.entity.Entity
import net.minecraft.world.flag.FeatureFlags
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.mojang.serialization.DynamicOps
import com.mojang.serialization.JsonOps
//? if >=26.1 {
import net.minecraft.client.multiplayer.chat.GuiMessage
import net.minecraft.client.multiplayer.chat.GuiMessageSource
//?} else
/*import net.minecraft.client.GuiMessage*/
import net.minecraft.core.RegistryAccess
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.ComponentSerialization
import org.polyfrost.keyframe.standalone.compat.*
import org.polyfrost.keyframe.standalone.mixin.Mixin_ChatComponent_Chat
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import org.polyfrost.keyframe.standalone.editor.TICKS_PER_SECOND
import org.polyfrost.keyframe.standalone.recording.ReplayHeader
import java.util.BitSet
import java.util.UUID

object Keyframe : KeyframeCore() {
	private val BLOCKED_TYPES by lazy {
		setOf(
			CommonPacketTypes.CLIENTBOUND_RESOURCE_PACK_PUSH,
			CommonPacketTypes.CLIENTBOUND_DISCONNECT,
			CommonPacketTypes.CLIENTBOUND_TRANSFER,
			CookiePacketTypes.CLIENTBOUND_COOKIE_REQUEST,
			CommonPacketTypes.CLIENTBOUND_STORE_COOKIE,
			//? if >=1.21.10 {
			CommonPacketTypes.CLIENTBOUND_SHOW_DIALOG,
			ConfigurationPacketTypes.CLIENTBOUND_CODE_OF_CONDUCT,
			//?}
			GamePacketTypes.CLIENTBOUND_COMMANDS,
		)
	}
	override val blockedConfig by lazy { packetIds(ConfigurationProtocols.CLIENTBOUND_TEMPLATE.details(), BLOCKED_TYPES) }
	override val blockedPlay by lazy { playIds(BLOCKED_TYPES) }
	override val loginPlay by lazy { playIds(setOf(GamePacketTypes.CLIENTBOUND_LOGIN)) }
	override val worldChangePlay by lazy { playIds(setOf(GamePacketTypes.CLIENTBOUND_RESPAWN, GamePacketTypes.CLIENTBOUND_START_CONFIGURATION)) }
	override val noisyPlay by lazy {
		playIds(
			setOf(
				GamePacketTypes.CLIENTBOUND_SOUND,
				GamePacketTypes.CLIENTBOUND_SOUND_ENTITY,
				GamePacketTypes.CLIENTBOUND_LEVEL_PARTICLES,
				GamePacketTypes.CLIENTBOUND_LEVEL_EVENT,
			),
		)
	}
	override val chatPlay by lazy {
		playIds(setOf(GamePacketTypes.CLIENTBOUND_SYSTEM_CHAT, GamePacketTypes.CLIENTBOUND_PLAYER_CHAT, GamePacketTypes.CLIENTBOUND_DISGUISED_CHAT))
	}

	override val rollsInMenu = false

	override fun openChannel(header: ReplayHeader): Pair<EmbeddedChannel, Connection> {
		val channel = EmbeddedChannel(false, false)
		val connection = Connection(PacketFlow.CLIENTBOUND)
		val pipeline = channel.pipeline()
		Connection.configureInMemoryPipeline(pipeline, PacketFlow.CLIENTBOUND)
		connection.configurePacketHandler(pipeline)
		pipeline.remove(FlowControlHandler::class.java)
		pipeline.addAfter("hackfix", "keyframe:outbound_dropper", OutboundDropper())
		channel.register()
		connection.setupInboundProtocol(
			ConfigurationProtocols.CLIENTBOUND,
			ClientConfigurationPacketListenerImpl(Minecraft.getInstance(), connection, cookie(header)),
		)
		return channel to connection
	}

	override fun frame(payload: ByteArray, skips: (Int) -> Boolean): Any? {
		val frame = Unpooled.wrappedBuffer(payload)
		val id = VarInt.read(frame)
		frame.readerIndex(0)
		return if (skips(id)) null else frame
	}

	override fun clearWorld(level: ClientLevel, player: LocalPlayer) {
		level.entitiesForRendering().filter { it !== player }.forEach { level.removeEntity(it.id, Entity.RemovalReason.DISCARDED) }
		player.inventory.clearContent()
		val scoreboard = level.scoreboard
		scoreboard.objectives.toList().forEach(scoreboard::removeObjective)
		scoreboard.playerTeams.toList().forEach(scoreboard::removePlayerTeam)
		player.activeEffectsMap.clear()
		Minecraft.getInstance().gui.hud.onDisconnected()
	}

	override fun playRate(session: KeyframeCore.Session): Float {
		val rate = TICKS_PER_SECOND * minOf(session.clipSpeed, 1f)
		if (session.slowed || rate < TICKS_PER_SECOND) {
			Minecraft.getInstance().level?.tickRateManager()?.setTickRate(rate)
			session.slowed = rate < TICKS_PER_SECOND
		}
		return maxOf(session.clipSpeed, 1f)
	}

	override fun stopped() {
		//? if >=1.21.10
		Minecraft.getInstance().gui.hud.chat.discardDraft()
	}

	override fun sampleChatHistory(): ByteArray? {
		val hud = Minecraft.getInstance().gui.hud
		val messages = (hud.chat as Mixin_ChatComponent_Chat).`keyframe$getAllMessages`().toList()
		if (messages.isEmpty()) return null
		val ops = chatOps()
		val bytes = ByteArrayOutputStream()
		DataOutputStream(bytes).use { out ->
			out.writeInt(messages.size)
			for (message in messages) {
				val json = encodeChat(ops, message.content()).toByteArray(Charsets.UTF_8)
				out.writeInt(hud.guiTicks - message.addedTime())
				out.writeInt(json.size)
				out.write(json)
			}
		}
		return bytes.toByteArray()
	}

	override fun onChatHistory(payload: ByteArray) {
		val hud = Minecraft.getInstance().gui.hud
		val messages = (hud.chat as Mixin_ChatComponent_Chat).`keyframe$getAllMessages`()
		val ops = chatOps()
		DataInputStream(ByteArrayInputStream(payload)).use { input ->
			repeat(input.readInt()) {
				val age = input.readInt()
				val json = String(input.readNBytes(input.readInt()), Charsets.UTF_8)
				val content = runCatching { ComponentSerialization.CODEC.parse(ops, JsonParser.parseString(json)).getOrThrow() }.getOrNull() ?: return@repeat
				//? if >=26.1 {
				messages.add(GuiMessage(hud.guiTicks - age, content, null, GuiMessageSource.SYSTEM_SERVER, null))
				//?} else
				/*messages.add(GuiMessage(hud.guiTicks - age, content, null, null))*/
			}
		}
		hud.chat.rescaleChat()
	}

	private fun chatOps(): DynamicOps<JsonElement> =
		(Minecraft.getInstance().level?.registryAccess() ?: RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY))
			.createSerializationContext(JsonOps.INSTANCE)

	private fun encodeChat(ops: DynamicOps<JsonElement>, content: Component): String =
		ComponentSerialization.CODEC.encodeStart(ops, content).result()
			.orElseGet { ComponentSerialization.CODEC.encodeStart(ops, Component.literal(content.string)).getOrThrow() }
			.toString()

	private fun playIds(types: Set<PacketType<*>>): BitSet = packetIds(GameProtocols.CLIENTBOUND_TEMPLATE.details(), types)

	private fun packetIds(details: ProtocolInfo.Details, types: Set<PacketType<*>>): BitSet {
		val ids = BitSet()
		details.listPackets { type, id -> if (type in types) ids.set(id) }
		return ids
	}

	private fun cookie(header: ReplayHeader) = CommonListenerCookie(
		//? if >=1.21.10
		LevelLoadTracker(),
		GameProfile(header.uuid, header.name),
		//? if >=26.2 {
		WorldSessionTelemetryManager(TelemetryEventSender.DISABLED, false, null, null, UUID.randomUUID()),
		//?} else
		/*WorldSessionTelemetryManager(TelemetryEventSender.DISABLED, false, null, null),*/
		ClientRegistryLayer.createRegistryAccess().compositeAccess(),
		FeatureFlags.DEFAULT_FLAGS,
		null,
		null,
		null,
		mapOf(),
		null,
		//? if >=1.21.10 {
		mapOf(),
		ServerLinks.EMPTY,
		hashMapOf(),
		false,
		//?} elif >=1.21.4 {
		/*mapOf(),
		ServerLinks.EMPTY,
		*///?} else {
		/*false,
		mapOf(),
		ServerLinks.EMPTY,
		*///?}
	)

	private class OutboundDropper : ChannelOutboundHandlerAdapter() {
		override fun write(ctx: ChannelHandlerContext, msg: Any, promise: ChannelPromise) {
			if (msg is UnconfiguredPipelineHandler.InboundConfigurationTask) {
				ctx.write(msg, promise)
			} else {
				ReferenceCountUtil.release(msg)
				promise.setSuccess()
			}
		}
	}
}
