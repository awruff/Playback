package org.polyfrost.keyframe.standalone.compat

import net.minecraft.client.Minecraft
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity
import net.minecraft.client.gui.GameGui
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screen.menu.multiplayer.DownloadingTerrainScreen
import net.minecraft.client.render.GameRenderer
import net.minecraft.client.render.Window
import net.minecraft.client.render.pipeline.RenderTarget
import net.minecraft.client.render.platform.GLX
import net.minecraft.client.render.platform.GlStateManager
import net.minecraft.client.render.texture.TextureUtil
import net.minecraft.client.render.vertex.DefaultVertexFormat
import net.minecraft.client.render.vertex.Tesselator
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.client.render.world.WorldRenderer
import net.minecraft.client.resource.manager.ResourceManager
import net.minecraft.client.world.ClientWorld
import net.minecraft.network.Connection
import net.minecraft.resources.Identifier
import net.minecraft.text.LiteralText
import net.minecraft.text.Text
import org.polyfrost.keyframe.standalone.mixin.ornithe.Mixin_ChunkRenderDispatcher_Export
import org.polyfrost.keyframe.standalone.mixin.ornithe.Mixin_WorldRenderer_Export
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.Display
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL21
import org.lwjgl.opengl.GL30
import org.lwjgl.opengl.GL32
import org.lwjgl.opengl.GLContext
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.imageio.ImageIO

internal const val FENCE_NO_TIMEOUT = Long.MAX_VALUE

internal fun realNanos(): Long = System.nanoTime()

class DeltaTracker(private val partial: Float) {
	fun getGameTimeDeltaPartialTick(ignoreFrozen: Boolean): Float = partial

	fun getGameTimeDeltaTicks(): Float = partial
}

object SharedConstants {
	class Version(val protocolVersion: Int, val name: String)

	private val version = Version(47, "1.8.9")

	fun getCurrentVersion() = version
}

object TimeSource {
	fun interface NanoTimeSource {
		fun getAsLong(): Long
	}
}

object Util {
	enum class OS { WINDOWS, OSX, OTHER }

	private val FILE_NAME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss")

	internal var source = TimeSource.NanoTimeSource { realNanos() }

	fun getNanos(): Long = source.getAsLong()

	fun getFilenameFormattedDateTime(): String = FILE_NAME.format(LocalDateTime.now())

	fun getPlatform(): OS {
		val name = System.getProperty("os.name").lowercase()
		return when {
			"win" in name -> OS.WINDOWS
			"mac" in name -> OS.OSX
			else -> OS.OTHER
		}
	}
}

object GameClock {
	private var source: TimeSource.NanoTimeSource? = null
	private var offset = 0L
	private var live = 0L

	internal fun set(next: TimeSource.NanoTimeSource) {
		source = next
		offset = live - next.getAsLong() / 1_000_000
	}

	@JvmStatic
	fun millis(real: Long): Long {
		live = source?.let { it.getAsLong() / 1_000_000 + offset } ?: real
		return live
	}
}

internal fun setTimeSource(source: TimeSource.NanoTimeSource) {
	Util.source = source
	GameClock.set(source)
}

internal fun maxTextureSize(): Int = Minecraft.getMaxTextureSize()

internal val Minecraft.level: ClientWorld? get() = world

internal val Minecraft.gameDirectory: File get() = gameDir

internal val Minecraft.levelRenderer: WorldRenderer get() = worldRenderer

internal fun Minecraft.execute(task: Runnable) {
	executeTask(task)
}

internal fun Minecraft.updateTitle() = Display.setTitle("Minecraft 1.8.9")

internal fun Minecraft.resizeGui() {
	val window = Window(this)
	screen?.resize(this, window.width, window.height)
}

internal fun Minecraft.hasControlDown() = Screen.isControlDown()

internal fun Minecraft.hasAltDown() = Screen.isAltDown()

internal fun GameGui.screen(): Screen? = Minecraft.getInstance().screen

internal fun GameGui.setScreen(screen: Screen?) = Minecraft.getInstance().openScreen(screen)

internal val GameGui.hud: GameGui get() = this

internal fun GameGui.tick(paused: Boolean) = tick()

internal val GameGui.isHidden: Boolean get() = Minecraft.getInstance().options.hideGui

internal fun GameGui.toggle() {
	val options = Minecraft.getInstance().options
	options.hideGui = !options.hideGui
}

internal val Minecraft.debugEntries get() = DebugEntries

internal object DebugEntries {
	var isOverlayVisible: Boolean
		get() = Minecraft.getInstance().options.debugEnabled
		set(value) {
			Minecraft.getInstance().options.debugEnabled = value
		}
}

