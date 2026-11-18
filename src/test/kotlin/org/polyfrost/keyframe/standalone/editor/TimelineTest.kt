package org.polyfrost.keyframe.standalone.editor

import androidx.compose.ui.geometry.Offset
import org.polyfrost.keyframe.standalone.export.Ffmpeg
import org.polyfrost.keyframe.standalone.recording.Clip
import org.polyfrost.keyframe.standalone.recording.ReplayFile
import org.polyfrost.keyframe.standalone.recording.ReplayHeader
import org.polyfrost.keyframe.standalone.recording.ReplayWriter
import org.polyfrost.keyframe.standalone.recording.Thumbnail
import org.polyfrost.keyframe.standalone.recording.activityPeak
import org.polyfrost.keyframe.standalone.recording.scanRecording
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TimelineTest {
	private val a = TimelineClip("a", 100, 200)
	private val b = TimelineClip("b", 0, 50)
	private val c = TimelineClip("a", 300, 340)

	@Test
	fun locateMapsTimelineTimeToClips() {
		val clips = listOf(a, b)
		assertEquals(Located(0, 0), locate(clips, 0))
		assertEquals(Located(1, 0), locate(clips, 100))
		assertEquals(Located(1, 49), locate(clips, 149))
		assertEquals(Located(1, 50), locate(clips, 150))
		assertNull(locate(emptyList(), 0))
	}

	@Test
	fun splitCutsTheClipUnderThePlayhead() {
		val result = split(listOf(a, b), 40)
		assertEquals(listOf(TimelineClip("a", 100, 140), TimelineClip("a", 140, 200), b), result)
		assertEquals(150, totalTicks(result))
	}

	@Test
	fun splitIgnoresCutsThatWouldLeaveASliver() {
		val clips = listOf(a)
		assertEquals(clips, split(clips, 0))
		assertEquals(clips, split(clips, 100))
		assertEquals(listOf(TimelineClip("a", 100, 101), TimelineClip("a", 101, 200)), split(clips, 1))
		assertEquals(listOf(TimelineClip("a", 100, 199), TimelineClip("a", 199, 200)), split(clips, 99))
	}

	@Test
	fun speedChangesTimelineLengthAndMapsBackToSourceTicks() {
		val slow = a.copy(speed = 0.5f)
		val fast = a.copy(speed = 2f)
		assertEquals(200, slow.duration)
		assertEquals(50, fast.duration)
		assertEquals(150, slow.sourceTick(100))
		assertEquals(150, fast.sourceTick(25))
		assertEquals(100, slow.timelineOffset(150))
		assertEquals(200, fast.sourceTick(999))
		assertEquals(250, totalTicks(listOf(slow, b)))
		assertEquals(Located(1, 0), locate(listOf(slow, b), 200))
	}

	@Test
	fun splitOnAFastClipCutsAtTheSourceTick() {
		val fast = a.copy(speed = 2f, fadeIn = 10, fadeOut = 10, crossfade = 20)
		val (left, right) = split(listOf(fast), 10)
		assertEquals(fast.copy(end = 120, fadeOut = 0, crossfade = 0), left)
		assertEquals(fast.copy(start = 120, fadeIn = 0), right)
	}

	@Test
	fun crossfadesOverlapClipsAndHandOverToTheNextOne() {
		val clips = listOf(a.copy(crossfade = 20), b)
		assertEquals(80, startTime(clips, 1))
		assertEquals(130, totalTicks(clips))
		assertEquals(Located(0, 79), locate(clips, 79))
		assertEquals(Located(1, 0), locate(clips, 80))
		assertEquals(25, overlap(listOf(a.copy(crossfade = 999), b), 0))
		assertEquals(0, overlap(clips, 1))
		assertEquals(80, editAfter(clips, 0))
	}

	@Test
	fun cuttingMenusKeepsTheEffectsOnTheOuterPieces() {
		val clip = a.copy(fadeIn = 10, fadeOut = 5, crossfade = 20)
		val pieces = withoutSpans(clip, listOf(Clip(150, 160), Clip(120, 130), Clip(0, 5), Clip(190, 400)))
		assertEquals(
			listOf(
				clip.copy(end = 120, fadeOut = 0, crossfade = 0),
				clip.copy(start = 130, end = 150, fadeIn = 0, fadeOut = 0, crossfade = 0),
				clip.copy(start = 160, end = 190, fadeIn = 0),
			),
			pieces,
		)
		assertEquals(listOf(clip), withoutSpans(clip, listOf(Clip(0, 50))))
		assertEquals(emptyList(), withoutSpans(clip, listOf(Clip(0, 500))))
	}

	@Test
	fun fadesDarkenTheEdgesOfAClip() {
		val clips = listOf(a.copy(fadeIn = 20, fadeOut = 10))
		assertEquals(1f, fadeAmount(clips, 0))
		assertEquals(0.5f, fadeAmount(clips, 10))
		assertEquals(0f, fadeAmount(clips, 50))
		assertEquals(0.5f, fadeAmount(clips, 95))
	}

	@Test
	fun oldProjectsLoadWithNormalSpeed() {
		assertEquals(a, a.copy(speed = 0f).normalized())
		assertEquals(a.copy(speed = 2f), a.copy(speed = 2f).normalized())
	}

	@Test
	fun titleLaneSitsBetweenRulerAndClips() {
		val layout = Layout(listOf(a), listOf(TimelineTitle("Hi", 20, 60)), 1f, 10f, 10f, 5f, 20f)
		assertEquals(Hit.Title(0), layout.hit(Offset(40f, 15f)))
		assertEquals(Hit.TitleEdge(0, true), layout.hit(Offset(58f, 15f)))
		assertEquals(Hit.Ruler, layout.hit(Offset(80f, 15f)))
		assertEquals(Hit.Grip(0), layout.hit(Offset(40f, 25f)))
	}

	@Test
	fun thumbnailsPickTheLatestFrameAtOrBeforeATick() {
		val thumbnails = listOf(Thumbnail(100, ByteArray(0)), Thumbnail(200, ByteArray(0)))
		assertEquals(100, thumbnailAt(thumbnails, 50)?.tick)
		assertEquals(100, thumbnailAt(thumbnails, 150)?.tick)
		assertEquals(200, thumbnailAt(thumbnails, 200)?.tick)
		assertEquals(200, thumbnailAt(thumbnails, 900)?.tick)
		assertNull(thumbnailAt(emptyList(), 5))
	}

	@Test
	fun moveDropsIntoTheNamedSlot() {
		val clips = listOf(a, b, c)
		assertEquals(listOf(b, c, a), move(clips, 0, 3))
		assertEquals(listOf(c, a, b), move(clips, 2, 0))
		assertEquals(listOf(b, a, c), move(clips, 0, 2))
		assertEquals(clips, move(clips, 1, 1))
		assertEquals(clips, move(clips, 1, 2))
	}

	@Test
	fun dropSlotCountsMidpointsPassed() {
		val midpoints = listOf(10f, 50f, 90f)
		assertEquals(0, dropSlot(midpoints, 5f))
		assertEquals(1, dropSlot(midpoints, 10f))
		assertEquals(2, dropSlot(midpoints, 60f))
		assertEquals(3, dropSlot(midpoints, 95f))
	}

	@Test
	fun clipBodyScrubsAndTitleBarReorders() {
		val layout = Layout(listOf(a, b), emptyList(), 1f, 10f, 0f, 5f, 20f)
		assertEquals(Hit.Ruler, layout.hit(Offset(50f, 5f)))
		assertEquals(Hit.Grip(0), layout.hit(Offset(50f, 20f)))
		assertEquals(Hit.Body(0), layout.hit(Offset(50f, 40f)))
		assertEquals(Hit.Edge(0, false), layout.hit(Offset(2f, 40f)))
		assertEquals(Hit.Body(1), layout.hit(Offset(125f, 40f)))
	}

	@Test
	fun forgettingARecordingClearsItFromUndoAndRedo() {
		val history = History()
		history.push(Project(listOf(a, b)))
		history.push(Project(listOf(b)))
		assertEquals(Project(listOf(b)), history.undo(Project(listOf(a, c))))
		history.forget("a")
		assertEquals(Project(listOf(b)), history.undo(Project(listOf(b))))
		assertEquals(emptyList(), history.redo(Project())?.clips.orEmpty().filter { it.file == "a" })
		assertEquals(emptyList(), history.redo(Project(listOf(b)))?.clips.orEmpty().filter { it.file == "a" })
	}

	@Test
	fun editPointsAreClipBoundaries() {
		val clips = listOf(a, b)
		assertEquals(0, editBefore(clips, 50))
		assertEquals(100, editBefore(clips, 120))
		assertEquals(0, editBefore(clips, 100))
		assertEquals(100, editAfter(clips, 50))
		assertEquals(150, editAfter(clips, 100))
		assertEquals(0, editBefore(emptyList(), 0))
		assertEquals(0, editAfter(emptyList(), 0))
	}

	@Test
	fun moveKeepsIdenticalClipsApart() {
		val clips = listOf(a, b, a)
		assertEquals(listOf(b, a, a), move(clips, 0, 3))
		assertEquals(listOf(a, a, b), move(clips, 2, 1))
	}

	@Test
	fun trimStaysInsideTheRecordingAndKeepsAMinimumLength() {
		assertEquals(TimelineClip("a", 90, 200), trimStart(listOf(a), 0, 40, 90)[0])
		assertEquals(TimelineClip("a", 199, 200), trimStart(listOf(a), 0, 500, 0)[0])
		assertEquals(TimelineClip("a", 100, 250), trimEnd(listOf(a), 0, 900, 250)[0])
		assertEquals(TimelineClip("a", 100, 101), trimEnd(listOf(a), 0, 0, 250)[0])
	}

	@Test
	fun historyWalksBothWaysAndNewEditsClearRedo() {
		val history = History()
		val one = Project(listOf(a))
		val two = Project(listOf(a, b))
		history.push(one)
		assertEquals(one, history.undo(two))
		assertEquals(two, history.redo(one))
		history.undo(two)
		history.push(one)
		assertFalse(history.canRedo)
		assertTrue(history.canUndo)
	}

	@Test
	fun frameSizeHonoursShapeAndStaysEven() {
		assertEquals(1920 to 1080, frameSize(16, 9, 1080))
		assertEquals(1080 to 1920, frameSize(9, 16, 1080))
		assertEquals(720 to 720, frameSize(1, 1, 720))
		assertEquals(960 to 720, frameSize(4, 3, 720))
		assertEquals(0, frameSize(21, 9, 1081).first % 2)
	}

	@Test
	fun previewCropsOnlyWhenTheExportIsNarrower() {
		val vertical = previewCrop(16f / 9f, 9f / 16f)
		assertEquals(9f / 16f, vertical.aspect)
		assertEquals(1f, vertical.u0 + vertical.u1, 0.0001f)
		assertTrue(vertical.u0 > 0.3f)
		assertEquals(Crop(0f, 1f, 16f / 9f), previewCrop(16f / 9f, 21f / 9f))
	}

	@Test
	fun hudFillsTheCroppedFrameAtExportSize() {
		val crop = previewCrop(16f / 9f, 9f / 16f)
		val vertical = hudArea(480, 270, crop, 640)
		assertEquals(640, vertical.height)
		assertEquals(360, vertical.width)
		assertEquals(270f / 640, vertical.scale, 0.0001f)
		assertEquals(crop.u0 * 480, vertical.x)
		assertEquals(HudArea(0f, 1f, 480, 270), hudArea(480, 270, previewCrop(16f / 9f, 21f / 9f), 270))
	}

	@Test
	fun concatListQuotesPaths() {
		val parts = listOf(Path.of("/a/0.mp4"), Path.of("/a/it's/1.mp4"))
		assertEquals("file '/a/0.mp4'\nfile '/a/it'\\''s/1.mp4'", Ffmpeg.concatList(parts))
	}

	@Test
	fun frameRateClampsAndFallsBackWhenEmpty() {
		assertEquals(120, frameRate("120", 60))
		assertEquals(24, frameRate("1", 60))
		assertEquals(9999, frameRate("9999", 60))
		assertEquals(60, frameRate("", 60))
	}

	@Test
	fun formatsMinutesAndSeconds() {
		assertEquals("0:00", formatTime(19))
		assertEquals("1:24", formatTime(1680))
	}

	@Test
	fun scanFindsClipsScreensAndActivity() {
		val path = Files.createTempDirectory("keyframe").resolve("2026-11-03_23-33-20-834.pbrm")
		val header = ReplayHeader(7, "26.3", UUID(0, 1), "Tester", true)
		ReplayWriter(path, header).use {
			it.write(ReplayFile.SNAPSHOT, 2, byteArrayOf(1))
			it.write(ReplayFile.MARKER, 5, byteArrayOf(ReplayFile.MARKER_START))
			repeat(4) { _ -> it.write(ReplayFile.PLAY_FRAME, 10, ByteArray(1)) }
			it.write(ReplayFile.SCREEN_TRANSITION, 12, "Inventory".toByteArray())
			it.write(ReplayFile.SCREEN_TRANSITION, 15, ByteArray(0))
			it.write(ReplayFile.MARKER, 20, byteArrayOf(ReplayFile.MARKER_STOP))
			it.write(ReplayFile.MARKER, 30, byteArrayOf(ReplayFile.MARKER_START))
			it.write(ReplayFile.PLAY_FRAME, 40, ByteArray(1000))
			it.write(ReplayFile.THUMBNAIL, 40, byteArrayOf(0, 0, 0, 35, 9, 9))
		}

		val info = scanRecording(path, 7)
		assertTrue(info.playable)
		assertEquals(listOf(Clip(5, 20), Clip(30, 40)), info.clips)
		assertEquals(listOf(Clip(12, 15)), info.screens)
		assertEquals(2, info.firstTick)
		assertEquals(40, info.lastTick)
		assertEquals(1f, info.activity[1])
		assertEquals(0.5f, info.activity[4])
		assertEquals(1f, activityPeak(info.activity, 5, 15))
		assertEquals(0f, activityPeak(info.activity, 20, 25))
		assertEquals(0f, activityPeak(info.activity, 1000, 1005))
		assertEquals(35, info.thumbnails.single().tick)
		assertEquals(listOf<Byte>(9, 9), info.thumbnails.single().jpeg.toList())
		assertEquals("Nov 3, 23:33", info.label)
		assertFalse(scanRecording(path, 8).playable)
	}
}
