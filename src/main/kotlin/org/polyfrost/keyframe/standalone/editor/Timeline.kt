package org.polyfrost.keyframe.standalone.editor

import org.polyfrost.keyframe.standalone.recording.Clip
import kotlin.math.ceil

const val TICKS_PER_SECOND = 20
internal const val MIN_FRAME_RATE = 24
internal const val MAX_FRAME_RATE = 9999
internal const val SMOOTH_FRAME_RATE = 120
internal const val MAX_FRAME_RATE_DIGITS = 4
private const val MIN_CLIP_TICKS = 1

data class TimelineClip(
	val file: String,
	val start: Int,
	val end: Int,
	val speed: Float = 1f,
	val fadeIn: Int = 0,
	val fadeOut: Int = 0,
	val crossfade: Int = 0,
) {
	val length: Int get() = end - start

	val duration: Int get() = if (speed == 1f) length else ceil(length / speed.toDouble()).toInt()

	fun sourceTick(offset: Int): Int = start + Math.clamp((offset * speed.toDouble()).toLong(), 0, length)

	fun timelineOffset(tick: Int): Int = Math.clamp(((tick - start) / speed.toDouble()).toLong(), 0, duration)

	internal fun normalized(): TimelineClip = if (speed in SPEEDS) this else copy(speed = 1f)
}

internal val SPEEDS = listOf(0.25f, 0.5f, 1f, 2f, 4f)

enum class TitlePosition { TOP, MIDDLE, BOTTOM }

enum class TitleSize(val label: String, val height: Float) {
	SMALL("Small", 0.04f),
	MEDIUM("Medium", 0.06f),
	LARGE("Large", 0.09f),
}

data class TimelineTitle(
	val text: String,
	val start: Int,
	val end: Int,
	val position: TitlePosition = TitlePosition.BOTTOM,
	val size: TitleSize = TitleSize.MEDIUM,
) {
	internal fun normalized(): TimelineTitle = copy(position = position ?: TitlePosition.BOTTOM, size = size ?: TitleSize.MEDIUM)
}

data class Project(val clips: List<TimelineClip> = emptyList(), val titles: List<TimelineTitle> = emptyList()) {
	internal fun normalized(): Project = Project(clips.orEmpty().map { it.normalized() }, titles.orEmpty().map { it.normalized() })
}

internal data class Located(val index: Int, val offset: Int)

internal fun overlap(clips: List<TimelineClip>, index: Int): Int {
	if (index >= clips.lastIndex) return 0
	return minOf(clips[index].crossfade, clips[index].duration / 2, clips[index + 1].duration / 2)
}

internal fun starts(clips: List<TimelineClip>): IntArray {
	val starts = IntArray(clips.size)
	for (i in 1 until clips.size) starts[i] = starts[i - 1] + clips[i - 1].duration - overlap(clips, i - 1)
	return starts
}

internal fun totalTicks(clips: List<TimelineClip>): Int = if (clips.isEmpty()) 0 else starts(clips).last() + clips.last().duration

internal fun startTime(clips: List<TimelineClip>, index: Int): Int = if (index >= clips.size) totalTicks(clips) else starts(clips)[index]

internal fun locate(clips: List<TimelineClip>, time: Int): Located? {
	if (clips.isEmpty()) return null
	val starts = starts(clips)
	for (i in 0 until clips.lastIndex) if (time < starts[i + 1]) return Located(i, maxOf(time - starts[i], 0))
	return Located(clips.lastIndex, Math.clamp((time - starts.last()).toLong(), 0, clips.last().duration))
}

internal fun split(clips: List<TimelineClip>, time: Int): List<TimelineClip> {
	val (index, offset) = locate(clips, time) ?: return clips
	val clip = clips[index]
	val cut = clip.sourceTick(offset)
	if (cut <= clip.start || cut >= clip.end) return clips
	return clips.take(index) + clip.copy(end = cut, fadeOut = 0, crossfade = 0) + clip.copy(start = cut, fadeIn = 0) + clips.drop(index + 1)
}

internal fun withoutSpans(clip: TimelineClip, spans: List<Clip>): List<TimelineClip> {
	val pieces = mutableListOf<Clip>()
	var cursor = clip.start
	for (span in spans.sortedBy { it.start }) {
		if (span.end <= cursor || span.start >= clip.end) continue
		if (span.start > cursor) pieces += Clip(cursor, span.start)
		cursor = maxOf(cursor, span.end)
	}
	if (cursor < clip.end) pieces += Clip(cursor, clip.end)
	if (pieces.size == 1 && pieces[0] == Clip(clip.start, clip.end)) return listOf(clip)
	return pieces.mapIndexed { i, piece ->
		val last = i == pieces.lastIndex
		clip.copy(
			start = piece.start,
			end = piece.end,
			fadeIn = if (i == 0) clip.fadeIn else 0,
			fadeOut = if (last) clip.fadeOut else 0,
			crossfade = if (last) clip.crossfade else 0,
		)
	}
}

