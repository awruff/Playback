package org.polyfrost.keyframe.standalone.export

import net.minecraft.client.Minecraft
import net.minecraft.client.sounds.SoundEngineExecutor
import org.lwjgl.openal.ALC10
import org.lwjgl.openal.SOFTLoopback
import org.lwjgl.system.MemoryStack
import java.nio.IntBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

private const val SOUND_THREAD_TIMEOUT_NANOS = 10_000_000_000L

object AudioCapture {
	@JvmStatic
	var armed = false
		private set

	@JvmStatic
	var switching = false
		private set

	@JvmStatic
	lateinit var executor: SoundEngineExecutor

	private var device = 0L
	private val loads = ArrayDeque<CompletableFuture<*>>()

	fun arm() {
		armed = true
		reload()
		if (device == 0L) {
			disarm()
			error("OpenAL could not open a loopback device")
		}
	}

	fun disarm() {
		if (!armed) return
		armed = false
		device = 0L
		loads.clear()
		reload()
	}

	private fun reload() {
		switching = true
		try {
			Minecraft.getInstance().soundManager.reload()
			runOnce(executor, SOUND_THREAD_TIMEOUT_NANOS) {}
		} finally {
			switching = false
		}
	}

	@JvmStatic
	fun openDevice(): Long {
		device = SOFTLoopback.alcLoopbackOpenDeviceSOFT(null as CharSequence?)
		check(device != 0L) { "alcLoopbackOpenDeviceSOFT failed" }
		return device
	}

	@JvmStatic
	fun attributes(stack: MemoryStack, base: IntBuffer): IntBuffer {
		val attributes = stack.mallocInt(base.remaining() + 6)
		attributes.put(SOFTLoopback.ALC_FORMAT_CHANNELS_SOFT).put(SOFTLoopback.ALC_STEREO_SOFT)
		attributes.put(SOFTLoopback.ALC_FORMAT_TYPE_SOFT).put(SOFTLoopback.ALC_FLOAT_SOFT)
		attributes.put(ALC10.ALC_FREQUENCY).put(AUDIO_SAMPLE_RATE.toInt())
		return attributes.put(base).flip()
	}

	@JvmStatic
	fun trackLoad(load: CompletableFuture<*>): CompletableFuture<*> {
		if (armed) loads.addLast(load)
		return load
	}

	@JvmStatic
	fun tick() {
		if (armed && device != 0L && !Export.active) render(TICK_SAMPLES, null)
	}

	fun render(samples: Int, output: FileChannel?) {
		loads.forEach { runCatching { it.join() } }
		loads.clear()
		runOnce(executor, SOUND_THREAD_TIMEOUT_NANOS) {
			renderLoopback(device, samples, output)
		}
	}
}

internal fun runOnce(executor: Executor, timeoutNanos: Long, task: () -> Unit) {
	val claimed = AtomicBoolean()
	val done = CompletableFuture<Unit>()
	val run = Runnable {
		if (claimed.compareAndSet(false, true)) {
			try {
				task()
				done.complete(Unit)
			} catch (e: Throwable) {
				done.completeExceptionally(e)
			}
		}
	}
	val deadline = System.nanoTime() + timeoutNanos
	while (true) {
		if (!claimed.get()) executor.execute(run)
		try {
			return done.get(50, TimeUnit.MILLISECONDS)
		} catch (_: TimeoutException) {
			check(claimed.get() || System.nanoTime() < deadline) { "The sound thread stopped running tasks" }
		} catch (e: ExecutionException) {
			throw e.cause ?: e
		}
	}
}
