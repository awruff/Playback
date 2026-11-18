package org.polyfrost.keyframe.standalone.recording

import com.mojang.authlib.GameProfile
//? if >=1.21.1 {
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufUtil
//?}
import net.minecraft.SharedConstants
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.network.Connection
//? if >=1.21.1 {
import net.minecraft.network.ConnectionProtocol
import net.minecraft.network.ProtocolInfo
import net.minecraft.network.protocol.PacketFlow
//?}
import org.polyfrost.keyframe.standalone.Channels
import org.polyfrost.keyframe.standalone.Standalone
import org.polyfrost.keyframe.standalone.StandaloneConfig
import org.polyfrost.keyframe.standalone.compat.*
import org.polyfrost.keyframe.standalone.playback.ChangedPayload
import org.polyfrost.keyframe.standalone.playback.LocalEvents
import org.polyfrost.keyframe.standalone.playback.Puppet
import org.polyfrost.keyframe.standalone.playback.ScreenReplay
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime

object Recorder {
	private val LOGGER = LoggerFactory.getLogger(Standalone.MOD_ID)

	val directory: Path
		get() = Minecraft.getInstance().gameDirectory.toPath().resolve("keyframe/recordings")

	val clipsDirectory: Path
		get() = directory.resolveSibling("clips")

	private const val CLIP_SUFFIX = ".clip.${ReplayFile.EXTENSION}"

	fun replayFile(name: String): Path =
		if (name.endsWith(CLIP_SUFFIX)) clipsDirectory.resolve(name) else directory.resolve(name)

	@Volatile
	private var writer: ReplayWriter? = null

	@Volatile
	var currentTick = 0
		private set

	@Volatile
	private var pendingClips = emptyList<Clip>()

	private var connection: Connection? = null
	private var path: Path? = null
	private var protocol = 0
	private var hasRecording = false
	private var lastClipTick = -1
	private val screenStates = ChangedPayload()

	var recording = false
		private set

	val writing: Boolean
		get() = writer != null

	@Synchronized
	fun open(connection: Connection, profile: GameProfile) {
		this.connection?.let(::close)
		if (writer != null) {
			this.connection = connection
			return
		}
		val version = SharedConstants.getCurrentVersion()
		val header = ReplayHeader(
			//? if >=1.21.10 {
			version.protocolVersion(),
			version.name(),
			profile.id(),
			profile.name(),
			//?} elif >=1.21.8 {
			/*version.protocolVersion(),
			version.name(),
			profile.id,
			profile.name,
			*///?} else {
			/*version.protocolVersion,
			version.name,
			profile.id,
			profile.name,
			*///?}
			connection.isMemoryConnection,
		)
		try {
			Files.createDirectories(directory)
			val path = directory.resolve("${FILE_STAMP.format(LocalDateTime.now())}.${ReplayFile.EXTENSION}")
			val writer = ReplayWriter(path, header)
			this.path = path
			this.connection = connection
			protocol = header.protocol
			currentTick = 0
			hasRecording = false
			lastClipTick = -1
			Channels.resetWritten()
			this.writer = writer
		} catch (e: IOException) {
			LOGGER.error("Could not open recording", e)
		}
	}

	//? if >=1.21.1 {
	fun tap(protocol: ProtocolInfo<*>, frame: ByteBuf) {
		val writer = writer ?: return
		if (protocol.flow() != PacketFlow.CLIENTBOUND) return
		val type = when (protocol.id()) {
			ConnectionProtocol.CONFIGURATION -> ReplayFile.CONFIG_FRAME
			ConnectionProtocol.PLAY -> ReplayFile.PLAY_FRAME
			else -> return
		}
		writer.write(type, currentTick, ByteBufUtil.getBytes(frame))
	}
	//?} else {
	/*fun tap(frame: ByteArray) {
		writer?.write(ReplayFile.PLAY_FRAME, currentTick, frame)
	}
	*///?}

	fun tick() {
		if (writer == null) return
		LocalEvents.pollLoops()
		currentTick++
		if (pendingClips.isNotEmpty()) writeDueClips()
		val target = writer
		Thumbnails.tick(currentTick) { if (writer === target) target?.write(ReplayFile.THUMBNAIL, currentTick, it) }
	}

