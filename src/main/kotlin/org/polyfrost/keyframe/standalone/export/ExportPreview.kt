package org.polyfrost.keyframe.standalone.export

import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.systems.RenderSystem
import org.polyfrost.keyframe.standalone.compat.*
//? if >=26.2 {
import com.mojang.renderpearl.api.GpuFormat
//?} elif >=1.21.8 {
/*import com.mojang.renderpearl.api.textures.TextureFormat
*///?}
//? if >=1.21.8 {
import com.mojang.renderpearl.api.textures.GpuTexture
import com.mojang.renderpearl.api.textures.GpuTextureView
//?}
import net.minecraft.client.Minecraft
import kotlin.math.roundToInt

private const val BLACK = 0xFF000000.toInt()
private const val WHITE = -1
private const val MARGIN = 8

internal data class Fit(val x: Int, val y: Int, val width: Int, val height: Int)

internal fun fit(sourceWidth: Int, sourceHeight: Int, width: Int, height: Int): Fit {
	val fittedWidth = minOf(width.toLong(), height.toLong() * sourceWidth / sourceHeight).toInt()
	val fittedHeight = minOf(height.toLong(), width.toLong() * sourceHeight / sourceWidth).toInt()
	return Fit((width - fittedWidth) / 2, (height - fittedHeight) / 2, fittedWidth, fittedHeight)
}

internal fun statusLines(frame: Int, total: Int, fps: Double, framesLeftFps: Double, renderMs: Double, readbackMs: Double, ffmpegMs: Double): List<String> {
	val percent = frame * 100L / total
	val lines = mutableListOf("%.1f fps".format(fps), "$percent% done ($frame/$total frames)")
	if (framesLeftFps > 0) {
		val seconds = ((total - frame) / framesLeftFps).roundToInt()
		lines += "%d:%02d left".format(seconds / 60, seconds % 60)
	}
	lines += "render %.0f ms, readback %.0f ms, ffmpeg %.0f ms".format(renderMs, readbackMs, ffmpegMs)
	return lines
}

internal class ExportPreview(private val showFrame: Boolean) : AutoCloseable {
	private val lazyFont = lazy {
		runCatching {
			Minecraft.getInstance().resourceManager.open(identifier("minecraft", "textures/font/ascii.png")).use(NativeImage::read)
		}.getOrNull()
	}
	private var image: NativeImage? = null
	private var texture: GpuTexture? = null

	var view: GpuTextureView? = null
		private set

	fun draw(pixels: ByteArray, frameWidth: Int, frameHeight: Int, hasFrame: Boolean, nv12: Boolean, lines: List<String>) {
		//? if >=26.2 {
		val size = Minecraft.getInstance().windowSurface().currentConfiguration().orElse(null) ?: return
		//?} else
		/*val size = Minecraft.getInstance().window.queryFramebufferSize()*/
		val width = size.width()
		val height = size.height()
		if (width <= 0 || height <= 0) return
		val image = target(width, height)

		image.fillRect(0, 0, width, height, BLACK)
		if (showFrame && hasFrame) drawFrame(image, pixels, frameWidth, frameHeight, nv12)
		drawLines(image, lines)
		RenderSystem.getDevice().createCommandEncoder().writeToTexture(texture!!, image)
	}

	override fun close() {
		view?.close()
		texture?.close()
		image?.close()
		if (lazyFont.isInitialized()) lazyFont.value?.close()
	}

	private fun target(width: Int, height: Int): NativeImage {
		image?.takeIf { it.width == width && it.height == height }?.let { return it }
		view?.close()
		texture?.close()
		image?.close()

		val device = RenderSystem.getDevice()
		val usage = GpuTexture.USAGE_COPY_DST or GpuTexture.USAGE_COPY_SRC or GpuTexture.USAGE_TEXTURE_BINDING or GpuTexture.USAGE_RENDER_ATTACHMENT
		//? if >=26.2 {
		val texture = device.createTexture({ "Keyframe preview" }, usage, GpuFormat.RGBA8_UNORM, width, height, 1, 1)
		//?} else
		/*val texture = device.createTexture({ "Keyframe preview" }, usage, TextureFormat.RGBA8, width, height, 1, 1)*/
		this.texture = texture
		view = device.createTextureView(texture)
		return NativeImage(width, height, false).also { image = it }
	}

	private fun drawFrame(image: NativeImage, pixels: ByteArray, frameWidth: Int, frameHeight: Int, nv12: Boolean) {
		val area = fit(frameWidth, frameHeight, image.width, image.height)
		for (y in 0 until area.height) {
			val sourceY = (y.toLong() * frameHeight / area.height).toInt()
			for (x in 0 until area.width) {
				val sourceX = (x.toLong() * frameWidth / area.width).toInt()
				val abgr = if (nv12) nv12Pixel(pixels, frameWidth, frameHeight, sourceX, sourceY) else rgbPixel(pixels, frameWidth, sourceX, sourceY)
				image.setPixelABGR(area.x + x, area.y + y, abgr)
			}
		}
	}

	private fun drawLines(image: NativeImage, lines: List<String>) {
		val font = lazyFont.value ?: return
		val cell = font.width / 16
		val scale = maxOf(1, image.height / 270)
		val advance = cell * 3 / 4 * scale
		val lineHeight = (cell + 2) * scale
		val panelWidth = minOf(image.width, MARGIN * 2 + lines.maxOf { it.length } * advance)
		val panelHeight = minOf(image.height, MARGIN * 2 + lines.size * lineHeight)
		image.fillRect(0, image.height - panelHeight, panelWidth, panelHeight, BLACK)

		lines.forEachIndexed { row, line ->
			line.forEachIndexed { column, char ->
				if (char.code < 256) {
					val glyphX = char.code % 16 * cell
					val glyphY = char.code / 16 * cell
					for (y in 0 until cell) for (x in 0 until cell) {
						if (font.getPixel(glyphX + x, glyphY + y) ushr 24 == 0) continue
						for (sy in 0 until scale) for (sx in 0 until scale) {
							val px = MARGIN + column * advance + x * scale + sx
							val py = image.height - 1 - (MARGIN + row * lineHeight + y * scale + sy)
							if (px in 0 until image.width && py in 0 until image.height) image.setPixelABGR(px, py, WHITE)
						}
					}
				}
			}
		}
	}
}

private fun rgbPixel(pixels: ByteArray, width: Int, x: Int, y: Int): Int {
	val i = (y * width + x) * 4
	return abgr(pixels[i].toInt() and 0xFF, pixels[i + 1].toInt() and 0xFF, pixels[i + 2].toInt() and 0xFF)
}

internal fun nv12Pixel(pixels: ByteArray, width: Int, height: Int, x: Int, glY: Int): Int {
	val y = height - 1 - glY
	val luma = ((pixels[y * width + x].toInt() and 0xFF) - 16) * 1.164383f
	val chroma = width * height + y / 2 * width + x / 2 * 2
	val u = (pixels[chroma].toInt() and 0xFF) - 128
	val v = (pixels[chroma + 1].toInt() and 0xFF) - 128
	return abgr(
		Math.clamp((luma + 1.792741f * v).toLong(), 0, 255),
		Math.clamp((luma - 0.213249f * u - 0.532909f * v).toLong(), 0, 255),
		Math.clamp((luma + 2.112402f * u).toLong(), 0, 255),
	)
}

private fun abgr(r: Int, g: Int, b: Int): Int = BLACK or (b shl 16) or (g shl 8) or r
