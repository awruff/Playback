package org.polyfrost.keyframe.standalone

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChannelsTest {
	@Test
	fun roundTripsValuesAndRemovals() {
		assertEquals("evergreenhud:huds/fps.json:text" to "1144", decodeChannel(encodeChannel("evergreenhud:huds/fps.json:text", "1144")))
		assertEquals("a" to null, decodeChannel(encodeChannel("a", null)))
		assertEquals("a" to "", decodeChannel(encodeChannel("a", "")))
		val art = "x".repeat(200_000)
		assertEquals("art" to art, decodeChannel(encodeChannel("art", art)))
	}

	@Test
	fun rejectsTruncatedPayloads() {
		assertNull(decodeChannel(encodeChannel("a", "value").copyOf(8)))
	}

	@Test
	fun samplesOnlyChangedValues() {
		var value: String? = "1"
		val written = ArrayList<Pair<String, String?>?>()
		Channels.register("test") { value }
		try {
			Channels.resetWritten()
			repeat(2) { Channels.sample { written += decodeChannel(it) } }
			value = null
			Channels.sample { written += decodeChannel(it) }
			assertEquals(listOf<Pair<String, String?>?>("test" to "1", "test" to null), written)
		} finally {
			Channels.unregister("test")
		}
	}
}
