package org.polyfrost.keyframe.standalone.playback

import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.Identifier
import net.minecraft.util.Util
import org.polyfrost.keyframe.standalone.Standalone
import org.polyfrost.keyframe.standalone.compat.*
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.readBytes
import kotlin.io.path.readLines
import kotlin.io.path.readText
import kotlin.math.abs

private const val XCURSOR_MAGIC = 0x72756358
private const val XCURSOR_IMAGE = 0xFFFD0002.toInt()
private const val XCURSOR_IMAGE_HEADER_LEN = 36
private const val PNG_MAGIC = 0x474E5089
private const val DEFAULT_XCURSOR_SIZE = 24
private const val WINDOWS_CURSOR_SIZE = 32

internal class CursorPixels(val width: Int, val height: Int, val hotX: Int, val hotY: Int, val argb: IntArray, val scale: Float = 1f) {
	fun scaled(scale: Float) = CursorPixels(width, height, hotX, hotY, argb, scale)
}

internal fun parseXcursor(data: ByteArray, wanted: Int): CursorPixels? {
	val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
	if (data.size < 16 || buffer.getInt(0) != XCURSOR_MAGIC) return null
	val entries = (0 until buffer.getInt(12)).map { 16 + it * 12 }.filter { buffer.getInt(it) == XCURSOR_IMAGE }
	val best = entries.minByOrNull { abs(buffer.getInt(it + 4) - wanted) } ?: return null
	val image = buffer.getInt(best + 8)
	val width = buffer.getInt(image + 16)
	val height = buffer.getInt(image + 20)
	val argb = IntArray(width * height) { unpremultiply(buffer.getInt(image + XCURSOR_IMAGE_HEADER_LEN + it * 4)) }
	return CursorPixels(width, height, buffer.getInt(image + 24), buffer.getInt(image + 28), argb)
}

private fun unpremultiply(pixel: Int): Int {
	val alpha = pixel ushr 24
	if (alpha == 0 || alpha == 255) return pixel
	fun channel(shift: Int) = minOf(255, (pixel ushr shift and 0xFF) * 255 / alpha) shl shift
	return (alpha shl 24) or channel(16) or channel(8) or channel(0)
}

internal fun parseCur(data: ByteArray, wanted: Int): CursorPixels? {
	val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
	if (data.size < 22 || buffer.getShort(2).toInt() != 2) return null
	val entries = (0 until buffer.getShort(4).toInt()).map { 6 + it * 16 }
	val best = entries.minByOrNull { abs(curSize(buffer, it) - wanted) } ?: return null
	val hotX = buffer.getShort(best + 4).toInt()
	val hotY = buffer.getShort(best + 6).toInt()
	val length = buffer.getInt(best + 8)
	val offset = buffer.getInt(best + 12)
	if (buffer.getInt(offset) == PNG_MAGIC) return readPng(data.copyOfRange(offset, offset + length), hotX, hotY).let { it.scaled(wanted.toFloat() / it.width) }

	val width = buffer.getInt(offset + 4)
	val height = buffer.getInt(offset + 8) / 2
	if (buffer.getShort(offset + 14).toInt() != 32) return null
	val pixels = offset + buffer.getInt(offset)
	val argb = IntArray(width * height) {
		val rowFromBottom = height - 1 - it / width
		buffer.getInt(pixels + (rowFromBottom * width + it % width) * 4)
	}
	return CursorPixels(width, height, hotX, hotY, argb, wanted.toFloat() / width)
}

private fun curSize(buffer: ByteBuffer, entry: Int): Int {
	val size = buffer.get(entry).toInt() and 0xFF
	return if (size == 0) 256 else size
}

private fun readPng(data: ByteArray, hotX: Int, hotY: Int): CursorPixels =
	//? if >=1.21.4 {
	NativeImage.read(data).use { CursorPixels(it.width, it.height, hotX, hotY, it.pixels) }
	//?} else
	/*NativeImage.read(data).use { CursorPixels(it.width, it.height, hotX, hotY, it.pixelsRGBA.map(::swapRedBlue).toIntArray()) }*/

object SystemCursor {
	class Loaded(val texture: Identifier, val width: Int, val height: Int, val hotX: Int, val hotY: Int, val scale: Float)

	private val LOGGER = LoggerFactory.getLogger(Standalone.MOD_ID)

	private var tried = false
	private var loaded: Loaded? = null

	fun get(): Loaded? {
		if (!tried) {
			tried = true
			try {
				loaded = read()?.let(::register)
			} catch (e: Exception) {
				LOGGER.warn("Could not load the system cursor", e)
			}
		}
		return loaded
	}

	private fun read(): CursorPixels? = when (Util.getPlatform()) {
		Util.OS.WINDOWS -> readWindows()
		Util.OS.OSX -> readMac()
		else -> readXcursor()
	}

