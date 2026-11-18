@file:JvmName("OrnitheLocalEvents")

package org.polyfrost.keyframe.standalone.playback

import net.minecraft.block.Block
import net.minecraft.block.state.BlockState
import net.minecraft.client.Minecraft
import net.minecraft.client.sound.SoundCategory
import net.minecraft.client.sound.instance.AbstractTickableSoundInstance
import net.minecraft.client.sound.instance.SoundInstance
import net.minecraft.client.sound.instance.TickableSoundInstance
import net.minecraft.resources.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import org.polyfrost.keyframe.standalone.Standalone
import org.polyfrost.keyframe.standalone.recording.Recorder
import org.polyfrost.keyframe.standalone.recording.ReplayRecord
import org.slf4j.LoggerFactory
import java.io.IOException

private class ReplayedUiSound(sound: String, volume: Float, pitch: Float, looping: Boolean) : AbstractTickableSoundInstance(Identifier(sound)) {
	init {
		this.volume = volume
		this.pitch = pitch
		this.looping = looping
		this.attenuation = SoundInstance.Attenuation.NONE
	}

	override fun tick() {}

	fun fade(volume: Float) {
		this.volume = volume
	}

	fun end() {
		done = true
	}
}

private fun play(sound: ReplayedUiSound) = sound.also { Minecraft.getInstance().soundManager.play(it) }

private fun isUi(instance: SoundInstance) = Minecraft.getInstance().soundManager.get(instance.location)?.category == SoundCategory.MASTER

object LocalEvents {
	private val LOGGER = LoggerFactory.getLogger(Standalone.MOD_ID)

	private var muteUi = false

	@JvmStatic
	fun <T> muted(block: () -> T): T = whileSet({ muteUi = it }, block)

	private val watch = UiLoopWatch<SoundInstance>()
	private val loops = UiLoopPlayer(
		start = { play(ReplayedUiSound(it.sound, it.volume, it.pitch, it.looping)) },
		stop = ReplayedUiSound::end,
		fade = ReplayedUiSound::fade,
	)

	@JvmStatic
	fun sound(x: Double, y: Double, z: Double, sound: String, volume: Float, pitch: Float) =
		write(LocalSound(x, y, z, sound, "", volume, pitch, 0L))

	@JvmStatic
	fun uiSound(instance: SoundInstance) {
		if (!Recorder.writing || !isUi(instance)) return
		val sound = instance.location.toString()
		val volume = instance.volume
		if (instance is TickableSoundInstance || instance.isLooping) {
			write(LocalUiLoopStart(watch.start(instance, volume), sound, volume, instance.pitch, instance.isLooping))
		} else {
			write(LocalUiSound(sound, volume, instance.pitch))
		}
	}

	fun pollLoops() {
		val sounds = Minecraft.getInstance().soundManager
		watch.poll({ sounds.isPlaying(it) && !(it is TickableSoundInstance && it.isStopped) }, { it.volume }).forEach(::write)
	}

	fun forgetLoops() = watch.clear()

	@JvmStatic
	fun mutes(instance: SoundInstance): Boolean = Keyframe.silentExport ||
		Keyframe.active && instance !is ReplayedUiSound && isUi(instance) && (muteUi || !Keyframe.previewing)

	fun settleLoops() = loops.settle()

	fun clearLoops() = loops.clear()

	@JvmStatic
	fun levelEvent(type: Int, pos: BlockPos, data: Int) = write(LocalLevelEvent(type, pos.toLong(), data))

	@JvmStatic
	fun breaking(pos: BlockPos, direction: Direction) = write(LocalBreaking(pos.toLong(), direction.id, false))

	@JvmStatic
	fun destroyProgress(pos: BlockPos, progress: Int) = write(LocalDestroyProgress(pos.toLong(), progress))

	private var fromPacket = false

	@JvmStatic
	fun <T> asPacket(block: () -> T): T = whileSet({ fromPacket = it }, block)

	@JvmStatic
	fun localBlock(pos: BlockPos, state: BlockState, flags: Int) {
		if (!fromPacket) write(LocalBlockState(pos.toLong(), Block.serialize(state), flags))
	}

	private fun write(event: LocalEvent) = Recorder.writeEvent(encodeEvent(event))

	fun replay(record: ReplayRecord, live: Boolean) {
		try {
			val event = decodeEvent(record.payload)
			if (event is LocalUiLoopEvent) loops.track(event)
			val mc = Minecraft.getInstance()
			if (event is LocalBlockState) {
				mc.world?.setBlockState(BlockPos.fromLong(event.pos), Block.deserialize(event.state), event.flags)
				return
			}
			if (!live) return
			if (event is LocalUiLoopEvent) return loops.settle()
			if (event is LocalUiSound) {
				play(ReplayedUiSound(event.sound, event.volume, event.pitch, false))
				return
			}
			val world = mc.world ?: return
			when (event) {
				is LocalSound -> world.playSound(event.x, event.y, event.z, event.sound, event.volume, event.pitch, false)
				is LocalLevelEvent -> world.doEvent(mc.player, event.type, BlockPos.fromLong(event.pos), event.data)
				is LocalDestroyProgress -> world.updateBlockMiningProgress(REPLAY_BREAKER, BlockPos.fromLong(event.pos), event.progress)
				is LocalBreaking -> mc.particleManager.addBlockMiningParticles(BlockPos.fromLong(event.pos), Direction.byId(event.direction))
				else -> {}
			}
		} catch (e: IOException) {
			LOGGER.error("Could not read local event at tick {}", record.tick, e)
		} catch (e: RuntimeException) {
			LOGGER.error("Could not replay local event at tick {}", record.tick, e)
		}
	}
}
