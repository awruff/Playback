package org.polyfrost.keyframe.standalone.playback

//? if >=1.21.1 {
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.client.resources.sounds.TickableSoundInstance
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.Holder
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import org.slf4j.LoggerFactory
//?}
import org.polyfrost.keyframe.standalone.Standalone
import org.polyfrost.keyframe.standalone.compat.*
import org.polyfrost.keyframe.standalone.recording.Recorder
import org.polyfrost.keyframe.standalone.recording.ReplayRecord
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.IdentityHashMap

private const val SOUND = 0
private const val LEVEL_EVENT = 1
private const val BREAKING = 2
private const val ENTITY_SOUND = 3
private const val UI_SOUND = 4
private const val UI_LOOP_START = 5
private const val UI_LOOP_VOLUME = 6
private const val UI_LOOP_STOP = 7
private const val DESTROY_PROGRESS = 8
private const val BLOCK_STATE = 9

internal const val REPLAY_BREAKER = Int.MIN_VALUE

internal sealed interface LocalEvent

internal data class LocalSound(
	val x: Double,
	val y: Double,
	val z: Double,
	val sound: String,
	val source: String,
	val volume: Float,
	val pitch: Float,
	val seed: Long,
) : LocalEvent

internal data class LocalLevelEvent(val type: Int, val pos: Long, val data: Int) : LocalEvent

internal data class LocalBreaking(val pos: Long, val direction: Int, val playSound: Boolean) : LocalEvent

internal data class LocalDestroyProgress(val pos: Long, val progress: Int) : LocalEvent

internal data class LocalBlockState(val pos: Long, val state: Int, val flags: Int) : LocalEvent

internal data class LocalEntitySound(
	val entity: Int,
	val sound: String,
	val source: String,
	val volume: Float,
	val pitch: Float,
	val seed: Long,
) : LocalEvent

internal data class LocalUiSound(val sound: String, val volume: Float, val pitch: Float) : LocalEvent

internal sealed interface LocalUiLoopEvent : LocalEvent {
	val id: Int
}

internal data class LocalUiLoopStart(
	override val id: Int,
	val sound: String,
	val volume: Float,
	val pitch: Float,
	val looping: Boolean,
) : LocalUiLoopEvent

internal data class LocalUiLoopVolume(override val id: Int, val volume: Float) : LocalUiLoopEvent

internal data class LocalUiLoopStop(override val id: Int) : LocalUiLoopEvent

internal fun encodeEvent(event: LocalEvent): ByteArray {
	val bytes = ByteArrayOutputStream()
	DataOutputStream(bytes).run {
		when (event) {
			is LocalSound -> {
				writeByte(SOUND)
				writeDouble(event.x)
				writeDouble(event.y)
				writeDouble(event.z)
				writeUTF(event.sound)
				writeUTF(event.source)
				writeFloat(event.volume)
				writeFloat(event.pitch)
				writeLong(event.seed)
			}
			is LocalLevelEvent -> {
				writeByte(LEVEL_EVENT)
				writeInt(event.type)
				writeLong(event.pos)
				writeInt(event.data)
			}
			is LocalBreaking -> {
				writeByte(BREAKING)
				writeLong(event.pos)
				writeInt(event.direction)
				writeBoolean(event.playSound)
			}
			is LocalDestroyProgress -> {
				writeByte(DESTROY_PROGRESS)
				writeLong(event.pos)
				writeInt(event.progress)
			}
			is LocalBlockState -> {
				writeByte(BLOCK_STATE)
				writeLong(event.pos)
				writeInt(event.state)
				writeInt(event.flags)
			}
			is LocalEntitySound -> {
				writeByte(ENTITY_SOUND)
				writeInt(event.entity)
				writeUTF(event.sound)
				writeUTF(event.source)
				writeFloat(event.volume)
				writeFloat(event.pitch)
				writeLong(event.seed)
			}
			is LocalUiSound -> {
				writeByte(UI_SOUND)
				writeUTF(event.sound)
				writeFloat(event.volume)
				writeFloat(event.pitch)
			}
			is LocalUiLoopStart -> {
				writeByte(UI_LOOP_START)
				writeInt(event.id)
				writeUTF(event.sound)
				writeFloat(event.volume)
				writeFloat(event.pitch)
				writeBoolean(event.looping)
			}
			is LocalUiLoopVolume -> {
				writeByte(UI_LOOP_VOLUME)
				writeInt(event.id)
				writeFloat(event.volume)
			}
			is LocalUiLoopStop -> {
				writeByte(UI_LOOP_STOP)
				writeInt(event.id)
			}
		}
	}
	return bytes.toByteArray()
}

