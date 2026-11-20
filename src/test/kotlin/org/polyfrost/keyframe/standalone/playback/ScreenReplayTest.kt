package org.polyfrost.keyframe.standalone.playback

import org.polyfrost.keyframe.standalone.recording.ReplayFile
import org.polyfrost.keyframe.standalone.recording.ReplayRecord
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScreenReplayTest {
	@Test
	fun commandsAreRedacted() {
		assertEquals("/", redactCommand("/login hunter2"))
		assertEquals("hello /there", redactCommand("hello /there"))
	}

	@Test
	fun cursorRoundTrip() {
		val sample = decodeCursor(ReplayRecord(ReplayFile.INPUT, 7, encodeCursor(0.5f, -12.25f, 40f, 3)))!!
		assertEquals(7.5, sample.time)
		assertEquals(-12.25f, sample.x)
		assertEquals(40f, sample.y)
		assertEquals(3, sample.guiScale)
	}

	@Test
	fun otherRecordsAreNotCursorSamples() {
		assertNull(decodeCursor(ReplayRecord(ReplayFile.CAMERA, 7, encodeCursor(0f, 0f, 0f, 1))))
		assertNull(decodeCursor(ReplayRecord(ReplayFile.INPUT, 7, ByteArray(3))))
	}

	@Test
	fun cursorInterpolatesBetweenSamples() {
		val samples = listOf(CursorSample(10.0, 0f, 40f, 2), CursorSample(11.0, 100f, 20f, 3)).iterator()
		val track = SampleTrack(::interpolateCursor) { if (samples.hasNext()) samples.next() else null }
		val middle = track.at(10.5)!!
		assertEquals(50f, middle.x)
		assertEquals(30f, middle.y)
		assertEquals(3, middle.guiScale)
	}

	private val state = ScreenState(
		debugOverlay = true,
		tabList = false,
		hideGui = true,
		screen = "net.minecraft.client.gui.screens.ChatScreen",
		creativeTab = "minecraft:search",
		creativeScroll = 0.25f,
		editBoxes = listOf("héllo /gamemode", ""),
		scrollAreas = listOf(12.5, 0.0),
	)

	@Test
	fun screenStateRoundTrip() {
		assertEquals(state, decodeScreenState(encodeScreenState(state)))
		val empty = ScreenState(false, false, false, "", "", 0f, emptyList(), emptyList())
		assertEquals(empty, decodeScreenState(encodeScreenState(empty)))
	}

	@Test
	fun malformedScreenStateIsDropped() {
		val payload = encodeScreenState(state)
		assertNull(decodeScreenState(payload.copyOf(payload.size - 1)))
		assertNull(decodeScreenState(ByteArray(0)))
		assertNull(decodeScreenState(ByteBuffer.allocate(5).put(0).putInt(-1).array()))
		assertNull(decodeScreenState(ByteBuffer.allocate(21).put(0).putInt(0).putInt(0).putFloat(0f).putInt(Int.MAX_VALUE).array()))
	}

	@Test
	fun unchangedPayloadsAreSkippedUntilReset() {
		val changes = ChangedPayload()
		assertTrue(changes.changed(byteArrayOf(1, 2)))
		assertFalse(changes.changed(byteArrayOf(1, 2)))
		assertTrue(changes.changed(byteArrayOf(1, 3)))
		changes.reset()
		assertTrue(changes.changed(byteArrayOf(1, 3)))
	}

	@Test
	fun flattenedKeepsNestedWidgetsInWalkOrder() {
		val tree = mapOf("root" to listOf("list", "box"), "list" to listOf("nested"))
		assertEquals(listOf("root", "list", "nested", "box"), flattened(listOf("root")) { tree[it].orEmpty() })
	}

	class Rebuilt(val parent: String) {
		constructor(name: Int, flag: Boolean, unknown: List<String>) : this("$name $flag $unknown")
		constructor(name: Int, flag: Boolean) : this("$name $flag")
	}

	abstract class Unbuildable

	@Test
	fun constructPicksTheSmallestConstructorItCanFill() {
		assertEquals("6 false", (construct(Rebuilt::class.java, listOf(6, false)) as Rebuilt).parent)
		assertEquals("title", (construct(Rebuilt::class.java, listOf("title", 6, false)) as Rebuilt).parent)
	}

	@Test
	fun constructGivesUpWithoutMatchingArguments() {
		assertNull(construct(Rebuilt::class.java, listOf(6)))
		assertNull(construct(Unbuildable::class.java, listOf(6)))
	}

	@Test
	fun xcursorPicksTheNearestSizeAndUnpremultiplies() {
		val small = xcursorImage(1, 1, 3, 4, 0x80402010.toInt())
		val large = xcursorImage(2, 1, 9, 9, 0, 0)
		val data = ByteBuffer.allocate(16 + 24 + small.size + large.size).order(ByteOrder.LITTLE_ENDIAN)
			.putInt(0x72756358).putInt(16).putInt(0x10000).putInt(2)
			.putInt(0xFFFD0002.toInt()).putInt(24).putInt(40)
			.putInt(0xFFFD0002.toInt()).putInt(48).putInt(40 + small.size)
			.put(small).put(large).array()

		val cursor = parseXcursor(data, 32)!!
		assertEquals(1, cursor.width)
		assertEquals(3, cursor.hotX)
		assertEquals(4, cursor.hotY)
		assertContentEquals(intArrayOf(0x807F3F1F.toInt()), cursor.argb)
	}

	@Test
	fun notAnXcursor() {
		assertNull(parseXcursor(ByteArray(32), 24))
	}

	@Test
	fun curRowsAreFlipped() {
		val data = ByteBuffer.allocate(22 + 40 + 8).order(ByteOrder.LITTLE_ENDIAN)
			.putShort(0).putShort(2).putShort(1)
			.put(1).put(2).put(0).put(0).putShort(5).putShort(6).putInt(48).putInt(22)
			.putInt(40).putInt(1).putInt(4).putShort(1).putShort(32).put(ByteArray(24))
			.putInt(0xFF000002.toInt()).putInt(0xFF000001.toInt()).array()

		val cursor = parseCur(data, 32)!!
		assertEquals(1, cursor.width)
		assertEquals(2, cursor.height)
		assertEquals(5, cursor.hotX)
		assertEquals(6, cursor.hotY)
		assertEquals(32f, cursor.scale)
		assertContentEquals(intArrayOf(0xFF000001.toInt(), 0xFF000002.toInt()), cursor.argb)
	}

	@Test
	fun curRejectsOtherFiles() {
		assertNull(parseCur(ByteArray(32), 32))
	}

	private fun xcursorImage(width: Int, height: Int, hotX: Int, hotY: Int, vararg pixels: Int): ByteArray {
		val image = ByteBuffer.allocate(36 + pixels.size * 4).order(ByteOrder.LITTLE_ENDIAN)
			.putInt(36).putInt(0xFFFD0002.toInt()).putInt(24).putInt(1)
			.putInt(width).putInt(height).putInt(hotX).putInt(hotY).putInt(0)
		pixels.forEach(image::putInt)
		return image.array()
	}
}
