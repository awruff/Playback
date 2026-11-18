package org.polyfrost.keyframe.standalone.editor

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.gson.Gson
import net.minecraft.SharedConstants
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.TitleScreen
import org.polyfrost.keyframe.standalone.Standalone
import org.polyfrost.keyframe.standalone.StandaloneConfig
import org.polyfrost.keyframe.standalone.compat.*
import org.polyfrost.keyframe.standalone.export.Ffmpeg
import org.polyfrost.keyframe.standalone.export.PreviewTexture
import org.polyfrost.keyframe.standalone.export.ffmpegInstallRoot
import org.polyfrost.keyframe.standalone.export.formatProblem
import org.polyfrost.keyframe.standalone.playback.Keyframe
import org.polyfrost.keyframe.standalone.recording.Clip
import org.polyfrost.keyframe.standalone.recording.Recorder
import org.polyfrost.keyframe.standalone.recording.RecordingInfo
import org.polyfrost.keyframe.standalone.recording.ReplayFile
import org.polyfrost.keyframe.standalone.recording.ReplayReader
import org.polyfrost.keyframe.standalone.recording.scanRecording
import org.polyfrost.oneconfig.api.notifications.v1.Notifications
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import kotlin.concurrent.thread
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.listDirectoryEntries

private const val MAX_SHUTTLE = 8
private const val REVERSE_STEP_TICKS = TICKS_PER_SECOND / 2
internal const val MAX_ZOOM = 64f
private const val TITLE_TICKS = 3 * TICKS_PER_SECOND

data class ContextMenu(val x: Float, val y: Float, val clip: Int?, val title: Int?, val time: Int)

private data class EditorPrefs(val names: Map<String, String> = emptyMap())

object Editor {
	private val LOGGER = LoggerFactory.getLogger(Standalone.MOD_ID)
	private val GSON = Gson()

	private val history = History()
	private val scans = HashMap<Path, RecordingInfo>()
	private var opened = false
	private var previewFile: Path? = null
	private var pendingFile: Path? = null
	private var pendingTick = 0
	private var probedFfmpeg: String? = null
	private var scrubbing = false
	private var clipboard: TimelineClip? = null
	private var playingIndex = -1

	var project by mutableStateOf(Project())
		private set
	val clips: List<TimelineClip> get() = project.clips
	val titles: List<TimelineTitle> get() = project.titles
	var library by mutableStateOf(emptyList<RecordingInfo>())
		private set
	var scanning by mutableStateOf(true)
		private set
	private var selectedClip by mutableStateOf<Int?>(null)
	private var selectedTitleIndex by mutableStateOf<Int?>(null)
	var selected: Int?
		get() = selectedClip
		set(value) {
			selectedClip = value
			if (value != null) selectedTitleIndex = null
		}
	var selectedTitle: Int?
		get() = selectedTitleIndex
		set(value) {
			selectedTitleIndex = value
			if (value != null) selectedClip = null
		}
	var menu by mutableStateOf<ContextMenu?>(null)
	var playhead by mutableIntStateOf(0)
		private set
	var playing by mutableStateOf(false)
		private set
	var canUndo by mutableStateOf(false)
		private set
	var canRedo by mutableStateOf(false)
		private set
	var zoom by mutableFloatStateOf(1f)
	var moreSettings by mutableStateOf(false)
	var exportPage by mutableStateOf(false)
	var blade by mutableStateOf(false)
	var deleting by mutableStateOf<RecordingInfo?>(null)
	var speed by mutableIntStateOf(1)
		private set
	var reversing by mutableStateOf(false)
		private set
	var waiting by mutableStateOf(false)
		private set
	var live by mutableStateOf(false)
		private set
	var exportWidth by mutableIntStateOf(StandaloneConfig.width)
		private set
	var exportHeight by mutableIntStateOf(StandaloneConfig.height)
		private set
	var exportFps by mutableIntStateOf(StandaloneConfig.frameRate)
		private set
	var exportProblem by mutableStateOf<String?>(null)
		private set

