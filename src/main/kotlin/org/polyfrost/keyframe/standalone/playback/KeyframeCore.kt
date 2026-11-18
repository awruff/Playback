package org.polyfrost.keyframe.standalone.playback

import io.netty.channel.embedded.EmbeddedChannel
import net.minecraft.SharedConstants
import net.minecraft.client.Minecraft
//? if >=1.21.1 {
import net.minecraft.client.gui.screens.LevelLoadingScreen
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.chat.Component
//?}
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.network.Connection
import org.polyfrost.keyframe.standalone.Channels
import org.polyfrost.keyframe.standalone.Standalone
import org.polyfrost.keyframe.standalone.compat.*
import org.polyfrost.keyframe.standalone.editor.EditorScreen
import org.polyfrost.keyframe.standalone.export.AudioCapture
import org.polyfrost.keyframe.standalone.export.Export
import org.polyfrost.keyframe.standalone.export.ExportSettings
import org.polyfrost.keyframe.standalone.export.MAX_PRE_ROLL_TICKS
import org.polyfrost.keyframe.standalone.export.exportFrames
import org.polyfrost.keyframe.standalone.recording.Clip
import org.polyfrost.keyframe.standalone.recording.Recorder
import org.polyfrost.keyframe.standalone.recording.ReplayFile
import org.polyfrost.keyframe.standalone.recording.ReplayHeader
import org.polyfrost.keyframe.standalone.recording.ReplayReader
import org.polyfrost.keyframe.standalone.recording.ReplayRecord
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.Path
import java.util.BitSet

private const val ROLL_BUDGET_NANOS = 25_000_000L
private const val SETTLE_TICKS = 5

abstract class KeyframeCore {
	private val LOGGER = LoggerFactory.getLogger(Standalone.MOD_ID)

	protected abstract val blockedConfig: BitSet?
	protected abstract val blockedPlay: BitSet
	protected abstract val loginPlay: BitSet
	protected abstract val worldChangePlay: BitSet
	protected abstract val noisyPlay: BitSet

	protected abstract val rollsInMenu: Boolean

	protected abstract fun openChannel(header: ReplayHeader): Pair<EmbeddedChannel, Connection>

	protected abstract fun frame(payload: ByteArray, skips: (Int) -> Boolean): Any?

	internal abstract fun clearWorld(level: ClientLevel, player: LocalPlayer)

	internal abstract fun playRate(session: Session): Float

	protected open fun started() {}

	protected open fun stopped() {}

	private var session: Session? = null

	val active: Boolean
		get() = session != null

	val tick: Int
		get() = session?.tick ?: 0

	val previewing: Boolean
		get() = session?.preview == true

	val silentExport: Boolean
		get() = session?.export?.audio == false

	val blocksInput: Boolean
		get() = active && !previewing

	val loaded: Boolean
		get() = Minecraft.getInstance().let { it.level != null && it.gui.screen() !is LevelLoadingScreen }

	val inMenu: Boolean
		get() = session?.crossedBreak == true && Minecraft.getInstance().level == null

	val settled: Boolean
		get() = session?.let { (loaded || inMenu) && it.tick >= it.rollTo } == true

	val rolling: Boolean
		get() = session?.let { it.preview && it.tick < it.rollTo } == true

	val canRewind: Boolean
		get() = session?.worldChanged == false

	val held: Boolean
		get() = session?.let { it.preview && !it.playing && it.settle == 0 && it.tick >= it.rollTo && loaded } == true

	var playing: Boolean
		get() = session?.playing == true
		set(value) {
			session?.playing = value
		}

	var speed: Int
		get() = session?.speed ?: 1
		set(value) {
			session?.speed = value
		}

	var clipSpeed: Float
		get() = session?.clipSpeed ?: 1f
		set(value) {
			session?.clipSpeed = value
		}

	fun start(file: Path, export: ExportSettings? = null, range: Clip? = null, preview: Boolean = false) {
		val mc = Minecraft.getInstance()
		check(session == null && mc.level == null) { "Keyframe starts only from outside a world" }
		Recorder.finish()
		val protocol = SharedConstants.getCurrentVersion().protocolVersion()
		val clip = range ?: ReplayReader(file, protocol).use { it.lastClip() } ?: error("$file has no recorded clip")
		if (export?.audio == true) AudioCapture.arm()
		val reader = ReplayReader(file, protocol)
		val (channel, connection) = openChannel(reader.header)
		Puppet.begin(ReplayReader(file, protocol))
		ScreenReplay.begin(ReplayReader(file, protocol))
		session = Session(file, reader, channel, connection, clip, export, preview)
		started()
	}

	fun seek(tick: Int) {
		val session = session ?: return
		if (tick <= session.tick) return
		session.rollTo = tick
		session.settle = SETTLE_TICKS
	}

	fun rewind(tick: Int): Boolean {
		val session = session ?: return false
		val mc = Minecraft.getInstance()
		val level = mc.level ?: return false
		val player = mc.player ?: return false
		if (!session.preview || !loaded || session.worldChanged) return false
		val protocol = session.reader.header.protocol
		val readers = try {
			List(3) { ReplayReader(session.file, protocol) }
		} catch (e: IOException) {
			LOGGER.warn("Could not reopen {}", session.file, e)
			return false
		}
		session.reader.close()
		Puppet.end()
		ScreenReplay.end()
		Channels.clearReplayed()
		session.reader = readers[0]
		Puppet.begin(readers[1])
		ScreenReplay.begin(readers[2])
		LocalEvents.clearLoops()
		session.tick = 0
		session.pending = null
		session.rollTo = tick
		session.settle = SETTLE_TICKS
		session.rewound = true
		clearWorld(level, player)
		return true
	}