internal fun <T> move(items: List<T>, from: Int, slot: Int): List<T> {
	val target = if (slot > from) slot - 1 else slot
	if (target == from) return items
	return items.toMutableList().apply { add(target, removeAt(from)) }
}

internal fun editBefore(clips: List<TimelineClip>, time: Int): Int = starts(clips).lastOrNull { it < time } ?: 0

internal fun editAfter(clips: List<TimelineClip>, time: Int): Int = starts(clips).firstOrNull { it > time } ?: totalTicks(clips)

internal fun dropSlot(midpoints: List<Float>, x: Float): Int = midpoints.count { it <= x }

internal fun trimStart(clips: List<TimelineClip>, index: Int, tick: Int, first: Int): List<TimelineClip> {
	val clip = clips[index]
	val start = Math.clamp(tick.toLong(), first, clip.end - MIN_CLIP_TICKS)
	return clips.toMutableList().apply { set(index, clip.copy(start = start)) }
}

internal fun trimEnd(clips: List<TimelineClip>, index: Int, tick: Int, last: Int): List<TimelineClip> {
	val clip = clips[index]
	val end = Math.clamp(tick.toLong(), clip.start + MIN_CLIP_TICKS, last)
	return clips.toMutableList().apply { set(index, clip.copy(end = end)) }
}

internal fun fadeAmount(clips: List<TimelineClip>, time: Int): Float {
	val (index, offset) = locate(clips, time) ?: return 0f
	val clip = clips[index]
	val fadeIn = if (offset < clip.fadeIn) 1f - offset.toFloat() / clip.fadeIn else 0f
	val left = clip.duration - offset
	val fadeOut = if (left < clip.fadeOut) 1f - left.toFloat() / clip.fadeOut else 0f
	return Math.clamp(maxOf(fadeIn, fadeOut), 0f, 1f)
}

internal fun titlesAt(titles: List<TimelineTitle>, time: Int): List<TimelineTitle> = titles.filter { time >= it.start && time < it.end }

internal class History {
	private val undo = ArrayDeque<Project>()
	private val redo = ArrayDeque<Project>()

	val canUndo: Boolean get() = undo.isNotEmpty()
	val canRedo: Boolean get() = redo.isNotEmpty()

	fun forget(file: String) {
		for (stack in listOf(undo, redo)) {
			stack.indices.forEach { stack[it] = stack[it].copy(clips = stack[it].clips.filter { clip -> clip.file != file }) }
		}
	}

	fun push(project: Project) {
		undo.addLast(project)
		redo.clear()
	}

	fun undo(current: Project): Project? = undo.removeLastOrNull()?.also { redo.addLast(current) }

	fun redo(current: Project): Project? = redo.removeLastOrNull()?.also { undo.addLast(current) }
}

internal fun formatTime(ticks: Int): String {
	val seconds = ticks / TICKS_PER_SECOND
	return "%d:%02d".format(seconds / 60, seconds % 60)
}

internal fun formatTimecode(ticks: Int): String = "%s.%02d".format(formatTime(ticks), ticks % TICKS_PER_SECOND * 100 / TICKS_PER_SECOND)

internal fun formatSpeed(speed: Float): String = if (speed < 1f) "${speed}×".removePrefix("0") else "${speed.toInt()}×"

internal fun clampFrameRate(fps: Int): Int = Math.clamp(fps.toLong(), MIN_FRAME_RATE, MAX_FRAME_RATE)

internal fun frameRate(text: String, fallback: Int): Int = text.toIntOrNull()?.let(::clampFrameRate) ?: fallback

internal fun frameSize(wide: Int, tall: Int, shortEdge: Int): Pair<Int, Int> {
	val scale = shortEdge.toDouble() / minOf(wide, tall)
	return evenSize(wide * scale) to evenSize(tall * scale)
}

internal fun evenSize(size: Double): Int = Math.round(size / 2).toInt() * 2

internal data class Crop(val u0: Float, val u1: Float, val aspect: Float)

internal data class HudArea(val x: Float, val scale: Float, val width: Int, val height: Int)

internal fun hudArea(guiWidth: Int, guiHeight: Int, crop: Crop, exportGuiHeight: Int): HudArea {
	val scale = guiHeight.toFloat() / exportGuiHeight
	return HudArea(crop.u0 * guiWidth, scale, Math.round((crop.u1 - crop.u0) * guiWidth / scale), exportGuiHeight)
}

internal fun previewCrop(windowAspect: Float, exportAspect: Float): Crop {
	if (exportAspect >= windowAspect) return Crop(0f, 1f, windowAspect)
	val margin = (1 - exportAspect / windowAspect) / 2
	return Crop(margin, 1 - margin, exportAspect)
}