	private val names = mutableStateMapOf<String, String>()

	val scroll = ScrollState(0)

	val total: Int get() = totalTicks(clips)
	val canExport: Boolean get() = clips.isNotEmpty() && exportProblem == null

	private val recordings: Path get() = Recorder.directory
	private val projectFile: Path
		get() = Minecraft.getInstance().gameDirectory.toPath().resolve("keyframe/edit.json")
	private val prefsFile: Path
		get() = Minecraft.getInstance().gameDirectory.toPath().resolve("keyframe/editor.json")

	fun open() {
		Recorder.finish()
		opened = true
		load()
		scanLibrary()
		probeEncoders()
		Minecraft.getInstance().gui.setScreen(EditorScreen())
		seek(playhead)
	}

	fun close() {
		exportPage = false
		leave()
		if (Keyframe.active) Keyframe.stop() else Minecraft.getInstance().gui.setScreen(TitleScreen())
	}

	fun export() {
		val settings = try {
			StandaloneConfig.exportSettings()
		} catch (e: InvalidPathException) {
			Notifications.error("Keyframe", "The output folder isn't a valid path: ${e.reason}")
			return
		}
		leave()
		if (Keyframe.active) Keyframe.stop()
		EditorExport.start(project, settings)
	}

	private fun leave() {
		opened = false
		playing = false
		reversing = false
		deleting = null
		menu = null
		pendingFile = null
		previewFile = null
		PreviewTexture.close()
		StandaloneConfig.save()
	}

	fun tick() {
		if (!opened) return
		exportProblem = formatProblem(StandaloneConfig.encoder, StandaloneConfig.container, StandaloneConfig.colorFormat)
		update()
		waiting = pendingFile != null || (Keyframe.previewing && !Keyframe.settled && !scrubbing)
		live = Keyframe.previewing
	}

	private fun update() {
		val mc = Minecraft.getInstance()
		val file = pendingFile
		if (file != null) {
			if (!Keyframe.active && mc.level == null) startPreview(file)
			return
		}
		if (!Keyframe.previewing) return
		if (Keyframe.loaded && mc.gui.screen() !is EditorScreen) mc.gui.setScreen(EditorScreen())
		Keyframe.playing = playing && !scrubbing
		Keyframe.speed = speed
		Keyframe.clipSpeed = clips.getOrNull(playingIndex)?.speed ?: 1f
		if (!Keyframe.settled) return
		when {
			scrubbing -> followScrub()
			reversing -> stepBack()
			playing -> follow()
		}
	}

	private fun stepBack() {
		if (playhead <= 0 || !Keyframe.canRewind) return stop()
		seek(playhead - speed * REVERSE_STEP_TICKS)
	}

	private fun followScrub() {
		val (index, file, tick) = target() ?: return
		if (file != previewFile) return
		playingIndex = index
		if (tick > Keyframe.tick) Keyframe.seek(tick) else if (tick < Keyframe.tick) Keyframe.rewind(tick)
	}

	private fun follow() {
		val clip = clips.getOrNull(playingIndex) ?: return play(false)
		val start = startTime(clips, playingIndex)
		val next = if (playingIndex < clips.lastIndex) startTime(clips, playingIndex + 1) else Int.MAX_VALUE
		val time = start + clip.timelineOffset(Keyframe.tick)
		when {
			Keyframe.tick < clip.end && time < next -> playhead = time
			playingIndex < clips.lastIndex -> seek(next)
			else -> {
				playhead = total
				play(false)
			}
		}
	}

	private fun startPreview(file: Path) {
		val info = scans[file] ?: scanRecording(file, protocol())
		pendingFile = null
		try {
			Keyframe.start(file, range = Clip(pendingTick, info.lastTick), preview = true)
			previewFile = file
		} catch (e: Exception) {
			LOGGER.error("Could not load {}", file, e)
			Notifications.error("Keyframe", "This recording could not be loaded: ${e.message}")
		}
	}

