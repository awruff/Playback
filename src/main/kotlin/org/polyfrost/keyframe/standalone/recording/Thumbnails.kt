package org.polyfrost.keyframe.standalone.recording

import com.mojang.blaze3d.systems.RenderSystem
//? if >=1.21.8 {
import com.mojang.renderpearl.api.buffers.GpuBuffer
import com.mojang.renderpearl.api.commands.GpuFence
//?}
import net.minecraft.client.Minecraft
import org.polyfrost.keyframe.standalone.Standalone
import org.polyfrost.keyframe.standalone.compat.*
import org.polyfrost.keyframe.standalone.editor.TICKS_PER_SECOND
import org.polyfrost.keyframe.standalone.export.Export
import org.slf4j.LoggerFactory
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import javax.imageio.ImageIO
import kotlin.concurrent.thread

internal const val THUMBNAIL_TICKS = 5 * TICKS_PER_SECOND
private const val THUMBNAIL_HEIGHT = 72
private const val MAX_THUMBNAIL_WIDTH = 160
private const val SAMPLES = 3

class Thumbnail(val tick: Int, val jpeg: ByteArray)

internal fun decodeThumbnail(payload: ByteArray): Thumbnail? {
	if (payload.size <= Int.SIZE_BYTES) return null
	return Thumbnail(ByteBuffer.wrap(payload).int, payload.copyOfRange(Int.SIZE_BYTES, payload.size))
}

internal fun thumbnailSize(width: Int, height: Int): Pair<Int, Int> =
	minOf(MAX_THUMBNAIL_WIDTH, maxOf(1, width * THUMBNAIL_HEIGHT / maxOf(height, 1))) to THUMBNAIL_HEIGHT

internal fun downscale(pixels: ByteBuffer, width: Int, height: Int, outWidth: Int, outHeight: Int): IntArray {
	val out = IntArray(outWidth * outHeight)
	for (y in 0 until outHeight) for (x in 0 until outWidth) {
		var r = 0
		var g = 0
		var b = 0
		for (sy in 0 until SAMPLES) for (sx in 0 until SAMPLES) {
			val px = ((x * SAMPLES + sx) * width.toLong() / (outWidth * SAMPLES)).toInt()
			val py = height - 1 - ((y * SAMPLES + sy) * height.toLong() / (outHeight * SAMPLES)).toInt()
			val i = (py * width + px) * 4
			r += pixels.get(i).toInt() and 0xFF
			g += pixels.get(i + 1).toInt() and 0xFF
			b += pixels.get(i + 2).toInt() and 0xFF
		}
		val n = SAMPLES * SAMPLES
		out[y * outWidth + x] = (r / n shl 16) or (g / n shl 8) or (b / n)
	}
	return out
}

object Thumbnails {
	private val LOGGER = LoggerFactory.getLogger(Standalone.MOD_ID)

	private class Pending(val buffer: GpuBuffer, val fence: GpuFence, val width: Int, val height: Int, val tick: Int, val done: (ByteArray) -> Unit)

	private var pending: Pending? = null
	private var failed = false

	fun tick(tick: Int, done: (ByteArray) -> Unit) {
		pending?.let(::collect)
		if (failed || pending != null || tick % THUMBNAIL_TICKS != 0 || Export.active || Minecraft.getInstance().level == null) return
		try {
			start(tick, done)
		} catch (e: RuntimeException) {
			LOGGER.warn("Could not capture recording thumbnails", e)
			failed = true
		}
	}

	private fun start(tick: Int, done: (ByteArray) -> Unit) {
		val texture = Minecraft.getInstance().gameRenderer.mainRenderTarget().colorTexture ?: return
		val width = texture.getWidth(0)
		val height = texture.getHeight(0)
		if (width <= 0 || height <= 0) return
		val device = RenderSystem.getDevice()
		val buffer = device.createBuffer(
			{ "Keyframe thumbnail" },
			GpuBuffer.USAGE_MAP_READ or GpuBuffer.USAGE_COPY_DST,
			//? if >=1.21.11 {
			width.toLong() * height * 4,
			//?} else
			/*width * height * 4,*/
		)
		val encoder = device.createCommandEncoder()
		//? if >=1.21.11 {
		encoder.copyTextureToBuffer(texture, buffer, 0L, Runnable {}, 0)
		//?} else
		/*encoder.copyTextureToBuffer(texture, buffer, 0, Runnable {}, 0)*/
		pending = Pending(buffer, encoder.createFence(), width, height, tick, done)
	}

	private fun collect(job: Pending) {
		if (!job.fence.awaitCompletion(0L)) return
		pending = null
		val (outWidth, outHeight) = thumbnailSize(job.width, job.height)
		val pixels = try {
			job.buffer.map(true, false).use { downscale(it.data(), job.width, job.height, outWidth, outHeight) }
		} finally {
			job.fence.close()
			job.buffer.close()
		}
		val mc = Minecraft.getInstance()
		thread(name = "Keyframe Thumbnail", isDaemon = true) {
			val image = BufferedImage(outWidth, outHeight, BufferedImage.TYPE_INT_RGB)
			image.setRGB(0, 0, outWidth, outHeight, pixels, 0, outWidth)
			val bytes = ByteArrayOutputStream()
			DataOutputStream(bytes).writeInt(job.tick)
			if (ImageIO.write(image, "jpg", bytes)) mc.execute { job.done(bytes.toByteArray()) }
		}
	}
}
