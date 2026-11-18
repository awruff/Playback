package org.polyfrost.keyframe.standalone.export

import com.mojang.blaze3d.systems.RenderSystem
//? if >=1.21.8 {
import com.mojang.renderpearl.api.buffers.GpuBuffer
import com.mojang.renderpearl.api.commands.GpuFence
//?}
//? if >=26.2
import com.mojang.renderpearl.api.textures.GpuTextureView
//? if >=1.21.1
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import net.minecraft.util.TimeSource
import net.minecraft.util.Util
//? if >=1.21.8 {
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher
import org.polyfrost.keyframe.standalone.mixin.Mixin_LevelRenderer_Export
import org.polyfrost.keyframe.standalone.mixin.Mixin_SectionTask_Export
//?}
import org.polyfrost.keyframe.standalone.StandaloneConfig
import org.polyfrost.keyframe.standalone.compat.*
import org.polyfrost.keyframe.standalone.playback.Keyframe
import org.polyfrost.oneconfig.api.notifications.v1.Notifications
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.math.ceil

internal val ffmpegInstallRoot: Path get() = Minecraft.getInstance().gameDirectory.toPath().resolve("keyframe/ffmpeg")

private const val NANOS_PER_SECOND = 1_000_000_000L
private const val MIN_NANOS_PER_TICK = 50_000_000L
private const val READBACK_SLOTS = 6
private const val WRITE_CHUNK = 1 shl 20
internal const val MAX_PRE_ROLL_TICKS = 40

internal class TickSchedule(private val fps: Int) {
	private var residualTimesFps = 0L
	private var tickTimesFps = MIN_NANOS_PER_TICK * fps

	fun step(nanosPerTick: Long, speed: Float = 1f): Int {
		tickTimesFps = (maxOf(nanosPerTick, MIN_NANOS_PER_TICK) / speed.toDouble()).toLong() * fps
		residualTimesFps += NANOS_PER_SECOND
		val ticks = minOf(residualTimesFps / tickTimesFps, ceil(speed.toDouble()).toLong()).toInt()
		if (ticks == 0) return 0
		residualTimesFps = minOf(residualTimesFps - ticks * tickTimesFps, tickTimesFps - 1)
		return ticks
	}

	fun partial(): Float = residualTimesFps.toFloat() / tickTimesFps
}

internal fun frameNanos(frame: Long, fps: Int): Long = frame * NANOS_PER_SECOND / fps

internal fun audioSamples(frame: Long, fps: Int): Int =
	((frame + 1) * AUDIO_SAMPLE_RATE / fps - frame * AUDIO_SAMPLE_RATE / fps).toInt()

internal fun preRollFrames(startTick: Int, fps: Int): Int = minOf(startTick, MAX_PRE_ROLL_TICKS) * fps / 20

object Export {
	private val LOGGER = LoggerFactory.getLogger("Keyframe")
	private val READINESS_TIMEOUT = TimeUnit.SECONDS.toNanos(30)
	private val STATUS_INTERVAL = TimeUnit.MILLISECONDS.toNanos(500)
	private val sodium = FabricLoader.getInstance().isModLoaded("sodium")

	//? if >=1.21.8 {
	private val LAST_COMPILE_TASK = SectionRenderDispatcher.RenderSection::class.java.declaredFields
		//? if >=26.2 {
		.first { SectionRenderDispatcher.RenderSection.SectionTask::class.java.isAssignableFrom(it.type) }
		//?} else
		/*.first { SectionRenderDispatcher.RenderSection.CompileTask::class.java.isAssignableFrom(it.type) }*/
		.apply { isAccessible = true }
	//?}

	private var session: Session? = null

	@Volatile
	var busy = false
		private set

	@Volatile
	private var child: Process? = null

	init {
		Runtime.getRuntime().addShutdownHook(Thread { child?.destroyForcibly() })
		//? if >=1.21.1
		ClientLifecycleEvents.CLIENT_STOPPING.register { cancel() }
	}

	@JvmStatic
	val active: Boolean get() = session != null