	fun togglePlaying() {
		if (clips.isEmpty()) return
		if (reversing) return stop()
		speed = 1
		if (!playing && playhead >= total) seek(0)
		play(!playing)
	}

	fun stop() {
		reversing = false
		speed = 1
		play(false)
	}

	fun shuttleForward() {
		if (clips.isEmpty()) return
		speed = if (playing) minOf(speed * 2, MAX_SHUTTLE) else 1
		reversing = false
		if (!playing && playhead >= total) seek(0)
		play(true)
	}

	fun shuttleBack() {
		if (clips.isEmpty()) return
		speed = if (reversing) minOf(speed * 2, MAX_SHUTTLE) else 1
		play(false)
		reversing = true
	}

	private fun play(value: Boolean) {
		playing = value
		Keyframe.playing = value
	}

	fun step(ticks: Int) {
		stop()
		seek(playhead + ticks)
	}

	fun previousEdit() = seek(editBefore(clips, playhead))

	fun nextEdit() = seek(editAfter(clips, playhead))

	fun zoomBy(factor: Float) {
		zoom = Math.clamp(zoom * factor, 1f, MAX_ZOOM)
	}

	fun scrub(time: Int) {
		scrubbing = true
		movePlayhead(time)
	}

	private fun movePlayhead(time: Int) {
		playhead = Math.clamp(time.toLong(), 0, total)
	}

	private fun target(): Triple<Int, Path, Int>? {
		val (index, offset) = locate(clips, playhead) ?: return null
		val clip = clips[index]
		return Triple(index, Recorder.replayFile(clip.file), minOf(clip.sourceTick(offset), maxOf(clip.end - 1, clip.start)))
	}

	fun seek(time: Int) {
		scrubbing = false
		movePlayhead(time)
		val (index, file, tick) = target() ?: return release()
		playingIndex = index
		val loaded = Keyframe.previewing && previewFile == file
		when {
			loaded && tick >= Keyframe.tick -> Keyframe.seek(tick)
			loaded && Keyframe.rewind(tick) -> Unit
			else -> {
				pendingFile = file
				pendingTick = tick
				if (Keyframe.active) Keyframe.stop(EditorScreen())
			}
		}
	}

	private fun release() {
		pendingFile = null
		previewFile = null
		play(false)
		if (Keyframe.active) Keyframe.stop(EditorScreen())
	}

	fun add(info: RecordingInfo) {
		val spans = info.clips.ifEmpty { listOf(Clip(info.firstTick, info.lastTick)) }
		commitClips(clips + spans.map { TimelineClip(info.fileName, it.start, it.end) })
		selected = clips.lastIndex
	}

	fun cutAt(time: Int) = commitClips(split(clips, time))

	val hasMenuTime: Boolean
		get() = clips.any { clip -> info(clip)?.screens.orEmpty().any { it.start < clip.end && it.end > clip.start } }

	fun removeMenus() {
		val before = total
		selected = null
		commitClips(clips.flatMap { withoutSpans(it, info(it)?.screens.orEmpty()) })
		val removed = before - total
		if (removed > 0) Notifications.info("Keyframe", "Removed ${formatTimecode(removed)} where a menu was open")
	}

	fun changeClip(index: Int, change: (TimelineClip) -> TimelineClip) {
		val clip = clips.getOrNull(index) ?: return
		commitClips(clips.toMutableList().apply { set(index, change(clip)) })
	}

	fun duplicate(index: Int) {
		val clip = clips.getOrNull(index) ?: return
		commitClips(clips.take(index + 1) + clip.copy(fadeIn = 0) + clips.drop(index + 1))
		selected = index + 1
	}

	fun addTitle(at: Int = playhead) {
		val start = minOf(at, maxOf(total - 1, 0))
		val title = TimelineTitle("Title", start, maxOf(minOf(start + TITLE_TICKS, total), start + 1))
		commit(project.copy(titles = titles + title))
		selectedTitle = titles.lastIndex
	}