	fun writeSnapshot() {
		val writer = writer ?: return
		Puppet.sampleSnapshot(writer)?.let { writer.write(ReplayFile.SNAPSHOT, currentTick, it) }
		val state = ScreenReplay.sampleState()
		if (screenStates.changed(state)) writer.write(ReplayFile.SCREEN_STATE, currentTick, state)
		Channels.sample { writer.write(ReplayFile.CHANNEL, currentTick, it) }
	}

	fun writeEvent(payload: ByteArray) {
		writer?.write(ReplayFile.EVENT, currentTick, payload)
	}

	fun writeCamera(deltaTracker: DeltaTracker) {
		val writer = writer ?: return
		Puppet.sampleCamera(deltaTracker)?.let { writer.write(ReplayFile.CAMERA, currentTick, it) }
	}

	fun writeCursor(deltaTracker: DeltaTracker) {
		writer?.write(ReplayFile.INPUT, currentTick, ScreenReplay.sampleCursor(deltaTracker))
	}

	fun writeScreen() {
		val writer = writer ?: return
		val name = Minecraft.getInstance().gui.screen()?.javaClass?.name ?: ""
		writer.write(ReplayFile.SCREEN_TRANSITION, currentTick, name.toByteArray(Charsets.UTF_8))
	}

	fun writeHighlight(colour: Int, description: String) {
		writer?.write(ReplayFile.HIGHLIGHT, currentTick, encodeHighlight(colour, description))
	}

	@Synchronized
	fun marker(kind: Byte) {
		val writer = writer ?: return
		hasRecording = true
		writer.write(ReplayFile.MARKER, currentTick, byteArrayOf(kind))
	}

	@Synchronized
	fun toggle() {
		if (writer == null) return
		recording = !recording
		marker(if (recording) ReplayFile.MARKER_START else ReplayFile.MARKER_STOP)
		if (!recording && connection == null) finish()
	}

	@Synchronized
	fun requestClip(start: Int, end: Int): Boolean {
		if (writer == null || end <= start) return false
		pendingClips = mergeClip(pendingClips, Clip(start, end))
		writeDueClips()
		return true
	}

	@Synchronized
	private fun writeDueClips() {
		val (due, held) = pendingClips.partition { it.end <= currentTick }
		pendingClips = held
		due.forEach(::writeClip)
	}

	private fun writeClip(clip: Clip) {
		val writer = writer ?: return
		lastClipTick = currentTick
		writer.write(ReplayFile.CLIP, currentTick, encodeClip(clip))
		if (writer.failure == null) ShadowReplay.announceSaved()
	}

	private fun writeHeldClips() {
		clipsAtClose(pendingClips, currentTick).forEach(::writeClip)
		pendingClips = emptyList()
	}

	@Synchronized
	fun close(connection: Connection) {
		if (connection !== this.connection) return
		if (recording && StandaloneConfig.recordMainMenu) detach() else finish()
	}

	private fun detach() {
		writeHeldClips()
		connection = null
		Puppet.resetSampling()
		screenStates.reset()
		Channels.resetWritten()
		writer?.write(ReplayFile.SESSION_BREAK, currentTick, ByteArray(0))
	}

	@Synchronized
	fun finish() {
		val writer = writer ?: return
		if (recording) marker(ReplayFile.MARKER_STOP)
		recording = false
		writeHeldClips()
		this.writer = null
		this.connection = null
		screenStates.reset()
		Channels.resetWritten()
		LocalEvents.forgetLoops()
		writer.close()
		writer.failure?.let { LOGGER.error("Recording stopped early", it) }
		try {
			split(path!!)
		} catch (e: IOException) {
			LOGGER.warn("Could not sort {} into clips and recordings", path, e)
		}
	}

	private fun split(path: Path) {
		val clips = clipsDirectory.resolve(path.fileName.toString().removeSuffix(".${ReplayFile.EXTENSION}") + CLIP_SUFFIX)
		when {
			lastClipTick < 0 && !hasRecording -> Files.deleteIfExists(path)
			lastClipTick < 0 -> {}
			!hasRecording -> {
				Files.createDirectories(clipsDirectory)
				Files.move(path, clips)
			}
			else -> {
				Files.createDirectories(clipsDirectory)
				copyRecords(path, clips, protocol) { it.type != ReplayFile.MARKER && it.tick <= lastClipTick }
				copyRecords(path, path, protocol) { it.type != ReplayFile.CLIP }
			}
		}
	}
}