	fun start(settings: ExportSettings, totalFrames: Int, startTick: Int) {
		val problem = if (busy) "an export is already running" else problem(settings, totalFrames)
		if (problem != null) {
			report("Export failed: $problem", false)
			return
		}
		busy = true

		val mc = Minecraft.getInstance()
		val nv12 = if (settings.colorFormat == ColorFormat.YUV420) {
			Nv12Converter.create(settings.width, settings.height, mc.gameRenderer.mainRenderTarget().colorTexture!!)
		} else {
			null
		}
		thread(name = "Keyframe Export", isDaemon = true) {
			try {
				var shown = -1
				val ffmpeg = Ffmpeg.resolve(settings.ffmpegPath, ffmpegInstallRoot) { progress ->
					val percent = (progress * 100).toInt()
					if (percent != shown) {
						shown = percent
						mc.execute { mc.window.setTitle("Keyframe: installing ffmpeg $percent%") }
					}
				}
				Files.createDirectories(settings.outputDir)
				val session = Session(settings.withBestEncoder(ffmpeg), totalFrames, startTick, ffmpeg, nv12)
				mc.execute { begin(session) }
			} catch (e: Exception) {
				mc.execute { nv12?.close() }
				done("Export failed: ${e.message ?: e}", false)
			} catch (e: OutOfMemoryError) {
				mc.execute { nv12?.close() }
				done("Export failed: not enough memory for ${settings.width}x${settings.height}", false)
			}
		}
	}

	fun cancel() {
		val session = session
		if (session == null) {
			child?.destroyForcibly()
			return
		}
		stopWriter(session)
		restore(session)
		discard(session)
		done("Export cancelled", false)
	}

	@JvmStatic
	fun beginFrame(): Int {
		val session = session ?: return 0
		val mc = Minecraft.getInstance()
		if (mc.level == null && !Keyframe.inMenu) {
			abort(session, "the world was unloaded")
			return 0
		}

		val window = mc.window
		if (window.width != session.settings.width || window.height != session.settings.height) {
			window.width = session.settings.width
			window.height = session.settings.height
			mc.resizeGui()
		}

		session.armed = true
		session.readyBefore = ready()
		return session.pendingTicks.also { session.pendingTicks = 0 }
	}

	@JvmStatic
	fun endFrame() {
		val session = session ?: return
		if (!session.armed) return
		session.armed = false
		advance(session)
		if (this.session === session) refreshStatus(session)
	}

	private fun advance(session: Session) {
		session.waiting = false
		if (session.frame < 0) {
			session.lastReady = System.nanoTime()
		} else {
			val ready = session.readyBefore && ready()
			if (!ready && !waitedOut(session)) {
				session.waiting = true
				return
			}

			try {
				capture(session)
			} catch (e: Exception) {
				abort(session, e.message ?: e.toString())
				return
			}

			if (ready) {
				session.lastReady = System.nanoTime()
				session.warned = false
			}
		}

		if (session.settings.audio) {
			try {
				AudioCapture.render(audioSamples(session.frame.toLong(), session.settings.fps), session.pcmOut.takeIf { session.frame >= 0 })
			} catch (e: Exception) {
				abort(session, e.message ?: e.toString())
				return
			}
		}

		session.frame++
		if (session.frame == session.totalFrames) {
			try {
				RenderSystem.getDevice().createCommandEncoder().submit()
				collect(session, Int.MAX_VALUE)
				session.writer.join()
			} catch (e: Exception) {
				abort(session, e.message ?: e.toString())
				return
			}
			restore(session)
			thread(name = "Keyframe Export", isDaemon = true) { encode(session) }
			return
		}

		session.pendingTicks = session.schedule.step(nanosPerTick(), if (session.frame >= 0) session.settings.speed else 1f)
		session.now = session.origin + frameNanos((session.frame - session.firstFrame).toLong(), session.settings.fps)
	}

	//? if >=1.21.8 {
	@JvmStatic
	fun completeSyncTask(section: SectionRenderDispatcher.RenderSection) {
		(LAST_COMPILE_TASK.get(section) as Mixin_SectionTask_Export?)?.`keyframe$getIsCompleted`()?.set(true)
	}
	//?}

	@JvmStatic
	fun partialTick(): Float = session?.schedule?.partial() ?: 0f