	fun changeTitle(index: Int, change: (TimelineTitle) -> TimelineTitle) {
		val title = titles.getOrNull(index) ?: return
		commit(project.copy(titles = titles.toMutableList().apply { set(index, change(title)) }))
	}

	fun previewTitles(next: List<TimelineTitle>) {
		project = project.copy(titles = next)
	}

	fun finishTitleEdit(before: List<TimelineTitle>) {
		if (titles == before) return
		history.push(project.copy(titles = before))
		afterEdit()
	}

	fun trimToPlayhead(atEnd: Boolean) {
		val (index, offset) = locate(clips, playhead) ?: return
		val clip = clips[index]
		val cut = clip.sourceTick(offset)
		val next = if (atEnd) {
			trimEnd(clips, index, cut, info(clip)?.lastTick ?: clip.end)
		} else {
			trimStart(clips, index, cut, info(clip)?.firstTick ?: clip.start)
		}
		if (!atEnd) movePlayhead(startTime(clips, index))
		commitClips(next)
	}

	val canPaste: Boolean get() = clipboard != null

	fun copySelected() {
		clipboard = selected?.let(clips::getOrNull)
	}

	fun cutSelected() {
		copySelected()
		deleteSelected()
	}

	fun paste() {
		val clip = clipboard ?: return
		val index = locate(clips, playhead)?.let { it.index + 1 } ?: 0
		commitClips(clips.take(index) + clip + clips.drop(index))
		selected = index
	}

	fun deleteSelected() {
		selectedTitle?.let { index ->
			selectedTitle = null
			commit(project.copy(titles = titles.filterIndexed { i, _ -> i != index }))
			return
		}
		val index = selected ?: return
		selected = null
		commitClips(clips.filterIndexed { i, _ -> i != index })
	}

	fun clear() {
		selected = null
		selectedTitle = null
		commit(Project())
	}

	fun moveClip(from: Int, slot: Int) {
		val next = move(clips, from, slot)
		selected = next.indexOfFirst { it === clips[from] }
		commitClips(next)
	}

	fun preview(next: List<TimelineClip>) {
		project = project.copy(clips = next)
	}

	fun finishTrim(before: List<TimelineClip>, index: Int, atEnd: Boolean) {
		if (clips == before) return
		history.push(project.copy(clips = before))
		afterEdit()
		seek(startTime(clips, index) + if (atEnd) clips[index].duration - 1 else 0)
	}

	fun undo() {
		project = history.undo(project) ?: return
		restored()
	}

	fun redo() {
		project = history.redo(project) ?: return
		restored()
	}

	private fun restored() {
		selected = null
		selectedTitle = null
		afterEdit()
		seek(playhead)
	}

	private fun commitClips(next: List<TimelineClip>) = commit(project.copy(clips = next))

	private fun commit(next: Project) {
		if (next == project) return
		history.push(project)
		project = next
		afterEdit()
		seek(playhead)
	}

	private fun afterEdit() {
		canUndo = history.canUndo
		canRedo = history.canRedo
		save(projectFile, project)
	}

	private fun save(file: Path, value: Any) {
		try {
			Files.createDirectories(file.parent)
			Files.writeString(file, GSON.toJson(value))
		} catch (e: IOException) {
			LOGGER.warn("Could not save {}", file, e)
		}
	}

	fun info(clip: TimelineClip): RecordingInfo? = scans[Recorder.replayFile(clip.file)]

	fun label(info: RecordingInfo): String = names[info.fileName] ?: info.label

	fun rename(info: RecordingInfo, name: String) {
		val trimmed = name.trim()
		if (trimmed.isEmpty() || trimmed == info.label) names.remove(info.fileName) else names[info.fileName] = trimmed
		savePrefs()
	}

	fun clipsUsing(info: RecordingInfo): Int = clips.count { it.file == info.fileName }

