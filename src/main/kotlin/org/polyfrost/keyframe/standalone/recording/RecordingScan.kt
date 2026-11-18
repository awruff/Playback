package org.polyfrost.keyframe.standalone.recording

import java.io.IOException
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import kotlin.io.path.nameWithoutExtension
import kotlin.math.sqrt

internal const val ACTIVITY_TICKS = 10

internal val FILE_STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss-SSS")
private val LABEL = DateTimeFormatter.ofPattern("MMM d, HH:mm", Locale.ENGLISH)

class RecordingInfo(
	val path: Path,
	val clips: List<Clip>,
	val firstTick: Int,
	val lastTick: Int,
	val activity: FloatArray,
	val screens: List<Clip>,
	val highlights: List<Highlight>,
	val playable: Boolean,
	val thumbnails: List<Thumbnail> = emptyList(),
) {
	val fileName: String get() = path.fileName.toString()
	val label: String get() = recordingLabel(path)
}

internal fun recordingLabel(path: Path): String {
	val stem = path.nameWithoutExtension.removeSuffix(".clip")
	val label = try {
		LABEL.format(LocalDateTime.parse(stem, FILE_STAMP))
	} catch (_: DateTimeParseException) {
		stem
	}
	return if (stem == path.nameWithoutExtension) label else "Clip, $label"
}

internal fun scanRecording(path: Path, protocol: Int): RecordingInfo {
	val reader = try {
		ReplayReader(path, protocol)
	} catch (_: IOException) {
		return RecordingInfo(path, emptyList(), 0, 0, FloatArray(0), emptyList(), emptyList(), false)
	}
	val clips = mutableListOf<Clip>()
	val screens = mutableListOf<Clip>()
	val highlights = mutableListOf<Highlight>()
	val thumbnails = mutableListOf<Thumbnail>()
	val packets = ArrayList<Long>()
	var clipStart = -1
	var screenStart = -1
	var first = -1
	var last = 0
	reader.use {
		while (true) {
			val record = it.next() ?: break
			last = record.tick
			when (record.type) {
				ReplayFile.SNAPSHOT -> if (first < 0) first = record.tick
				ReplayFile.PLAY_FRAME -> {
					val bucket = record.tick / ACTIVITY_TICKS
					while (packets.size <= bucket) packets += 0L
					packets[bucket]++
				}
				ReplayFile.MARKER -> {
					if (record.payload[0] == ReplayFile.MARKER_START) {
						clipStart = record.tick
					} else if (clipStart >= 0) {
						clips += Clip(clipStart, record.tick)
						clipStart = -1
					}
				}
				ReplayFile.CLIP -> clips += decodeClip(record.payload)
				ReplayFile.HIGHLIGHT -> highlights += decodeHighlight(record.tick, record.payload)
				ReplayFile.THUMBNAIL -> decodeThumbnail(record.payload)?.let { thumbnails += it }
				ReplayFile.SCREEN_TRANSITION -> {
					if (screenStart >= 0) screens += Clip(screenStart, record.tick)
					screenStart = if (record.payload.isEmpty()) -1 else record.tick
				}
			}
		}
	}
	if (clipStart >= 0) clips += Clip(clipStart, last)
	if (screenStart >= 0) screens += Clip(screenStart, last)
	val firstTick = maxOf(first, 0)
	return RecordingInfo(
		path,
		clips.map { Clip(maxOf(it.start, firstTick), it.end) }.filter { it.end > it.start },
		firstTick,
		last,
		normalizeActivity(packets),
		screens,
		highlights,
		true,
		thumbnails.sortedBy { it.tick },
	)
}

internal fun normalizeActivity(packets: List<Long>): FloatArray {
	val peak = packets.maxOrNull() ?: 0L
	if (peak == 0L) return FloatArray(packets.size)
	return FloatArray(packets.size) { sqrt(packets[it].toDouble() / peak).toFloat() }
}

internal fun activityPeak(activity: FloatArray, fromTick: Int, toTick: Int): Float =
	(fromTick / ACTIVITY_TICKS..toTick / ACTIVITY_TICKS).maxOf { activity.getOrElse(it) { 0f } }