	@JvmStatic
	fun acquireSurface(): Boolean {
		val session = session ?: return true
		return session.previewDirty.also { session.previewDirty = false }
	}

	//? if >=26.2 {
	@JvmStatic
	fun surfaceView(view: GpuTextureView): GpuTextureView = session?.preview?.view ?: view
	//?} else {
	/*
	@JvmStatic
	fun presentPreview(): Boolean {
		val view = session?.preview?.view ?: return true
		RenderSystem.getDevice().createCommandEncoder().presentTexture(view)
		return false
	}
	*///?}

	fun titleSeconds(): Float? = session?.takeIf { it.frame >= 0 }?.let { it.frame.toFloat() / it.settings.fps }

	val titles: List<TitleSpan> get() = session?.settings?.titles.orEmpty()

	@JvmStatic
	fun renderHud(): Boolean = session?.settings?.hud ?: (!Keyframe.previewing || StandaloneConfig.hud)

	private fun problem(settings: ExportSettings, totalFrames: Int): String? {
		val maxSize = maxTextureSize()
		return when {
			Minecraft.getInstance().level == null && !Keyframe.inMenu -> "no world is loaded"
			totalFrames <= 0 -> "nothing to export"
			settings.fps < 20 -> "the frame rate must be at least 20 fps, got ${settings.fps}"
			settings.width <= 0 || settings.height <= 0 -> "invalid size ${settings.width}x${settings.height}"
			settings.width % 2 != 0 || settings.height % 2 != 0 -> "width and height must be even, got ${settings.width}x${settings.height}"
			settings.width > maxSize || settings.height > maxSize -> "this GPU supports at most ${maxSize}x$maxSize, got ${settings.width}x${settings.height}"
			settings.width.toLong() * settings.height * 4 > Int.MAX_VALUE -> "${settings.width}x${settings.height} is too large to read back"
			else -> backBufferProblem(settings.width, settings.height) ?: formatProblem(settings.encoder, settings.container, settings.colorFormat)
		}
	}

	private fun begin(session: Session) {
		val mc = Minecraft.getInstance()
		if (mc.level == null && !Keyframe.inMenu) {
			discard(session)
			done("Export failed: the world was unloaded", false)
			return
		}

		val settings = session.settings
		try {
			resizeRenderTargets(settings.width, settings.height)
			repeat(READBACK_SLOTS) {
				session.slots += Readback(RenderSystem.getDevice().createBuffer(
					{ "Keyframe readback" },
					GpuBuffer.USAGE_MAP_READ or GpuBuffer.USAGE_COPY_DST,
					//? if >=1.21.11 {
					session.frameBytes.toLong(),
					//?} else
					/*session.frameBytes,*/
				))
			}
			session.idle += session.slots
		} catch (e: RuntimeException) {
			LOGGER.warn("Could not allocate export targets", e)
			closeReadbacks(session)
			restoreSize()
			discard(session)
			done("Export failed: not enough GPU memory for ${settings.width}x${settings.height}", false)
			return
		}

		//? if >=1.21.11 {
		val fadeIn = mc.options.chunkSectionFadeInTime()
		session.fadeIn = fadeIn.get()
		fadeIn.set(0.0)
		//?}

		session.origin = Util.getNanos()
		session.now = session.origin
		setTimeSource(TimeSource.NanoTimeSource { session.now })

		session.lastReady = System.nanoTime()
		session.statusTime = session.lastReady
		session.startTime = session.lastReady
		session.writer.start()
		this.session = session
	}

	private fun refreshStatus(session: Session) {
		val now = System.nanoTime()
		val elapsed = now - session.statusTime
		if (elapsed < STATUS_INTERVAL) return

		val lines = when {
			session.frame < 0 -> listOf("Preparing: ${-session.frame} pre-roll frames left")
			session.waiting -> listOf("Waiting for chunks at frame ${session.frame}/${session.totalFrames}")
			else -> statusLines(session)
		}
		session.preview.draw(session.pixels, session.settings.width, session.settings.height, session.previewFrame, session.nv12 != null, lines)
		session.previewDirty = true
		session.previewDue = session.settings.preview
		Minecraft.getInstance().window.setTitle("Keyframe: ${lines.joinToString(", ").take(100)}")

		session.statusTime = now
		session.statusFrames = session.captured
		session.totalReadbackNanos += session.readbackNanos
		session.totalFfmpegNanos += session.ffmpegNanos
		session.readbackNanos = 0
		session.ffmpegNanos = 0
	}

