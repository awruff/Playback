package org.polyfrost.keyframe.standalone.recording

import kotlin.test.Test
import kotlin.test.assertEquals

class ShadowReplayTest {
	@Test
	fun separateRequestsStaySeparate() {
		assertEquals(listOf(Clip(0, 10), Clip(20, 30)), mergeClip(listOf(Clip(0, 10)), Clip(20, 30)))
	}

	@Test
	fun overlappingRequestsMerge() {
		assertEquals(listOf(Clip(0, 25)), mergeClip(listOf(Clip(0, 15)), Clip(10, 25)))
		assertEquals(listOf(Clip(5, 20)), mergeClip(listOf(Clip(5, 20)), Clip(10, 15)))
	}

	@Test
	fun touchingRequestsMerge() {
		assertEquals(listOf(Clip(0, 20)), mergeClip(listOf(Clip(0, 10)), Clip(10, 20)))
	}

	@Test
	fun requestBridgesTwoHeldClips() {
		assertEquals(listOf(Clip(100, 110), Clip(0, 50)), mergeClip(listOf(Clip(0, 10), Clip(40, 50), Clip(100, 110)), Clip(5, 45)))
	}

	@Test
	fun closeClampsToCurrentTick() {
		assertEquals(listOf(Clip(0, 10), Clip(20, 30)), clipsAtClose(listOf(Clip(0, 10), Clip(20, 90)), 30))
	}

	@Test
	fun closeDropsClipsThatHaveNotStarted() {
		assertEquals(emptyList(), clipsAtClose(listOf(Clip(30, 90), Clip(40, 90)), 30))
	}
}
