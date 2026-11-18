package org.polyfrost.keyframe.standalone.recording

import org.polyfrost.keyframe.standalone.decodeChannel
import org.polyfrost.keyframe.standalone.encodeChannel
import org.polyfrost.keyframe.standalone.playback.ScreenState
import org.polyfrost.keyframe.standalone.playback.decodeScreenState
import org.polyfrost.keyframe.standalone.playback.encodeScreenState
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.io.path.fileSize
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ReplayFileTest {
	private val header = ReplayHeader(775, "26.3", UUID(1, 2), "Alex", true)
	private val dir = createTempDirectory("keyframe-test")

	@AfterTest
	@OptIn(kotlin.io.path.ExperimentalPathApi::class)
	fun cleanUp() {
		dir.deleteRecursively()
	}

	@Test
	fun roundTrip() {
		val path = write("round.pbrm") { writer ->
			for (tick in 0 until 350) writer.write(tick % 9, tick, byteArrayOf(tick.toByte(), 7))
		}
		ReplayReader(path, header.protocol).use { reader ->
			assertEquals(header, reader.header)
			for (tick in 0 until 350) {
				val record = reader.next()!!
				assertEquals(tick % 9, record.type)
				assertEquals(tick, record.tick)
				assertContentEquals(byteArrayOf(tick.toByte(), 7), record.payload)
			}
			assertNull(reader.next())
		}
	}

	@Test
	fun screenStateRecordsSurviveTheFile() {
		val state = ScreenState(false, true, false, "chat", "", 0f, listOf("draft"), listOf(3.0))
		val path = write("state.pbrm") { it.write(ReplayFile.SCREEN_STATE, 4, encodeScreenState(state)) }
		ReplayReader(path, header.protocol).use { reader ->
			val record = reader.next()!!
			assertEquals(12, record.type)
			assertEquals(state, decodeScreenState(record.payload))
		}
	}

	@Test
	fun channelRecordsSurviveTheFile() {
		val path = write("channel.pbrm") { it.write(ReplayFile.CHANNEL, 4, encodeChannel("evergreenhud:huds/fps.json:text", "160")) }
		ReplayReader(path, header.protocol).use { reader ->
			val record = reader.next()!!
			assertEquals(13, record.type)
			assertEquals("evergreenhud:huds/fps.json:text" to "160", decodeChannel(record.payload))
		}
	}

	@Test
	fun thumbnailRecordsSurviveTheFile() {
		val path = write("thumbnail.pbrm") { it.write(ReplayFile.THUMBNAIL, 9, byteArrayOf(0, 0, 0, 7, 1, 2, 3)) }
		ReplayReader(path, header.protocol).use { reader ->
			val record = reader.next()!!
			assertEquals(14, record.type)
			assertEquals(ReplayFile.LAST_TYPE, record.type)
			val thumbnail = decodeThumbnail(record.payload)!!
			assertEquals(7, thumbnail.tick)
			assertEquals(listOf<Byte>(1, 2, 3), thumbnail.jpeg.toList())
		}
	}

	@Test
	fun lateStampKeepsOrder() {
		val path = write("late.pbrm") { writer ->
			writer.write(ReplayFile.PLAY_FRAME, 5, byteArrayOf(1))
			writer.write(ReplayFile.MARKER, 4, byteArrayOf(2))
		}
		ReplayReader(path, header.protocol).use { reader ->
			assertEquals(5, reader.next()!!.tick)
			assertEquals(5, reader.next()!!.tick)
		}
	}

	@Test
	fun protocolMismatchIsRefused() {
		val path = write("protocol.pbrm") {}
		assertFailsWith<IOException> { ReplayReader(path, header.protocol + 1) }
	}

	@Test
	fun truncatedChunkEndsAtLastGoodChunk() {
		val path = threeChunks("truncated.pbrm")
		Files.write(path, Files.readAllBytes(path).copyOf(path.fileSize().toInt() - 3))
		assertEquals((0 until 202).toList(), readAll(path))
	}

	@Test
	fun badCrcStopsReading() {
		val path = threeChunks("crc.pbrm")
		val bytes = Files.readAllBytes(path)
		val second = chunkOffset(bytes, 1)
		bytes[second + 16] = (bytes[second + 16].toInt() xor 0x55).toByte()
		Files.write(path, bytes)
		assertEquals((0 until 101).toList(), readAll(path))
	}

	@Test
	fun unknownTypeIsSkipped() {
		val path = write("unknown.pbrm") { writer ->
			writer.write(ReplayFile.PLAY_FRAME, 1, byteArrayOf(1))
			writer.write(200, 3, byteArrayOf(2, 3, 4))
			writer.write(ReplayFile.PLAY_FRAME, 4, byteArrayOf(5))
		}
		ReplayReader(path, header.protocol).use { reader ->
			assertContentEquals(byteArrayOf(1), reader.next()!!.payload)
			val after = reader.next()!!
			assertEquals(4, after.tick)
			assertContentEquals(byteArrayOf(5), after.payload)
			assertNull(reader.next())
		}
	}

	@Test
	fun lastClipPicksNewestPair() {
		val path = write("clips.pbrm") { writer ->
			writer.write(ReplayFile.MARKER, 10, byteArrayOf(ReplayFile.MARKER_START))
			writer.write(ReplayFile.MARKER, 20, byteArrayOf(ReplayFile.MARKER_STOP))
			writer.write(ReplayFile.MARKER, 30, byteArrayOf(ReplayFile.MARKER_START))
			writer.write(ReplayFile.MARKER, 45, byteArrayOf(ReplayFile.MARKER_STOP))
			writer.write(ReplayFile.PLAY_FRAME, 50, byteArrayOf(1))
		}
		assertEquals(Clip(30, 45), ReplayReader(path, header.protocol).use { it.lastClip() })
	}

	@Test
	fun lastClipRunsToEndWithoutStop() {
		val path = write("open.pbrm") { writer ->
			writer.write(ReplayFile.MARKER, 5, byteArrayOf(ReplayFile.MARKER_START))
			writer.write(ReplayFile.PLAY_FRAME, 9, byteArrayOf(1))
		}
		assertEquals(Clip(5, 9), ReplayReader(path, header.protocol).use { it.lastClip() })
	}

	@Test
	fun noMarkerMeansNoClip() {
		val path = write("none.pbrm") { it.write(ReplayFile.PLAY_FRAME, 9, byteArrayOf(1)) }
		assertNull(ReplayReader(path, header.protocol).use { it.lastClip() })
	}

	@Test
	fun clipRoundTrip() {
		val path = write("clip.pbrm") { it.write(ReplayFile.CLIP, 900, encodeClip(Clip(300, 900))) }
		ReplayReader(path, header.protocol).use { reader ->
			val record = reader.next()!!
			assertEquals(ReplayFile.CLIP, record.type)
			assertEquals(900, record.tick)
			assertEquals(Clip(300, 900), decodeClip(record.payload))
			assertNull(reader.next())
		}
	}

	@Test
	fun typeAfterLastIsSkipped() {
		val path = write("future.pbrm") { writer ->
			writer.write(ReplayFile.LAST_TYPE + 1, 1, byteArrayOf(1, 2))
			writer.write(ReplayFile.LAST_TYPE, 2, encodeClip(Clip(0, 2)))
		}
		ReplayReader(path, header.protocol).use { reader ->
			assertEquals(ReplayFile.LAST_TYPE, reader.next()!!.type)
			assertNull(reader.next())
		}
	}

	@Test
	fun lastClipPicksClipAfterMarkers() {
		val path = write("clip-last.pbrm") { writer ->
			writer.write(ReplayFile.MARKER, 10, byteArrayOf(ReplayFile.MARKER_START))
			writer.write(ReplayFile.MARKER, 20, byteArrayOf(ReplayFile.MARKER_STOP))
			writer.write(ReplayFile.CLIP, 500, encodeClip(Clip(5, 500)))
			writer.write(ReplayFile.PLAY_FRAME, 510, byteArrayOf(1))
		}
		assertEquals(Clip(5, 500), ReplayReader(path, header.protocol).use { it.lastClip() })
	}

	@Test
	fun lastClipKeepsOpenRecordingOverClip() {
		val path = write("clip-open.pbrm") { writer ->
			writer.write(ReplayFile.MARKER, 10, byteArrayOf(ReplayFile.MARKER_START))
			writer.write(ReplayFile.CLIP, 20, encodeClip(Clip(5, 20)))
			writer.write(ReplayFile.PLAY_FRAME, 30, byteArrayOf(1))
		}
		assertEquals(Clip(10, 30), ReplayReader(path, header.protocol).use { it.lastClip() })
	}

	@Test
	fun clipSurvivesCrashBeforeChunkRoll() {
		val path = write("clip-crash.pbrm") { writer ->
			writer.write(ReplayFile.CLIP, 2, encodeClip(Clip(1, 2)))
			writer.write(ReplayFile.PLAY_FRAME, 3, byteArrayOf(1))
		}
		Files.write(path, Files.readAllBytes(path).copyOf(path.fileSize().toInt() - 3))
		assertEquals(Clip(1, 2), ReplayReader(path, header.protocol).use { it.lastClip() })
	}

	@Test
	fun scanListsClipsFromFirstSnapshot() {
		val path = write("clip-scan.pbrm") { writer ->
			writer.write(ReplayFile.SNAPSHOT, 40, byteArrayOf(1))
			writer.write(ReplayFile.CLIP, 100, encodeClip(Clip(0, 100)))
			writer.write(ReplayFile.CLIP, 120, encodeClip(Clip(0, 30)))
			writer.write(ReplayFile.MARKER, 150, byteArrayOf(ReplayFile.MARKER_START))
			writer.write(ReplayFile.MARKER, 160, byteArrayOf(ReplayFile.MARKER_STOP))
		}
		assertEquals(listOf(Clip(40, 100), Clip(150, 160)), scanRecording(path, header.protocol).clips)
	}

	@Test
	fun sessionBreakRoundTrip() {
		val path = write("break.pbrm") { it.write(ReplayFile.SESSION_BREAK, 12, ByteArray(0)) }
		ReplayReader(path, header.protocol).use { reader ->
			val record = reader.next()!!
			assertEquals(ReplayFile.SESSION_BREAK, record.type)
			assertEquals(12, record.tick)
			assertEquals(0, record.payload.size)
			assertNull(reader.next())
		}
	}

	@Test
	fun clipSpansSessionBreak() {
		val path = write("break-clip.pbrm") { writer ->
			writer.write(ReplayFile.SNAPSHOT, 5, byteArrayOf(1))
			writer.write(ReplayFile.MARKER, 10, byteArrayOf(ReplayFile.MARKER_START))
			writer.write(ReplayFile.SESSION_BREAK, 100, ByteArray(0))
			writer.write(ReplayFile.SCREEN_TRANSITION, 100, "TitleScreen".toByteArray())
			writer.write(ReplayFile.CONFIG_FRAME, 300, byteArrayOf(2))
			writer.write(ReplayFile.PLAY_FRAME, 301, byteArrayOf(3))
			writer.write(ReplayFile.SNAPSHOT, 310, byteArrayOf(4))
			writer.write(ReplayFile.MARKER, 400, byteArrayOf(ReplayFile.MARKER_STOP))
		}
		assertEquals(listOf(Clip(10, 400)), scanRecording(path, header.protocol).clips)
		assertEquals(Clip(10, 400), ReplayReader(path, header.protocol).use { it.lastClip() })
	}

	@Test
	fun clipEndsInMenuAfterSessionBreak() {
		val path = write("break-open.pbrm") { writer ->
			writer.write(ReplayFile.SNAPSHOT, 5, byteArrayOf(1))
			writer.write(ReplayFile.MARKER, 10, byteArrayOf(ReplayFile.MARKER_START))
			writer.write(ReplayFile.SESSION_BREAK, 100, ByteArray(0))
			writer.write(ReplayFile.INPUT, 150, byteArrayOf(2))
		}
		assertEquals(listOf(Clip(10, 150)), scanRecording(path, header.protocol).clips)
		assertEquals(Clip(10, 150), ReplayReader(path, header.protocol).use { it.lastClip() })
	}

	@Test
	fun highlightRoundTrip() {
		val path = write("highlight.pbrm") { writer ->
			writer.write(ReplayFile.HIGHLIGHT, 7, encodeHighlight(0xFFAA00, "Combo"))
		}
		ReplayReader(path, header.protocol).use { reader ->
			val record = reader.next()!!
			assertEquals(ReplayFile.HIGHLIGHT, record.type)
			assertEquals(Highlight(7, 0xFFAA00, "Combo"), decodeHighlight(record.tick, record.payload))
			assertNull(reader.next())
		}
	}

	@Test
	fun scanListsHighlights() {
		val path = write("highlight-scan.pbrm") { writer ->
			writer.write(ReplayFile.SNAPSHOT, 1, byteArrayOf(1))
			writer.write(ReplayFile.HIGHLIGHT, 50, encodeHighlight(0xFF5555, "Kill"))
			writer.write(ReplayFile.HIGHLIGHT, 90, encodeHighlight(0x55FFFF, "Bed broken"))
		}
		val info = scanRecording(path, header.protocol)
		assertEquals(listOf(Highlight(50, 0xFF5555, "Kill"), Highlight(90, 0x55FFFF, "Bed broken")), info.highlights)
		assertEquals(emptyList(), info.clips)
	}

	private fun threeChunks(name: String) = write(name) { writer ->
		for (tick in 0 until 303) writer.write(ReplayFile.PLAY_FRAME, tick, byteArrayOf(tick.toByte()))
	}

	private fun chunkOffset(bytes: ByteArray, index: Int): Int {
		var offset = write("empty-${bytes.size}.pbrm") {}.fileSize().toInt()
		repeat(index) { offset += 16 + ByteBuffer.wrap(bytes).getInt(offset) }
		return offset
	}

	private fun readAll(path: Path): List<Int> = ReplayReader(path, header.protocol).use { reader ->
		generateSequence { reader.next()?.tick }.toList()
	}

	private fun write(name: String, block: (ReplayWriter) -> Unit): Path {
		val path = dir.resolve(name)
		ReplayWriter(path, header).use(block)
		return path
	}
}
