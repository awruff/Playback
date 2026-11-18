package org.polyfrost.keyframe.standalone.editor

import net.minecraft.client.Minecraft
import net.minecraft.util.Util
import org.polyfrost.keyframe.standalone.Standalone
import org.polyfrost.keyframe.standalone.StandaloneConfig
import org.polyfrost.keyframe.standalone.compat.*
import org.polyfrost.keyframe.standalone.export.Container
import org.polyfrost.keyframe.standalone.export.Export
import org.polyfrost.keyframe.standalone.export.ExportSettings
import org.polyfrost.keyframe.standalone.export.Ffmpeg
import org.polyfrost.keyframe.standalone.export.TitleSpan
import org.polyfrost.keyframe.standalone.export.ffmpegInstallRoot
import org.polyfrost.keyframe.standalone.export.exportFrames
import org.polyfrost.keyframe.standalone.export.withBestEncoder
import org.polyfrost.keyframe.standalone.playback.Keyframe
import org.polyfrost.keyframe.standalone.recording.Clip
import org.polyfrost.keyframe.standalone.recording.EmbeddedReplay
import org.polyfrost.keyframe.standalone.recording.Recorder
import org.polyfrost.oneconfig.api.notifications.v1.Notifications
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.concurrent.thread
import kotlin.io.path.deleteRecursively
import kotlin.io.path.extension
import kotlin.io.path.listDirectoryEntries

object EditorExport {
	private val LOGGER = LoggerFactory.getLogger(Standalone.MOD_ID)

	private var job: Job? = null

	fun start(project: Project, settings: ExportSettings) {
		val stem = "keyframe-${Util.getFilenameFormattedDateTime()}"
		job = Job(project, settings, stem, settings.outputDir.resolve(".$stem-parts"))
	}

	fun tick() {
		val job = job ?: return
		val mc = Minecraft.getInstance()
		if (Keyframe.active || Export.busy || mc.level != null) return

		if (job.next > 0 && partFile(job, job.next - 1) == null) return abort(job)
		if (job.next == job.clips.size) return finish(job)
		startPart(job)
	}

	private fun startPart(job: Job) {
		val index = job.next++
		val clip = job.clips[index]
		val file = Recorder.replayFile(clip.file)
		try {
			if (job.clips.size > 1) Notifications.info("Keyframe", "Rendering part ${index + 1} of ${job.clips.size}")
			Keyframe.start(file, partSettings(job, index), Clip(clip.start, clip.end))
		} catch (e: Exception) {
			LOGGER.error("Could not render {}", clip.file, e)
			Notifications.error("Keyframe", "Could not render part ${index + 1}: ${e.message}")
			abort(job)
		}
	}

	private fun partSettings(job: Job, index: Int): ExportSettings {
		val clip = job.clips[index]
		val start = startTime(job.clips, index)
		val titles = job.project.titles.filter { it.end > start && it.start < start + clip.duration }.map {
			TitleSpan(it.text, (it.start - start).toFloat() / TICKS_PER_SECOND, (it.end - start).toFloat() / TICKS_PER_SECOND, it.position, it.size)
		}
		return job.settings.copy(
			outputDir = partDir(job, index),
			speed = clip.speed,
			fadeIn = clip.fadeIn.toFloat() / TICKS_PER_SECOND,
			fadeOut = clip.fadeOut.toFloat() / TICKS_PER_SECOND,
			titles = titles,
		)
	}

	private fun abort(job: Job) {
		this.job = null
		if (!failedWithLog(job)) clean(job)
		Editor.open()
	}

	private fun finish(job: Job) {
		this.job = null
		val settings = job.settings
		val mc = Minecraft.getInstance()
		val parts = job.clips.indices.map { partFile(job, it)!! }
		val output = settings.outputDir.resolve("${job.stem}.${settings.container.extension}")
		thread(name = "Keyframe Export", isDaemon = true) {
			val failure = try {
				join(Ffmpeg.resolve(settings.ffmpegPath, ffmpegInstallRoot), job, parts, output)
			} catch (e: IOException) {
				e.message ?: e.toString()
			}
			if (failure == null) clean(job)
			mc.execute {
				if (failure == null) {
					Notifications.success("Keyframe", "Saved ${output.fileName}")
				} else {
					Notifications.error("Keyframe", "Could not join the parts: $failure. They are in ${job.parts}")
				}
				Editor.open()
			}
		}
	}

	private fun join(ffmpeg: Path, job: Job, parts: List<Path>, output: Path): String? {
		val list = job.parts.resolve("parts.txt")
		val log = job.parts.resolve("join.log")
		val joined = job.parts.resolve("joined.tmp")
		Files.writeString(list, Ffmpeg.concatList(parts))
		val container = job.settings.container
		val embedded = if (StandaloneConfig.embedRecording && container != Container.WEBM) EmbeddedReplay.pack(job.clips, job.parts) else null
		val attachment = embedded.takeIf { container == Container.MKV }
		val crossfades = job.clips.indices.map { overlap(job.clips, it).toDouble() / TICKS_PER_SECOND }
		val command = if (crossfades.any { it > 0 }) {
			val settings = job.settings.withBestEncoder(ffmpeg)
			val durations = job.clips.map { exportFrames(it.length, settings.fps, it.speed).toDouble() / settings.fps }
			Ffmpeg.transitionCommand(ffmpeg, parts, Ffmpeg.transitionGraph(durations, crossfades, settings), settings, joined, attachment)
		} else {
			Ffmpeg.concatCommand(ffmpeg, list, job.settings, joined, attachment)
		}
		val process = ProcessBuilder(command)
			.redirectErrorStream(true)
			.redirectOutput(log.toFile())
			.start()
		process.outputStream.close()
		val exit = process.waitFor()
		if (exit != 0) return Files.readString(log).trim().takeLast(300).ifEmpty { "ffmpeg exited with code $exit" }
		if (embedded != null && container != Container.MKV) EmbeddedReplay.appendBox(joined, embedded)
		Files.move(joined, output, StandardCopyOption.ATOMIC_MOVE)
		return null
	}

	private fun partDir(job: Job, index: Int) = job.parts.resolve("$index")

	private fun partFile(job: Job, index: Int): Path? = try {
		partDir(job, index).listDirectoryEntries().firstOrNull { it.extension == job.settings.container.extension }
	} catch (_: IOException) {
		null
	}

	private fun failedWithLog(job: Job): Boolean = try {
		partDir(job, job.next - 1).listDirectoryEntries("*.log").isNotEmpty()
	} catch (_: IOException) {
		false
	}

	@OptIn(kotlin.io.path.ExperimentalPathApi::class)
	private fun clean(job: Job) {
		try {
			job.parts.deleteRecursively()
		} catch (e: IOException) {
			LOGGER.warn("Could not delete {}", job.parts, e)
		}
	}

	private class Job(val project: Project, val settings: ExportSettings, val stem: String, val parts: Path) {
		val clips: List<TimelineClip> get() = project.clips
		var next = 0
	}
}
