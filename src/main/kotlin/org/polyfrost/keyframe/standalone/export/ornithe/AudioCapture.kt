package org.polyfrost.keyframe.standalone.export

import net.minecraft.client.Minecraft
import net.minecraft.client.sound.system.SoundEngine
import org.polyfrost.keyframe.standalone.mixin.ornithe.Mixin_SoundEngine_Audio
import org.polyfrost.keyframe.standalone.mixin.ornithe.Mixin_SoundManager_Audio
import org.polyfrost.keyframe.standalone.mixin.ornithe.Mixin_Source_Audio
import org.lwjgl.openal.AL
import org.lwjgl.openal.AL10
import org.lwjgl.openal.ALC
import org.lwjgl.openal.ALC10
import org.lwjgl.openal.SOFTLoopback
import org.lwjgl.system.MemoryStack
import paulscode.sound.Library
import paulscode.sound.SoundSystemConfig
import paulscode.sound.libraries.ChannelLWJGLOpenAL
import java.nio.IntBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

private const val START_TIMEOUT_MILLIS = 5000L
private const val STREAM_WAIT_NANOS = 250_000_000L
private const val MIN_STREAM_BUFFERS = 2

object AudioCapture {
	@JvmStatic
	var armed = false
		private set

	private var device = 0L
	private var context = 0L

	private var commandQueue: List<*>? = null
	private var library: Library? = null

	@JvmStatic
	var soundFixExecutor: Executor? = null

	@JvmStatic
	fun attach(commandQueue: List<*>?, library: Library?) {
		this.commandQueue = commandQueue
		this.library = library
	}

	private val engine: SoundEngine get() = (Minecraft.getInstance().soundManager as Mixin_SoundManager_Audio).`keyframe$getEngine`()

	fun arm() {
		check(runCatching { Class.forName("org.lwjgl.openal.SOFTLoopback") }.isSuccess) {
			"Exporting audio on 1.8.9 needs Pylon, or turn audio off"
		}
		armed = true
		restartSound()
		if (device == 0L) {
			disarm()
			error("OpenAL could not open a loopback device")
		}
	}

	fun disarm() {
		if (!armed) return
		armed = false
		if (soundFixExecutor != null) device = 0L
		restartSound()
	}

	private fun restartSound() {
		val engine = engine
		engine.reload()
		if (soundFixExecutor != null) return
		val deadline = System.currentTimeMillis() + START_TIMEOUT_MILLIS
		while (!(engine as Mixin_SoundEngine_Audio).`keyframe$isStarted`() && System.currentTimeMillis() < deadline) Thread.sleep(10)
	}

	@JvmStatic
	fun open() {
		val device = openLoopback()
		context = MemoryStack.stackPush().use { ALC10.alcCreateContext(device, attributes(it, it.ints(0))) }
		check(context != 0L) { "alcCreateContext failed" }
		ALC10.alcMakeContextCurrent(context)
		AL.createCapabilities(ALC.createCapabilities(device))
		this.device = device
	}

	@JvmStatic
	fun openSoundFix(): Long = openLoopback().also { device = it }

	@JvmStatic
	fun attributes(stack: MemoryStack, base: IntBuffer): IntBuffer {
		val attributes = stack.mallocInt(base.remaining() + 6)
		attributes.put(SOFTLoopback.ALC_FORMAT_CHANNELS_SOFT).put(SOFTLoopback.ALC_STEREO_SOFT)
		attributes.put(SOFTLoopback.ALC_FORMAT_TYPE_SOFT).put(SOFTLoopback.ALC_FLOAT_SOFT)
		attributes.put(ALC10.ALC_FREQUENCY).put(AUDIO_SAMPLE_RATE.toInt())
		return attributes.put(base).flip()
	}

	private fun openLoopback(): Long {
		val device = SOFTLoopback.alcLoopbackOpenDeviceSOFT(null as CharSequence?)
		check(device != 0L) { "alcLoopbackOpenDeviceSOFT failed" }
		return device
	}

	@JvmStatic
	fun close(): Boolean {
		if (device == 0L) return false
		ALC10.alcMakeContextCurrent(0L)
		ALC10.alcDestroyContext(context)
		ALC10.alcCloseDevice(device)
		AL.setCurrentProcess(null)
		device = 0L
		context = 0L
		return true
	}

	@JvmStatic
	fun tick() {
		if (armed && device != 0L && !Export.active) render(TICK_SAMPLES, null)
	}

	fun render(samples: Int, output: FileChannel?) {
		soundFixExecutor?.let {
			CompletableFuture.runAsync({ renderLoopback(device, samples, output) }, it).get(START_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
			return
		}
		val deadline = System.currentTimeMillis() + START_TIMEOUT_MILLIS
		while (commandQueue?.isEmpty() == false && System.currentTimeMillis() < deadline) Thread.onSpinWait()
		awaitStreams()
		synchronized(SoundSystemConfig.THREAD_SYNC) {
			renderLoopback(device, samples, output)
		}
	}

	private fun awaitStreams() {
		val streams = synchronized(SoundSystemConfig.THREAD_SYNC) {
			library?.sources?.values?.filter { it.toStream && it.playing() }.orEmpty()
		}
		val deadline = System.nanoTime() + STREAM_WAIT_NANOS
		for (stream in streams) {
			val id = (stream.channel as? ChannelLWJGLOpenAL)?.ALSource?.get(0) ?: continue
			val codec = (stream as Mixin_Source_Audio).`keyframe$getCodec`()
			while (buffered(id) < MIN_STREAM_BUFFERS && codec?.endOfStream() == false && System.nanoTime() < deadline) {
				Thread.sleep(1)
			}
		}
	}

	private fun buffered(source: Int) = AL10.alGetSourcei(source, AL10.AL_BUFFERS_QUEUED) - AL10.alGetSourcei(source, AL10.AL_BUFFERS_PROCESSED)
}
