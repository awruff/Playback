package org.polyfrost.keyframe.standalone.export

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExportScheduleTest {
	private val fpsValues = listOf(20, 24, 25, 30, 48, 50, 60, 120, 144, 240)
	private val nanosPerTickValues = listOf(50_000_000L, 66_666_666L, 100_000_000L, 1_000_000_000L)

	@Test
	fun `ticks sum exactly across tick rates`() {
		for (fps in fpsValues) for (nanosPerTick in nanosPerTickValues) {
			val schedule = TickSchedule(fps)
			val frames = 100_000L
			var ticks = 0L
			repeat(frames.toInt()) {
				val step = schedule.step(nanosPerTick)
				assertTrue(step == 0 || step == 1, "fps=$fps nanosPerTick=$nanosPerTick")
				ticks += step
			}
			assertEquals(frames * 1_000_000_000L / (fps * nanosPerTick), ticks, "fps=$fps nanosPerTick=$nanosPerTick")
		}
	}

	@Test
	fun `every frame ticks at 20 fps`() {
		val schedule = TickSchedule(20)
		repeat(100) { assertEquals(1, schedule.step(50_000_000L)) }
	}

	@Test
	fun `60 fps ticks on every third frame`() {
		val schedule = TickSchedule(60)
		assertEquals(listOf(0, 0, 1, 0, 0, 1), List(6) { schedule.step(50_000_000L) })
	}

	@Test
	fun `tick length is floored at 50 ms`() {
		val fast = TickSchedule(60)
		val floored = TickSchedule(60)
		repeat(1000) { assertEquals(floored.step(50_000_000L), fast.step(10_000_000L)) }
	}

	@Test
	fun `speed scales ticks per frame`() {
		val fast = TickSchedule(20)
		repeat(100) { assertEquals(4, fast.step(50_000_000L, 4f)) }
		val slow = TickSchedule(20)
		assertEquals(listOf(0, 0, 0, 1, 0, 0, 0, 1), List(8) { slow.step(50_000_000L, 0.25f) })
		val sixty = TickSchedule(60)
		assertEquals(40, List(60) { sixty.step(50_000_000L, 2f) }.sum())
	}

	@Test
	fun `export frames follow clip speed`() {
		assertEquals(60, exportFrames(20, 60, 1f))
		assertEquals(120, exportFrames(20, 60, 0.5f))
		assertEquals(15, exportFrames(20, 60, 4f))
	}

	@Test
	fun `partial tick stays inside a tick and advances with frames`() {
		val schedule = TickSchedule(40)
		schedule.step(50_000_000L)
		assertEquals(0.5f, schedule.partial())
		schedule.step(50_000_000L)
		assertEquals(0f, schedule.partial())
	}

	@Test
	fun `tick rate change keeps partial below one`() {
		val schedule = TickSchedule(60)
		repeat(50) { schedule.step(1_000_000_000L) }
		repeat(50) {
			assertTrue(schedule.step(50_000_000L) in 0..1)
			assertTrue(schedule.partial() in 0f..1f)
		}
	}

	@Test
	fun `audio samples sum exactly`() {
		for (fps in fpsValues) {
			val frames = 100_000L
			var samples = 0L
			for (frame in 0 until frames) {
				val count = audioSamples(frame, fps)
				assertTrue(count == 48_000 / fps || count == 48_000 / fps + 1, "fps=$fps frame=$frame")
				samples += count
			}
			assertEquals(frames * 48_000 / fps, samples, "fps=$fps")
		}
	}

	@Test
	fun `frame time has no drift`() {
		for (fps in fpsValues) {
			assertEquals(1_000_000_000L, frameNanos(fps.toLong(), fps))
			assertEquals(3_600_000_000_000L, frameNanos(3600L * fps, fps))
		}
	}

	@Test
	fun `pre-roll is capped at 40 ticks`() {
		assertEquals(0, preRollFrames(0, 60))
		assertEquals(30, preRollFrames(10, 60))
		assertEquals(120, preRollFrames(40, 60))
		assertEquals(120, preRollFrames(5000, 60))
	}
}