internal fun decodeEvent(payload: ByteArray): LocalEvent? = DataInputStream(ByteArrayInputStream(payload)).run {
	when (readByte().toInt()) {
		SOUND -> LocalSound(readDouble(), readDouble(), readDouble(), readUTF(), readUTF(), readFloat(), readFloat(), readLong())
		LEVEL_EVENT -> LocalLevelEvent(readInt(), readLong(), readInt())
		BREAKING -> LocalBreaking(readLong(), readInt(), readBoolean())
		ENTITY_SOUND -> LocalEntitySound(readInt(), readUTF(), readUTF(), readFloat(), readFloat(), readLong())
		UI_SOUND -> LocalUiSound(readUTF(), readFloat(), readFloat())
		UI_LOOP_START -> LocalUiLoopStart(readInt(), readUTF(), readFloat(), readFloat(), readBoolean())
		UI_LOOP_VOLUME -> LocalUiLoopVolume(readInt(), readFloat())
		UI_LOOP_STOP -> LocalUiLoopStop(readInt())
		DESTROY_PROGRESS -> LocalDestroyProgress(readLong(), readInt())
		BLOCK_STATE -> LocalBlockState(readLong(), readInt(), readInt())
		else -> null
	}
}

internal class UiLoopWatch<T : Any> {
	private class Watched(val id: Int, var volume: Float)

	private val watched = IdentityHashMap<T, Watched>()
	private var nextId = 0

	fun start(instance: T, volume: Float): Int = watched.getOrPut(instance) { Watched(nextId++, volume) }.id

	fun poll(live: (T) -> Boolean, volume: (T) -> Float): List<LocalEvent> = buildList {
		watched.entries.removeIf { (instance, entry) ->
			val alive = live(instance)
			if (!alive) {
				add(LocalUiLoopStop(entry.id))
			} else {
				val now = volume(instance)
				if (now != entry.volume) {
					entry.volume = now
					add(LocalUiLoopVolume(entry.id, now))
				}
			}
			!alive
		}
	}

	fun clear() = watched.clear()
}

internal class UiLoopPlayer<S : Any>(
	private val start: (LocalUiLoopStart) -> S,
	private val stop: (S) -> Unit,
	private val fade: (S, Float) -> Unit,
) {
	private val wanted = LinkedHashMap<Int, LocalUiLoopStart>()
	private val playing = HashMap<Int, S>()

	fun track(event: LocalUiLoopEvent) {
		when (event) {
			is LocalUiLoopStart -> wanted[event.id] = event
			is LocalUiLoopVolume -> wanted.computeIfPresent(event.id) { _, loop -> loop.copy(volume = event.volume) }
			is LocalUiLoopStop -> wanted.remove(event.id)
		}
	}

	fun settle() {
		playing.entries.removeIf { (id, sound) -> (id !in wanted).also { if (it) stop(sound) } }
		for ((id, loop) in wanted) {
			val sound = playing[id]
			if (sound == null) playing[id] = start(loop) else fade(sound, loop.volume)
		}
	}

	fun clear() {
		playing.values.forEach(stop)
		playing.clear()
		wanted.clear()
	}
}

internal inline fun <T> whileSet(flag: (Boolean) -> Unit, block: () -> T): T {
	flag(true)
	try {
		return block()
	} finally {
		flag(false)
	}
}

//? if >=1.21.1 {
private class ReplayedUiSound(sound: String, volume: Float, pitch: Float, looping: Boolean) :
	AbstractTickableSoundInstance(variableRange(sound), SoundSource.UI, SoundInstance.createUnseededRandom()) {
	init {
		this.volume = volume
		this.pitch = pitch
		this.looping = looping
		this.attenuation = SoundInstance.Attenuation.NONE
		this.relative = true
	}

	override fun getVolume(): Float = this.volume

	override fun getPitch(): Float = this.pitch

	override fun canStartSilent(): Boolean = true

	override fun tick() {}

	fun fade(volume: Float) {
		this.volume = volume
	}

	fun end() = stop()
}

private fun variableRange(sound: String) = SoundEvent.createVariableRangeEvent(Identifier.parse(sound))

private fun play(sound: ReplayedUiSound) = sound.also { Minecraft.getInstance().soundManager.play(it) }

object LocalEvents {
	private val LOGGER = LoggerFactory.getLogger(Standalone.MOD_ID)

	private var fromServer = false

	private var muteUi = false

	@JvmStatic
	fun <T> muted(block: () -> T): T = whileSet({ muteUi = it }, block)

	@JvmStatic
	fun <T> asServer(block: () -> T): T = whileSet({ fromServer = it }, block)

