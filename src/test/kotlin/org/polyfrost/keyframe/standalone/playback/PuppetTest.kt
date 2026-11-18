package org.polyfrost.keyframe.standalone.playback

import org.polyfrost.keyframe.standalone.recording.ReplayFile
import org.polyfrost.keyframe.standalone.recording.ReplayRecord
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val OLD_LAYOUT_TRAILER = 20

class PuppetTest {
	private val snapshot = Snapshot(
		1.5, 64.0, -3.25, 370f, -20f, 0.1, -0.08, 0.2, 5, 0x2A5, 3, 17, 9, 4,
		0.5f, 1.5f, 0.25f, 1.25f, 0.1f, 42.5f,
		2, 6, 3, 0.25f, 0.5f, 0.75f, 0.35f, 1f, 0.6f,
		listOf(SlotChange(0, byteArrayOf(1, 2, 3)), SlotChange(40, ByteArray(0))),
		ScreenEffects(123456, 0.5f, 0.4875f, 812.5f, 18.25f),
	)

	@Test
	fun screenEffectsRoundTrip() {
		assertEquals(snapshot.effects, decodeSnapshot(encodeSnapshot(snapshot)).effects)
	}

	@Test
	fun oldLayoutSnapshotDecodesWithoutEffects() {
		val payload = encodeSnapshot(snapshot)
		val decoded = decodeSnapshot(payload.copyOf(payload.size - OLD_LAYOUT_TRAILER))
		assertNull(decoded.effects)
		assertEquals(snapshot.copy(slots = listOf(), effects = null), decoded.copy(slots = listOf()))
		assertEquals(listOf(0, 40), decoded.slots.map { it.slot })
	}

	@Test
	fun portalEntryIsOnlyTheFirstRisingTick() {
		assertTrue(ScreenEffects(0, 0.0125f, 0f, 0f, 20f).entersPortal)
		assertFalse(ScreenEffects(0, 0.025f, 0.0125f, 0f, 20f).entersPortal)
		assertFalse(ScreenEffects(0, 0f, 0f, 0f, 0f).entersPortal)
	}

	@Test
	fun snapshotRoundTrip() {
		val decoded = decodeSnapshot(encodeSnapshot(snapshot))
		assertEquals(snapshot.copy(slots = listOf()), decoded.copy(slots = listOf()))
		assertEquals(listOf(0, 40), decoded.slots.map { it.slot })
		assertContentEquals(byteArrayOf(1, 2, 3), decoded.slots[0].stack)
		assertContentEquals(ByteArray(0), decoded.slots[1].stack)
	}

	@Test
	fun truncatedSnapshotFails() {
		val payload = encodeSnapshot(snapshot)
		assertFailsWith<IOException> { decodeSnapshot(payload.copyOf(payload.size - 2)) }
		assertFailsWith<IOException> { decodeSnapshot(payload.copyOf(20)) }
	}

	@Test
	fun cameraRoundTrip() {
		val sample = decodeCamera(ReplayRecord(ReplayFile.CAMERA, 7, encodeCamera(0.5f, 90f, -10f)))!!
		assertEquals(7.5, sample.time)
		assertEquals(90f, sample.yaw)
		assertEquals(-10f, sample.pitch)
	}

	@Test
	fun cameraKeepsFovAndReadsOldSamples() {
		assertEquals(0.4f, decodeCamera(ReplayRecord(ReplayFile.CAMERA, 7, encodeCamera(0.5f, 90f, -10f, 0.4f)))!!.fov)
		val old = decodeCamera(ReplayRecord(ReplayFile.CAMERA, 7, encodeCamera(0.5f, 90f, -10f).copyOf(12)))!!
		assertEquals(90f, old.yaw)
		assertTrue(old.fov.isNaN())
	}

	@Test
	fun otherRecordsAreNotCameraSamples() {
		assertNull(decodeCamera(ReplayRecord(ReplayFile.SNAPSHOT, 7, encodeCamera(0f, 0f, 0f))))
		assertNull(decodeCamera(ReplayRecord(ReplayFile.CAMERA, 7, ByteArray(3))))
	}

	@Test
	fun cameraInterpolatesBetweenSamples() {
		val track = track(CameraSample(10.0, 0f, 40f), CameraSample(10.5, 100f, 20f), CameraSample(11.25, 400f, 20f))
		assertCamera(50f, 30f, track.at(10.25))
		assertCamera(100f, 20f, track.at(10.5))
		assertCamera(300f, 20f, track.at(11.0))
	}

	@Test
	fun cameraHoldsOutsideTheTrack() {
		val track = track(CameraSample(10.0, 5f, 6f), CameraSample(11.0, 7f, 8f))
		assertCamera(5f, 6f, track.at(9.0))
		assertCamera(7f, 8f, track.at(11.0))
		assertCamera(7f, 8f, track.at(500.0))
	}

	@Test
	fun cameraSkipsAheadAfterFastForward() {
		val samples = List(1000) { CameraSample(it.toDouble(), it.toFloat(), 0f) }.iterator()
		val track = SampleTrack(::interpolateCamera) { if (samples.hasNext()) samples.next() else null }
		assertCamera(800.5f, 0f, track.at(800.5))
	}

	@Test
	fun repeatedSampleTimesDoNotDivideByZero() {
		val track = track(CameraSample(10.0, 1f, 0f), CameraSample(10.0, 2f, 0f))
		assertCamera(2f, 0f, track.at(10.0))
	}

	@Test
	fun emptyTrackLeavesThePlayerAlone() {
		assertNull(track().at(5.0))
	}

	private fun track(vararg samples: CameraSample): SampleTrack<CameraSample> {
		val remaining = samples.iterator()
		return SampleTrack(::interpolateCamera) { if (remaining.hasNext()) remaining.next() else null }
	}

	private fun assertCamera(yaw: Float, pitch: Float, sample: CameraSample?) {
		assertEquals(yaw, sample!!.yaw)
		assertEquals(pitch, sample.pitch)
	}
}