	fun allowsScreen(screen: Screen?): Boolean {
		if (!previewing) return true
		val current = Minecraft.getInstance().gui.screen()
		if (current !is EditorScreen || screen is EditorScreen || screen is LevelLoadingScreen) return true
		ScreenReplay.adopt(screen)
		return false
	}

	@JvmOverloads
	fun stop(next: Screen = TitleScreen()) {
		val session = session ?: return
		this.session = null
		if (Export.active) Export.cancel()
		LocalEvents.clearLoops()
		session.reader.close()
		Puppet.end()
		ScreenReplay.end()
		Channels.clearReplayed()
		session.connection.disconnect(Component.literal("Keyframe ended"))
		Minecraft.getInstance().disconnect(next, false)
		stopped()
		AudioCapture.disarm()
	}

	fun pump() {
		val session = session ?: return
		LocalEvents.muted { pump(session) }
	}

	private fun pump(session: Session) {
		if (Minecraft.getInstance().level == null) session.connection.tick()
		ScreenReplay.flush()

		if (session.tick < session.rollTo) {
			if (!canRoll()) return advance(session)
			val deadline = System.nanoTime() + ROLL_BUDGET_NANOS
			while (canRoll() && session.tick < session.rollTo && System.nanoTime() < deadline) advance(session)
			return
		}

		if (settled) LocalEvents.settleLoops()

		if (session.preview) {
			if (session.settle > 0) session.settle--
			val rate = playRate(session)
			if (session.playing) {
				session.carry += session.speed * rate
				val steps = session.carry.toInt()
				session.carry -= steps
				repeat(minOf(steps, session.clip.end - session.tick)) { advance(session) }
				ScreenReplay.tickPreview()
			}
			return
		}

		if (session.export != null) {
			if (!loaded && !inMenu && !session.exporting) return advance(session)
			if (!holdForExport(session)) return stop()
			if (!session.exporting) return
		}
		if (session.tick >= session.clip.end) {
			if (session.export == null) stop()
			return
		}
		advance(session)
	}

	private fun canRoll() = loaded || (rollsInMenu && inMenu)

	private fun holdForExport(session: Session): Boolean {
		if (!session.handedOff) {
			session.handedOff = true
			val ticks = session.clip.end - session.clip.start
			Export.start(session.export!!, exportFrames(ticks, session.export.fps, session.export.speed), session.clip.start)
		}
		if (Export.active) {
			session.exporting = true
			return true
		}
		return !session.exporting && Export.busy
	}

	private fun advance(session: Session) {
		session.tick++
		if (session.tick < session.rollTo && loaded) {
			Minecraft.getInstance().gui.hud.tick(false)
			Puppet.ageWhileRolling()
		}
		while (true) {
			val record = session.pending ?: session.reader.next() ?: return
			if (record.tick > session.tick) {
				session.pending = record
				return
			}
			session.pending = null
			when (record.type) {
				ReplayFile.CONFIG_FRAME -> blockedConfig?.let { blocked -> if (!session.rewound) feed(record) { blocked[it] } }
				ReplayFile.PLAY_FRAME -> feed(record) { skipsPlay(session, it) }
				ReplayFile.SNAPSHOT -> Puppet.onSnapshot(record)
				ReplayFile.EVENT -> LocalEvents.replay(record, session.tick >= session.rollTo)
				ReplayFile.SCREEN_TRANSITION -> ScreenReplay.onTransition(record)
				ReplayFile.SCREEN_STATE -> ScreenReplay.onState(record)
				ReplayFile.CHANNEL -> Channels.onRecord(record)
				ReplayFile.SESSION_BREAK -> crossBreak(session)
			}
		}
	}

	private fun crossBreak(session: Session) {
		LocalEvents.clearLoops()
		session.crossedBreak = true
		session.worldChanged = true
		session.rewound = false
		Puppet.clearSnapshot()
		ScreenReplay.adopt(null)
		session.connection.disconnect(Component.literal("Keyframe session ended"))
		Minecraft.getInstance().disconnect(if (session.preview) EditorScreen() else TitleScreen(), false)
		val (channel, connection) = openChannel(session.reader.header)
		session.channel = channel
		session.connection = connection
	}

	fun onChannelException(cause: Throwable) {
		LOGGER.error("Replayed packet failed", cause)
	}

	private fun skipsPlay(session: Session, id: Int): Boolean {
		if (worldChangePlay[id]) session.worldChanged = true
		return blockedPlay[id] ||
			(session.rewound && loginPlay[id]) ||
			(session.preview && session.tick < session.rollTo && noisyPlay[id])
	}

	private fun feed(record: ReplayRecord, skips: (Int) -> Boolean) {
		try {
			frame(record.payload, skips)?.let { session?.channel?.writeInbound(it) }
		} catch (e: Exception) {
			LOGGER.error("Could not replay frame at tick {}", record.tick, e)
		}
	}

	internal class Session(
		val file: Path,
		var reader: ReplayReader,
		var channel: EmbeddedChannel,
		var connection: Connection,
		val clip: Clip,
		val export: ExportSettings?,
		val preview: Boolean,
	) {
		var rollTo = if (preview) clip.start else clip.start - minOf(clip.start, MAX_PRE_ROLL_TICKS)
		var playing = false
		var speed = 1
		var clipSpeed = 1f
		var carry = 0f
		var slowed = false
		var settle = SETTLE_TICKS
		var tick = 0
		var pending: ReplayRecord? = null
		var handedOff = false
		var exporting = false
		var rewound = false
		var worldChanged = false
		var crossedBreak = false
	}
}