	private fun register(pixels: CursorPixels): Loaded {
		val image = NativeImage(pixels.width, pixels.height, false)
		for (i in pixels.argb.indices) image.setPixel(i % pixels.width, i / pixels.width, pixels.argb[i])
		val id = identifier(Standalone.MOD_ID, "system_cursor")
		//? if >=1.21.8 {
		Minecraft.getInstance().textureManager.register(id, DynamicTexture({ "Keyframe cursor" }, image))
		//?} else
		/*Minecraft.getInstance().textureManager.register(id, DynamicTexture(image))*/
		return Loaded(id, pixels.width, pixels.height, pixels.hotX, pixels.hotY, pixels.scale)
	}

	internal fun readXcursor(): CursorPixels? {
		val home = Path.of(System.getProperty("user.home", "."))
		val kde = home.resolve(".config/kcminputrc")
		val gtk = home.resolve(".config/gtk-3.0/settings.ini")
		val theme = firstNonBlank(System.getenv("XCURSOR_THEME"), setting(kde, "cursorTheme"), setting(gtk, "gtk-cursor-theme-name"))
		val size = firstNonBlank(System.getenv("XCURSOR_SIZE"), setting(kde, "cursorSize"), setting(gtk, "gtk-cursor-theme-size"))
			?.toIntOrNull() ?: DEFAULT_XCURSOR_SIZE
		val roots = System.getenv("XCURSOR_PATH")?.takeIf(String::isNotBlank)
			?.split(":")
			?.map { Path.of(if (it.startsWith("~")) home.toString() + it.drop(1) else it) }
			?: listOf(
				home.resolve(".local/share/icons"),
				home.resolve(".icons"),
				Path.of("/usr/local/share/icons"),
				Path.of("/usr/share/icons"),
				Path.of("/usr/share/pixmaps"),
			)
		val seen = HashSet<String>()
		return listOfNotNull(theme, "Adwaita", "default").firstNotNullOfOrNull { findXcursor(it, roots, size, seen) }
	}

	private fun findXcursor(theme: String, roots: List<Path>, size: Int, seen: MutableSet<String>): CursorPixels? {
		if (!seen.add(theme)) return null
		for (root in roots) {
			for (name in listOf("default", "left_ptr", "arrow")) {
				val file = root.resolve(theme).resolve("cursors").resolve(name)
				if (file.isRegularFile()) parseXcursor(file.readBytes(), size)?.let { return it }
			}
		}
		return roots.mapNotNull { setting(it.resolve(theme).resolve("index.theme"), "Inherits") }
			.flatMap { it.split(',', ';') }
			.firstNotNullOfOrNull { findXcursor(it.trim(), roots, size, seen) }
	}

	private fun readWindows(): CursorPixels? {
		val windows = System.getenv("SystemRoot") ?: "C:\\Windows"
		return listOfNotNull(registryCursor(), Path.of(windows, "Cursors", "aero_arrow.cur"))
			.filter { it.toString().endsWith(".cur", ignoreCase = true) && it.isRegularFile() }
			.firstNotNullOfOrNull { parseCur(it.readBytes(), WINDOWS_CURSOR_SIZE) }
	}

	private fun registryCursor(): Path? {
		val output = try {
			ProcessBuilder("reg", "query", "HKCU\\Control Panel\\Cursors", "/v", "Arrow").redirectErrorStream(true).start()
				.inputStream.use { String(it.readAllBytes()) }
		} catch (_: IOException) {
			return null
		}
		val value = Regex("""Arrow\s+REG_\w+\s+(.+)""").find(output)?.groupValues?.get(1)?.trim() ?: return null
		return Path.of(Regex("%(\\w+)%").replace(value) { System.getenv(it.groupValues[1]) ?: it.value })
	}

	private fun readMac(): CursorPixels? {
		val dir = Path.of("/System/Library/Frameworks/ApplicationServices.framework/Versions/A/Frameworks/HIServices.framework/Versions/A/Resources/cursors/arrow")
		val plist = dir.resolve("info.plist").takeIf(Path::isRegularFile)?.readText() ?: ""
		for ((name, scale) in listOf("cursor@2x.png" to 2, "cursor.png" to 1)) {
			val file = dir.resolve(name)
			if (Files.isRegularFile(file)) {
				return readPng(file.readBytes(), (plistNumber(plist, "hotx") * scale).toInt(), (plistNumber(plist, "hoty") * scale).toInt()).scaled(1f / scale)
			}
		}
		return null
	}

	private fun plistNumber(plist: String, key: String): Double =
		Regex("<key>$key</key>\\s*<(?:real|integer)>([-0-9.]+)<").find(plist)?.groupValues?.get(1)?.toDouble() ?: 0.0

	private fun setting(file: Path, key: String): String? {
		if (!file.isRegularFile()) return null
		return runCatching {
			file.readLines().map(String::trim)
				.firstOrNull { it.startsWith(key) && it.substring(key.length).trimStart().startsWith("=") }
				?.substringAfter('=')?.trim()
		}.getOrNull()
	}

	private fun firstNonBlank(vararg values: String?): String? = values.firstOrNull { !it.isNullOrBlank() }
}