internal typealias LevelLoadingScreen = DownloadingTerrainScreen

internal typealias ClientLevel = ClientWorld

internal typealias LocalPlayer = LocalClientPlayerEntity

internal object Component {
	fun literal(text: String): Text = LiteralText(text)
}

internal fun Minecraft.disconnect(next: Screen, keepResourcePacks: Boolean) {
	setWorld(null)
	openScreen(next)
}

internal fun LocalPlayer.clientSideCloseContainer() = closeMenu()

internal fun LocalPlayer.sendOverlayMessage(message: String) = Minecraft.getInstance().gui.setOverlayMessage(message, false)

internal fun GameRenderer.mainRenderTarget(): RenderTarget = Minecraft.getInstance().renderTarget

internal fun resizeRenderTargets(width: Int, height: Int) {
	val mc = Minecraft.getInstance()
	mc.renderTarget.resize(width, height)
	mc.gameRenderer.onResolutionChanged(width, height)
}

internal fun ResourceManager.open(id: Identifier): InputStream = getResource(id).asStream()

internal val Connection.isMemoryConnection: Boolean get() = isLocal

internal object FixedTickRate {
	fun runsNormally() = false

	fun nanosecondsPerTick() = 0L
}

internal fun ClientWorld.tickRateManager() = FixedTickRate

internal fun WorldRenderer.hasRenderedAllSections(): Boolean {
	val renderer = this as Mixin_WorldRenderer_Export
	val dispatcher = renderer.`keyframe$getChunkRenderDispatcher`() as Mixin_ChunkRenderDispatcher_Export
	return renderer.`keyframe$getDirtyChunks`().isEmpty() &&
		dispatcher.`keyframe$getPendingTasks`().isEmpty() &&
		dispatcher.`keyframe$getPendingUploads`().isEmpty()
}

internal class FramebufferSize(private val width: Int, private val height: Int) {
	fun width() = width
	fun height() = height
}

internal object GameWindow {
	private val mc get() = Minecraft.getInstance()

	var width: Int
		get() = mc.width
		set(value) {
			mc.width = value
		}

	var height: Int
		get() = mc.height
		set(value) {
			mc.height = value
		}

	fun setTitle(title: String) = Display.setTitle(title)

	fun queryFramebufferSize() = FramebufferSize(Display.getWidth(), Display.getHeight())
}

internal val Minecraft.gameWindow get() = GameWindow

internal fun swapRedBlue(color: Int): Int =
	(color and 0xFF00FF00.toInt()) or ((color and 0xFF) shl 16) or ((color ushr 16) and 0xFF)

internal class NativeImage(val width: Int, val height: Int, clear: Boolean) : AutoCloseable {
	val pixelsRGBA = IntArray(width * height)

	fun getPixel(x: Int, y: Int): Int = swapRedBlue(pixelsRGBA[y * width + x])

	fun setPixel(x: Int, y: Int, argb: Int) {
		pixelsRGBA[y * width + x] = swapRedBlue(argb)
	}

	fun setPixelABGR(x: Int, y: Int, abgr: Int) {
		pixelsRGBA[y * width + x] = abgr
	}

	fun fillRect(x: Int, y: Int, fillWidth: Int, fillHeight: Int, abgr: Int) {
		for (row in y until y + fillHeight) pixelsRGBA.fill(abgr, row * width + x, row * width + x + fillWidth)
	}

	override fun close() {}

	companion object {
		fun read(stream: InputStream): NativeImage {
			val source = ImageIO.read(stream)
			val image = NativeImage(source.width, source.height, false)
			source.getRGB(0, 0, source.width, source.height, image.pixelsRGBA, 0, source.width)
			for (i in image.pixelsRGBA.indices) image.pixelsRGBA[i] = swapRedBlue(image.pixelsRGBA[i])
			return image
		}

		fun read(data: ByteArray): NativeImage = read(ByteArrayInputStream(data))
	}
}

internal fun DynamicTexture(image: NativeImage): DynamicTexture = DynamicTexture(image.width, image.height).also {
	for (i in image.pixelsRGBA.indices) it.pixels[i] = swapRedBlue(image.pixelsRGBA[i])
	it.upload()
}

internal enum class TextureFormat { RGBA8 }

