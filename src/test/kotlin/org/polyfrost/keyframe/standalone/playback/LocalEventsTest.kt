package org.polyfrost.keyframe.standalone.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LocalEventsTest {
	@Test
	fun `events survive encoding`() {
		val events = listOf(
			LocalSound(1.5, 64.0, -3.25, "minecraft:block.stone.step", "PLAYERS", 0.15f, 1f, -42L),
			LocalLevelEvent(2001, 123456789L, 77),
			LocalBreaking(-987654321L, 3, true),
			LocalEntitySound(1234, "minecraft:item.goat_horn.sound.0", "RECORDS", 16f, 1f, 99L),
			LocalUiSound("minecraft:ui.button.click", 0.25f, 1.5f),
			LocalUiLoopStart(7, "oneconfig:ambience", 0.5f, 0.75f, true),
			LocalUiLoopVolume(7, 0.125f),
			LocalUiLoopStop(7),
		)
		for (event in events) assertEquals(event, decodeEvent(encodeEvent(event)))
	}

	@Test
	fun `unknown event kinds are skipped`() {
		assertNull(decodeEvent(byteArrayOf(99)))
	}

	@Test
	fun `watch reports volume changes and stops by identity`() {
		val watch = UiLoopWatch<StringBuilder>()
		val first = StringBuilder("same")
		val second = StringBuilder("same")
		val volumes = mutableMapOf(first to 1f)
		val active = mutableSetOf(first)
		fun poll() = watch.poll({ it in active }, { volumes.getValue(it) })
		val firstId = watch.start(first, 1f)
		val secondId = watch.start(second, 1f)
		assertEquals(firstId, watch.start(first, 1f))
		active += second
		volumes[second] = 1f

		assertEquals(emptyList(), poll())
		volumes[first] = 0.5f
		assertEquals(listOf<LocalEvent>(LocalUiLoopVolume(firstId, 0.5f)), poll())
		active -= first
		assertEquals(listOf<LocalEvent>(LocalUiLoopStop(firstId)), poll())
		assertEquals(emptyList(), poll())
		active -= second
		assertEquals(listOf<LocalEvent>(LocalUiLoopStop(secondId)), poll())
	}

	private class FakeSound(val id: Int, var volume: Float, var stopped: Boolean = false)

	private class Loops {
		val started = mutableListOf<FakeSound>()
		val player = UiLoopPlayer<FakeSound>({ FakeSound(it.id, it.volume).also(started::add) }, { it.stopped = true }, { sound, volume -> sound.volume = volume })
	}

	private fun start(id: Int, volume: Float = 1f) = LocalUiLoopStart(id, "minecraft:loop", volume, 1f, true)

	@Test
	fun `a roll starts only loops still live when it settles`() {
		val loops = Loops()
		loops.player.track(start(1))
		loops.player.track(start(2, 0.2f))
		loops.player.track(LocalUiLoopVolume(2, 0.8f))
		loops.player.track(LocalUiLoopStop(1))
		assertEquals(emptyList(), loops.started)

		loops.player.settle()
		assertEquals(listOf(2), loops.started.map { it.id })
		assertEquals(0.8f, loops.started.single().volume)
	}

	@Test
	fun `a seek stops playing loops that ended and keeps the rest`() {
		val loops = Loops()
		loops.player.track(start(1))
		loops.player.track(start(2))
		loops.player.settle()
		val (first, second) = loops.started

		loops.player.track(LocalUiLoopStop(1))
		loops.player.track(LocalUiLoopVolume(2, 0.5f))
		loops.player.settle()
		assertEquals(true, first.stopped)
		assertEquals(false, second.stopped)
		assertEquals(0.5f, second.volume)
		assertEquals(2, loops.started.size)
	}

	@Test
	fun `clearing stops everything and forgets it`() {
		val loops = Loops()
		loops.player.track(start(1))
		loops.player.settle()
		loops.player.track(start(2))
		loops.player.clear()
		loops.player.settle()
		assertEquals(true, loops.started.single().stopped)
		assertEquals(1, loops.started.size)
	}

	@Test
	fun `volume for an unknown loop is ignored`() {
		val loops = Loops()
		loops.player.track(LocalUiLoopVolume(5, 0.5f))
		loops.player.settle()
		assertEquals(emptyList(), loops.started)
	}
}