	private fun statusLines(session: Session): List<String> {
		val now = System.nanoTime()
		val frames = session.captured - session.statusFrames
		val fps = frames * NANOS_PER_SECOND.toDouble() / (now - session.statusTime)
		val average = session.captured * NANOS_PER_SECOND.toDouble() / maxOf(now - session.firstCapture, 1L)
		val perFrame = maxOf(frames, 1).toDouble() * 1_000_000
		val readback = session.readbackNanos / perFrame
		val ffmpeg = session.ffmpegNanos / perFrame
		val render = maxOf((now - session.statusTime) / perFrame - readback - ffmpeg, 0.0)
		return statusLines(session.frame, session.totalFrames, fps, average, render, readback, ffmpeg)
	}

	private fun nanosPerTick(): Long {
		val tickRate = Minecraft.getInstance().level?.tickRateManager()
		return if (tickRate != null && tickRate.runsNormally()) tickRate.nanosecondsPerTick() else MIN_NANOS_PER_TICK
	}

	private fun ready(): Boolean {
		if (Minecraft.getInstance().level == null) return true
		val levelRenderer = Minecraft.getInstance().levelRenderer
		if (!levelRenderer.hasRenderedAllSections()) return false
		if (sodium) return true
		//? if >=1.21.8 {
		return (levelRenderer as Mixin_LevelRenderer_Export).`keyframe$getVisibleSections`().none { section ->
			val task = LAST_COMPILE_TASK.get(section) as Mixin_SectionTask_Export?
			task != null && !task.`keyframe$getIsCompleted`().get() && !task.`keyframe$getIsCancelled`().get()
		}
		//?} else
		/*return true*/
	}

	private fun waitedOut(session: Session): Boolean {
		if (System.nanoTime() - session.lastReady < READINESS_TIMEOUT) return false
		if (!session.warned) {
			LOGGER.warn("World still not ready after 30 s at frame {}, capturing anyway", session.frame)
			session.warned = true
		}
		return true
	}

	private fun capture(session: Session) {
		session.writeFailure?.let { throw it }
		val start = System.nanoTime()
		if (session.captured == 0) session.firstCapture = start
		reclaim(session, false)
		var waited = 0L
		if (session.idle.isEmpty()) {
			if (session.inFlight.size == session.slots.size) {
				collect(session, 1)
			} else {
				val waitStart = System.nanoTime()
				reclaim(session, true)
				waited = System.nanoTime() - waitStart
				session.ffmpegNanos += waited
			}
		}

		val slot = session.idle.removeFirst()
		val encoder = RenderSystem.getDevice().createCommandEncoder()
		val texture = Minecraft.getInstance().gameRenderer.mainRenderTarget().colorTexture!!
		if (session.nv12 != null) {
			session.nv12.convert(texture, slot.buffer)
		} else {
			//? if >=1.21.11 {
			encoder.copyTextureToBuffer(texture, slot.buffer, 0L, Runnable {}, 0)
			//?} else
			/*encoder.copyTextureToBuffer(texture, slot.buffer, 0, Runnable {}, 0)*/
		}
		slot.fence = encoder.createFence()
		session.inFlight.addLast(slot)
		session.readbackNanos += System.nanoTime() - start - waited
		collect(session, 0)
	}

	private fun collect(session: Session, blockingWaits: Int) {
		var waits = blockingWaits
		while (true) {
			val slot = session.inFlight.firstOrNull() ?: return
			val start = System.nanoTime()
			val fence = slot.fence!!
			if (!fence.awaitCompletion(if (waits-- > 0) FENCE_NO_TIMEOUT else 0L)) return
			val view = slot.buffer.map(true, false)
			slot.view = view
			slot.data = view.data()
			fence.close()
			slot.fence = null
			session.inFlight.removeFirst()
			if (session.previewDue) {
				slot.data!!.get(0, session.pixels, 0, session.frameBytes)
				session.previewDue = false
				session.previewFrame = true
			}
			session.queue.add(slot)
			session.captured++
			session.readbackNanos += System.nanoTime() - start
		}
	}

