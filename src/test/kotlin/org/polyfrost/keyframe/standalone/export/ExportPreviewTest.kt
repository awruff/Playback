package org.polyfrost.keyframe.standalone.export

import kotlin.test.Test
import kotlin.test.assertEquals

class ExportPreviewTest {
	private fun nv12(width: Int, height: Int, r: Int, g: Int, b: Int): ByteArray {
		val luma = 0.2126 * r + 0.7152 * g + 0.0722 * b
		val y = Math.round(16 + 219 * luma / 255).toByte()
		val u = Math.round(128 + 224 * (b - luma) / 255 / 1.8556).toByte()
		val v = Math.round(128 + 224 * (r - luma) / 255 / 1.5748).toByte()
		return ByteArray(width * height) { y } + ByteArray(width * height / 2) { if (it % 2 == 0) u else v }
	}

	@Test
	fun nv12PreviewDecodesBackToTheColor() {
		for ((r, g, b) in listOf(Triple(0, 0, 0), Triple(255, 255, 255), Triple(200, 40, 90), Triple(10, 180, 250))) {
			val abgr = nv12Pixel(nv12(4, 2, r, g, b), 4, 2, 3, 1)
			assertEquals(r.toDouble(), (abgr and 0xFF).toDouble(), 2.0, "red of $r,$g,$b")
			assertEquals(g.toDouble(), (abgr shr 8 and 0xFF).toDouble(), 2.0, "green of $r,$g,$b")
			assertEquals(b.toDouble(), (abgr shr 16 and 0xFF).toDouble(), 2.0, "blue of $r,$g,$b")
		}
	}

	@Test
	fun wideFrameLetterboxes() {
		assertEquals(Fit(0, 60, 800, 450), fit(1920, 1080, 800, 570))
	}

	@Test
	fun tallFramePillarboxes() {
		assertEquals(Fit(240, 0, 320, 480), fit(1080, 1620, 800, 480))
	}

	@Test
	fun statusShowsRateProgressAndTimeLeft() {
		assertEquals(
			listOf("12.5 fps", "25% done (750/3000 frames)", "3:00 left", "render 40 ms, readback 20 ms, ffmpeg 10 ms"),
			statusLines(750, 3000, 12.5, 12.5, 40.0, 20.0, 10.0),
		)
	}

	@Test
	fun statusOmitsTimeLeftWithoutARate() {
		assertEquals(3, statusLines(0, 100, 0.0, 0.0, 0.0, 0.0, 0.0).size)
	}
}