	fun confirmDelete() {
		val info = deleting ?: return
		deleting = null
		if (pendingFile == info.path) pendingFile = null
		if (previewFile == info.path) {
			previewFile = null
			if (Keyframe.active) Keyframe.stop(EditorScreen())
		}
		try {
			Files.deleteIfExists(info.path)
		} catch (e: IOException) {
			LOGGER.warn("Could not delete {}", info.path, e)
			Notifications.error("Keyframe", "Could not delete this recording: ${e.message}")
			seek(playhead)
			return
		}
		scans.remove(info.path)
		library = library - info
		names.remove(info.fileName)
		savePrefs()
		history.forget(info.fileName)
		if (clipboard?.file == info.fileName) clipboard = null
		selected = null
		project = project.copy(clips = clips.filter { it.file != info.fileName })
		afterEdit()
		seek(playhead)
	}

	private fun savePrefs() = save(prefsFile, EditorPrefs(names.toMap()))

	fun setFrame(width: Int, height: Int) {
		exportWidth = width
		exportHeight = height
		StandaloneConfig.width = width
		StandaloneConfig.height = height
		StandaloneConfig.save()
	}

	fun setFrameRate(fps: Int) {
		exportFps = fps
		StandaloneConfig.fps = fps
		StandaloneConfig.save()
	}

	private fun load() {
		exportWidth = StandaloneConfig.width
		exportHeight = StandaloneConfig.height
		exportFps = StandaloneConfig.frameRate
		loadPrefs()
		if (clips.isNotEmpty() || !projectFile.exists()) return
		try {
			val saved = (GSON.fromJson(Files.readString(projectFile), Project::class.java) ?: return).normalized()
			val present = saved.clips.filter { readable(Recorder.replayFile(it.file)) }
			if (present.size < saved.clips.size) Notifications.info("Keyframe", "Some clips were removed because their recordings are gone or from another version")
			project = saved.copy(clips = present)
		} catch (e: Exception) {
			LOGGER.warn("Could not read the saved edit", e)
		}
	}

	private fun loadPrefs() {
		if (!prefsFile.exists()) return
		try {
			val prefs = GSON.fromJson(Files.readString(prefsFile), EditorPrefs::class.java) ?: return
			names.clear()
			names.putAll(prefs.names)
		} catch (e: Exception) {
			LOGGER.warn("Could not read the editor settings", e)
		}
	}

	private fun probeEncoders() {
		val path = StandaloneConfig.ffmpegPath
		if (path == probedFfmpeg) return
		probedFfmpeg = path
		val mc = Minecraft.getInstance()
		thread(name = "Keyframe Encoders", isDaemon = true) {
			try {
				val supported = Ffmpeg.supportedEncoders(Ffmpeg.resolve(path, ffmpegInstallRoot))
				if (supported.isNotEmpty()) mc.execute { StandaloneConfig.limitEncoders(supported) }
			} catch (e: IOException) {
				LOGGER.warn("Could not check which encoders work", e)
				mc.execute { probedFfmpeg = null }
			}
		}
	}

	private fun scanLibrary() {
		scanning = true
		val mc = Minecraft.getInstance()
		val protocol = protocol()
		thread(name = "Keyframe Library", isDaemon = true) {
			val found = try {
				listOf(recordings, Recorder.clipsDirectory).flatMap { dir ->
					Files.createDirectories(dir)
					dir.listDirectoryEntries().filter { it.extension == ReplayFile.EXTENSION }
				}.sortedByDescending { it.fileName }
			} catch (e: IOException) {
				LOGGER.warn("Could not list recordings", e)
				emptyList()
			}
			for (path in found) {
				if (path in scans) continue
				val info = scanRecording(path, protocol)
				mc.execute {
					scans[path] = info
					library = found.mapNotNull(scans::get).filter { it.playable }
				}
			}
			mc.execute {
				library = found.mapNotNull(scans::get).filter { it.playable }
				scanning = false
			}
		}
	}

	private fun readable(path: Path) = try {
		ReplayReader(path, protocol()).close()
		true
	} catch (_: IOException) {
		false
	}

	private fun protocol() = SharedConstants.getCurrentVersion().protocolVersion()
}