internal class GpuTexture(
	val id: Int,
	private val width: Int,
	private val height: Int,
	val framebuffer: Int = -1,
	val u: Float = 1f,
	val v: Float = 1f,
) {
	val format get() = TextureFormat.RGBA8

	fun getWidth(mip: Int) = width

	fun getHeight(mip: Int) = height

	fun close() {
		if (framebuffer < 0) TextureUtil.deleteTextures(id)
	}

	companion object {
		const val USAGE_COPY_DST = 0
		const val USAGE_COPY_SRC = 0
		const val USAGE_TEXTURE_BINDING = 0
		const val USAGE_RENDER_ATTACHMENT = 0
	}
}

internal class GpuTextureView(val texture: GpuTexture) {
	fun close() {}
}

internal val RenderTarget.colorTexture
	get() = if (GLX.useFbo()) GpuTexture(colorTextureId, viewWidth, viewHeight, frameBufferId) else GpuTexture(-1, viewWidth, viewHeight)

private val gl32 by lazy { GLContext.getCapabilities().OpenGL32 }

private val npotTextures by lazy { GLContext.getCapabilities().let { it.OpenGL20 || it.GL_ARB_texture_non_power_of_two } }

internal fun supportsGl32() = gl32

internal fun backBufferProblem(width: Int, height: Int): String? {
	if (GLX.useFbo() || (width <= Display.getWidth() && height <= Display.getHeight())) return null
	return "with Use FBOs off, the export can't be larger than the window (${Display.getWidth()}x${Display.getHeight()}), got ${width}x$height"
}

internal class GpuFence(private val sync: Long) {
	fun awaitCompletion(timeout: Long): Boolean =
		sync == 0L || GL32.glClientWaitSync(sync, GL32.GL_SYNC_FLUSH_COMMANDS_BIT, timeout).let { it == GL32.GL_ALREADY_SIGNALED || it == GL32.GL_CONDITION_SATISFIED }

	fun close() {
		if (sync != 0L) GL32.glDeleteSync(sync)
	}
}

internal class GpuBuffer(private val size: Int) {
	val id = if (gl32) GL15.glGenBuffers() else 0
	val memory: ByteBuffer? = if (id == 0) BufferUtils.createByteBuffer(size) else null

	init {
		if (id != 0) {
			GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, id)
			GL15.glBufferData(GL21.GL_PIXEL_PACK_BUFFER, size.toLong(), GL15.GL_STREAM_READ)
			GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0)
		}
	}

	fun map(read: Boolean, write: Boolean): MappedView {
		memory?.let { return MappedView(0, it.clear()) }
		GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, id)
		val data = GL15.glMapBuffer(GL21.GL_PIXEL_PACK_BUFFER, GL15.GL_READ_ONLY, size.toLong(), null)!!
		GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0)
		return MappedView(id, data)
	}

	fun close() {
		if (id != 0) GL15.glDeleteBuffers(id)
	}

	class MappedView(private val id: Int, private val data: ByteBuffer) : AutoCloseable {
		fun data() = data

		override fun close() {
			if (id == 0) return
			GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, id)
			GL15.glUnmapBuffer(GL21.GL_PIXEL_PACK_BUFFER)
			GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0)
		}
	}

	companion object {
		const val USAGE_MAP_READ = 0
		const val USAGE_COPY_DST = 0
	}
}