	private fun reclaim(session: Session, wait: Boolean) {
		var slot = if (wait) awaitWritten(session) else session.written.poll()
		while (slot != null) {
			slot.view!!.close()
			slot.view = null
			slot.data = null
			session.idle.addLast(slot)
			slot = session.written.poll()
		}
	}

	private fun awaitWritten(session: Session): Readback {
		while (true) {
			session.writeFailure?.let { throw it }
			session.written.poll(100, TimeUnit.MILLISECONDS)?.let { return it }
		}
	}

	private fun writeFrames(session: Session) {
		try {
			val stdin = session.process.outputStream
			val chunk = ByteArray(WRITE_CHUNK)
			repeat(session.totalFrames) {
				val slot = session.queue.take()
				val data = slot.data!!.duplicate().clear()
				while (data.hasRemaining()) {
					val length = minOf(chunk.size, data.remaining())
					data.get(chunk, 0, length)
					stdin.write(chunk, 0, length)
				}
				session.written.put(slot)
			}
		} catch (_: InterruptedException) {
		} catch (e: IOException) {
			session.writeFailure = e
		}
	}

	private fun encode(session: Session) {
		try {
			session.writer.join()
			session.writeFailure?.let { throw it }
			session.process.outputStream.close()
			val videoExit = session.process.waitFor()
			if (videoExit != 0) {
				failed(session, "ffmpeg exited with code $videoExit")
				return
			}

			session.pcmOut?.close()
			val pcm = session.pcm.takeIf { session.settings.audio }
			val mux = launch(Ffmpeg.muxCommand(session.ffmpeg, session.video, pcm, session.settings, session.muxed, session.totalFrames), session.log)
			mux.outputStream.close()
			val muxExit = mux.waitFor()
			if (muxExit != 0) {
				failed(session, "ffmpeg exited with code $muxExit")
				return
			}

			Files.move(session.muxed, session.output, StandardCopyOption.ATOMIC_MOVE)
			Files.deleteIfExists(session.video)
			Files.deleteIfExists(session.pcm)
			Files.deleteIfExists(session.log)
			val seconds = (session.endTime - session.startTime) / NANOS_PER_SECOND.toDouble()
			LOGGER.info(
				"Rendered {} frames in {} s, {} fps: readback {} ms, ffmpeg wait {} ms per frame",
				session.totalFrames,
				"%.1f".format(seconds),
				"%.1f".format(session.totalFrames / seconds),
				"%.1f".format(session.totalReadbackNanos / 1e6 / session.totalFrames),
				"%.1f".format(session.totalFfmpegNanos / 1e6 / session.totalFrames),
			)
			done("Exported ${session.output.fileName}", true)
		} catch (e: IOException) {
			failed(session, e.message ?: e.toString())
		}
	}

	private fun abort(session: Session, reason: String) {
		stopWriter(session)
		restore(session)
		thread(name = "Keyframe Export", isDaemon = true) {
			session.process.waitFor()
			failed(session, reason)
		}
	}

	private fun failed(session: Session, reason: String) {
		val tail = logTail(session.log).ifBlank { reason }
		runCatching { deleteTemps(session) }
		done("Export failed at frame ${session.frame}: $tail. Log: ${session.log}", false)
	}

	private fun stopWriter(session: Session) {
		session.process.destroyForcibly()
		session.writer.interrupt()
		if (session.writer.isAlive) session.writer.join()
	}

	private fun discard(session: Session) {
		session.nv12?.close()
		session.writer.interrupt()
		session.process.destroyForcibly().waitFor()
		runCatching {
			deleteTemps(session)
			Files.deleteIfExists(session.log)
		}
	}

	private fun deleteTemps(session: Session) {
		session.pcmOut?.close()
		Files.deleteIfExists(session.video)
		Files.deleteIfExists(session.pcm)
		Files.deleteIfExists(session.muxed)
	}