	private val watch = UiLoopWatch<SoundInstance>()
	private val loops = UiLoopPlayer(
		start = { play(ReplayedUiSound(it.sound, it.volume, it.pitch, it.looping)) },
		stop = ReplayedUiSound::end,
		fade = ReplayedUiSound::fade,
	)

	@JvmStatic
	fun sound(x: Double, y: Double, z: Double, sound: SoundEvent, source: SoundSource, volume: Float, pitch: Float, seed: Long) {
		if (!fromServer) write(LocalSound(x, y, z, sound.location().toString(), source.name, volume, pitch, seed))
	}

	@JvmStatic
	fun entitySound(entity: Int, sound: SoundEvent, source: SoundSource, volume: Float, pitch: Float, seed: Long) {
		if (!fromServer) write(LocalEntitySound(entity, sound.location().toString(), source.name, volume, pitch, seed))
	}

	@JvmStatic
	fun uiSound(instance: SoundInstance) {
		if (instance.source != SoundSource.UI || instance.sound == null || !Recorder.writing) return
		val sound = instance.identifier.toString()
		val volume = instance.volume
		if (instance is TickableSoundInstance || instance.isLooping) {
			write(LocalUiLoopStart(watch.start(instance, volume), sound, volume, instance.pitch, instance.isLooping))
		} else {
			write(LocalUiSound(sound, volume, instance.pitch))
		}
	}

	fun pollLoops() {
		val sounds = Minecraft.getInstance().soundManager
		watch.poll({ sounds.isActive(it) && !(it is TickableSoundInstance && it.isStopped) }, { it.volume }).forEach(::write)
	}

	fun forgetLoops() = watch.clear()

	@JvmStatic
	fun mutes(instance: SoundInstance): Boolean = Keyframe.silentExport ||
		Keyframe.active && instance.source == SoundSource.UI && instance !is ReplayedUiSound && (muteUi || !Keyframe.previewing)

	fun settleLoops() = loops.settle()

	fun clearLoops() = loops.clear()

	@JvmStatic
	fun levelEvent(type: Int, pos: BlockPos, data: Int) = write(LocalLevelEvent(type, pos.asLong(), data))

	@JvmStatic
	fun breaking(pos: BlockPos, direction: Direction, playSound: Boolean) =
		write(LocalBreaking(pos.asLong(), direction.get3DDataValue(), playSound))

	@JvmStatic
	fun destroyProgress(pos: BlockPos, progress: Int) = write(LocalDestroyProgress(pos.asLong(), progress))

	@JvmStatic
	fun localBlock(pos: BlockPos, state: BlockState, flags: Int) = write(LocalBlockState(pos.asLong(), Block.getId(state), flags))

	private fun write(event: LocalEvent) = Recorder.writeEvent(encodeEvent(event))

	fun replay(record: ReplayRecord, live: Boolean) {
		try {
			val event = decodeEvent(record.payload)
			if (event is LocalUiLoopEvent) loops.track(event)
			val mc = Minecraft.getInstance()
			if (event is LocalBlockState) {
				mc.level?.setBlock(BlockPos.of(event.pos), Block.stateById(event.state), event.flags)
				return
			}
			if (!live) return
			if (event is LocalUiLoopEvent) return loops.settle()
			if (event is LocalUiSound) {
				play(ReplayedUiSound(event.sound, event.volume, event.pitch, false))
				return
			}
			val level = mc.level ?: return
			when (event) {
				is LocalSound -> level.playSeededSound(
					mc.player,
					event.x,
					event.y,
					event.z,
					Holder.direct(variableRange(event.sound)),
					SoundSource.valueOf(event.source),
					event.volume,
					event.pitch,
					event.seed,
				)
				is LocalEntitySound -> level.getEntity(event.entity)?.let {
					level.playSeededSound(mc.player, it, Holder.direct(variableRange(event.sound)), SoundSource.valueOf(event.source), event.volume, event.pitch, event.seed)
				}
				is LocalLevelEvent -> level.levelEvent(mc.player, event.type, BlockPos.of(event.pos), event.data)
				is LocalDestroyProgress -> level.destroyBlockProgress(REPLAY_BREAKER, BlockPos.of(event.pos), event.progress)
				is LocalBreaking -> level.addBreakingBlockEffects(BlockPos.of(event.pos), Direction.from3DDataValue(event.direction), event.playSound)
				else -> {}
			}
		} catch (e: IOException) {
			LOGGER.error("Could not read local event at tick {}", record.tick, e)
		} catch (e: RuntimeException) {
			LOGGER.error("Could not replay local event at tick {}", record.tick, e)
		}
	}
}
//?}