internal object CommandEncoder {
	private fun readFrom(source: GpuTexture, read: () -> Unit) {
		if (source.framebuffer < 0) return read()
		val previous = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING)
		GLX.bindFramebuffer(GLX.GL_FRAMEBUFFER, source.framebuffer)
		read()
		GLX.bindFramebuffer(GLX.GL_FRAMEBUFFER, previous)
	}

	fun copyTextureToBuffer(texture: GpuTexture, buffer: GpuBuffer, offset: Int, callback: Runnable, mip: Int) {
		val width = texture.getWidth(0)
		val height = texture.getHeight(0)
		readFrom(texture) {
			val memory = buffer.memory
			if (memory != null) {
				GL11.glReadPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, memory.clear())
			} else {
				GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, buffer.id)
				GL11.glReadPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, offset.toLong())
				GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0)
			}
		}
		callback.run()
	}

	fun copyTextureToTexture(source: GpuTexture, destination: GpuTexture, mip: Int, x: Int, y: Int, sourceX: Int, sourceY: Int, width: Int, height: Int) {
		readFrom(source) {
			GlStateManager.bindTexture(destination.id)
			GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, mip, x, y, sourceX, sourceY, width, height)
		}
	}

	fun writeToTexture(texture: GpuTexture, image: NativeImage) {
		val pixels = ByteBuffer.allocateDirect(image.pixelsRGBA.size * Int.SIZE_BYTES).order(ByteOrder.nativeOrder())
		pixels.asIntBuffer().put(image.pixelsRGBA)
		GlStateManager.bindTexture(texture.id)
		GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, image.width, image.height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels)
	}

	fun presentTexture(view: GpuTextureView) {
		val texture = view.texture
		val width = Display.getWidth().toDouble()
		val height = Display.getHeight().toDouble()
		GlStateManager.colorMask(true, true, true, false)
		GlStateManager.disableDepthTest()
		GlStateManager.depthMask(false)
		GlStateManager.matrixMode(GL11.GL_PROJECTION)
		GlStateManager.loadIdentity()
		GlStateManager.ortho(0.0, width, height, 0.0, 1000.0, 3000.0)
		GlStateManager.matrixMode(GL11.GL_MODELVIEW)
		GlStateManager.loadIdentity()
		GlStateManager.translatef(0f, 0f, -2000f)
		GlStateManager.viewport(0, 0, Display.getWidth(), Display.getHeight())
		GlStateManager.enableTexture()
		GlStateManager.disableLighting()
		GlStateManager.disableAlphaTest()
		GlStateManager.disableBlend()
		GlStateManager.enableColorMaterial()
		GlStateManager.color4f(1f, 1f, 1f, 1f)
		GlStateManager.bindTexture(texture.id)
		val u = texture.u.toDouble()
		val v = texture.v.toDouble()
		val tesselator = Tesselator.getInstance()
		val buffer = tesselator.buffer
		buffer.begin(GL11.GL_QUADS, DefaultVertexFormat.POSITION_TEX_COLOR)
		buffer.vertex(0.0, height, 0.0).texture(0.0, 0.0).color(255, 255, 255, 255).nextVertex()
		buffer.vertex(width, height, 0.0).texture(u, 0.0).color(255, 255, 255, 255).nextVertex()
		buffer.vertex(width, 0.0, 0.0).texture(u, v).color(255, 255, 255, 255).nextVertex()
		buffer.vertex(0.0, 0.0, 0.0).texture(0.0, v).color(255, 255, 255, 255).nextVertex()
		tesselator.end()
		GlStateManager.bindTexture(0)
		GlStateManager.depthMask(true)
		GlStateManager.colorMask(true, true, true, true)
	}

	fun createFence() = GpuFence(if (gl32) GL32.glFenceSync(GL32.GL_SYNC_GPU_COMMANDS_COMPLETE, 0) else 0L)

	fun submit() {}
}

internal object RenderSystem.getDevice() {
	fun createCommandEncoder() = CommandEncoder

	fun createBuffer(label: () -> String, usage: Int, size: Int) = GpuBuffer(size)

	fun createTexture(label: () -> String, usage: Int, format: TextureFormat, width: Int, height: Int, depth: Int, mips: Int): GpuTexture {
		val storedWidth = if (npotTextures) width else powerOfTwo(width)
		val storedHeight = if (npotTextures) height else powerOfTwo(height)
		val id = TextureUtil.genTextures()
		GlStateManager.bindTexture(id)
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR)
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR)
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP)
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP)
		GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, storedWidth, storedHeight, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, null as ByteBuffer?)
		return GpuTexture(id, width, height, u = width.toFloat() / storedWidth, v = height.toFloat() / storedHeight)
	}

	private fun powerOfTwo(size: Int) = if (size <= 1) 1 else Integer.highestOneBit(size - 1) shl 1

	fun createTextureView(texture: GpuTexture) = GpuTextureView(texture)
}

internal fun glTextureId(texture: GpuTexture): Int? = texture.id.takeIf { it >= 0 }

internal fun glBufferId(buffer: GpuBuffer): Int? = buffer.id.takeIf { it != 0 }

internal fun identifier(namespace: String, path: String): Identifier = Identifier(namespace, path)

object ArgentumCompat {
	private val requestFramebuffer = runCatching { Minecraft::class.java.getMethod("argentum\$requestFramebuffer") }.getOrNull()

	@JvmStatic
	fun keepMainFramebuffer() {
		requestFramebuffer?.invoke(Minecraft.getInstance())
	}

	private val config by lazy { runCatching { Class.forName("dev.rdh.argentum.impl.Argentum").getField("CONFIG") }.getOrNull() }

	private var ownFontBatching: Boolean? = null

	fun forceUnbatchedFont(force: Boolean) {
		runCatching {
			val cfg = config?.get(null) ?: return
			val field = cfg.javaClass.getField("fontBatching")
			if (force) {
				if (ownFontBatching == null) ownFontBatching = field.getBoolean(cfg)
				field.setBoolean(cfg, false)
			} else {
				ownFontBatching?.let { field.setBoolean(cfg, it) }
				ownFontBatching = null
			}
		}
	}
}