	private fun restore(session: Session) {
		this.session = null
		setTimeSource(TimeSource.NanoTimeSource { realNanos() })
		//? if >=1.21.11
		Minecraft.getInstance().options.chunkSectionFadeInTime().set(session.fadeIn)
		session.endTime = System.nanoTime()
		closeReadbacks(session)
		session.nv12?.close()
		session.preview.close()
		restoreSize()
	}

	private fun closeReadbacks(session: Session) {
		session.slots.forEach {
			it.view?.close()
			it.fence?.close()
			it.buffer.close()
		}
		session.slots.clear()
		session.inFlight.clear()
		session.idle.clear()
		session.queue.clear()
		session.written.clear()
	}

	private fun restoreSize() {
		val mc = Minecraft.getInstance()
		val window = mc.window
		val size = window.queryFramebufferSize()
		window.width = size.width()
		window.height = size.height()
		resizeRenderTargets(size.width(), size.height())
		mc.resizeGui()
	}

	private fun launch(command: List<String>, log: Path): Process =
		ProcessBuilder(command)
			.redirectErrorStream(true)
			.redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile()))
			.start()
			.also { child = it }

	private fun logTail(log: Path): String =
		runCatching { Files.readString(log, StandardCharsets.ISO_8859_1).trim().takeLast(300).trimEnd('.') }.getOrDefault("")

	private fun done(message: String, success: Boolean) {
		busy = false
		val mc = Minecraft.getInstance()
		mc.execute { mc.updateTitle() }
		report(message, success)
	}

	private fun report(message: String, success: Boolean) {
		if (success) LOGGER.info(message) else LOGGER.warn(message)
		Minecraft.getInstance().execute {
			if (success) Notifications.success("Keyframe", message) else Notifications.error("Keyframe", message)
		}
	}

	private class Readback(val buffer: GpuBuffer) {
		var fence: GpuFence? = null
		var view: AutoCloseable? = null
		var data: ByteBuffer? = null
	}

	private class Session(val settings: ExportSettings, val totalFrames: Int, startTick: Int, val ffmpeg: Path, val nv12: Nv12Converter?) {
		private val stem = settings.outputDir.resolve("keyframe-${Util.getFilenameFormattedDateTime()}").toString()
		val video: Path = Path.of("$stem.tmp.mkv")
		val pcm: Path = Path.of("$stem.tmp.pcm")
		val muxed: Path = Path.of("$stem.muxed.tmp")
		val log: Path = Path.of("$stem.log")
		val output: Path = Path.of("$stem.${settings.container.extension}")

		val pcmOut: FileChannel? = if (settings.audio) FileChannel.open(pcm, StandardOpenOption.CREATE, StandardOpenOption.WRITE) else null
		val pixels = ByteArray(settings.width * settings.height * 4)
		val frameBytes = if (nv12 != null) settings.width * settings.height * 3 / 2 else pixels.size
		val process = launch(Ffmpeg.videoCommand(ffmpeg, settings, video, nv12 != null, totalFrames), log)
		val idle = ArrayDeque<Readback>()
		val inFlight = ArrayDeque<Readback>()
		val slots = mutableListOf<Readback>()
		val queue = ArrayBlockingQueue<Readback>(READBACK_SLOTS)
		val written = ArrayBlockingQueue<Readback>(READBACK_SLOTS)
		val writer = thread(start = false, name = "Keyframe Encoder", isDaemon = true) { writeFrames(this) }

		@Volatile
		var writeFailure: IOException? = null

		val schedule = TickSchedule(settings.fps)
		val firstFrame = -preRollFrames(startTick, settings.fps)
		var frame = firstFrame
		var pendingTicks = 0
		var origin = 0L

		@Volatile
		var now = 0L

		var armed = false
		var readyBefore = false
		var lastReady = 0L
		var warned = false
		var fadeIn = 0.0

		val preview = ExportPreview(settings.preview)
		var previewDirty = false
		var previewDue = settings.preview
		var previewFrame = false
		var startTime = 0L
		var endTime = 0L
		var waiting = false
		var captured = 0
		var firstCapture = 0L
		var statusTime = 0L
		var statusFrames = 0
		var readbackNanos = 0L
		var totalReadbackNanos = 0L
		var totalFfmpegNanos = 0L
		var ffmpegNanos = 0L
	}
}
