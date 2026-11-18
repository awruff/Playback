package org.polyfrost.keyframe.standalone.compat

import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.resources.Identifier
//? if >=1.21.8 {
import com.mojang.renderpearl.api.textures.FilterMode
import com.mojang.renderpearl.api.textures.GpuTexture
import com.mojang.renderpearl.api.textures.GpuTextureView
//?}
//? if >=26.3 {
import com.mojang.renderpearl.api.buffers.GpuBuffer
import com.mojang.renderpearl.backend.opengl.GlBuffer
import com.mojang.renderpearl.backend.opengl.GlTexture
//?} elif >=26.2 {
/*import com.mojang.renderpearl.api.buffers.GpuBuffer
import com.mojang.blaze3d.opengl.GlBuffer
import com.mojang.blaze3d.opengl.GlTexture
*///?} elif >=1.21.8 {
/*import com.mojang.blaze3d.opengl.GlTexture
import org.polyfrost.keyframe.standalone.mixin.Mixin_GlBuffer_Export
*///?}
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.chat.Component
import net.minecraft.util.TimeSource
import net.minecraft.util.Util
//? if >=26.3 {
import com.mojang.renderpearl.api.commands.GpuFence
import org.lwjgl.sdl.SDLTimer
//?} else {
/*import com.mojang.blaze3d.platform.Window
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import org.lwjgl.glfw.GLFW
import org.lwjgl.system.MemoryStack
*///?}
//? if <26.2 {
/*import com.mojang.renderpearl.api.pipeline.RenderTarget
import net.minecraft.client.gui.Gui
import net.minecraft.client.renderer.GameRenderer
*///?}
//? if >=1.21.8 <26.2 {
/*import com.mojang.renderpearl.api.buffers.GpuBuffer
import com.mojang.renderpearl.api.commands.CommandEncoder
*///?}
//? if >=1.21.8 <26.1 {
/*import net.minecraft.client.renderer.RenderPipelines
import org.polyfrost.keyframe.standalone.mixin.Mixin_Graphics_Blit
*///?}
//? if <1.21.11 {
/*import net.minecraft.client.multiplayer.ClientPacketListener
import net.minecraft.client.resources.sounds.SoundInstance
*///?}
//? if >=1.21.10 <1.21.11 {
/*import net.minecraft.client.gui.components.debug.DebugScreenEntryList
*///?}
//? if <1.21.8 {
/*import com.mojang.renderpearl.api.pipeline.TextureTarget
import com.mojang.blaze3d.platform.GlConst
import com.mojang.blaze3d.platform.GlStateManager
import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.vertex.BufferUploader
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.MouseHandler
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.entity.player.Inventory
import org.polyfrost.keyframe.standalone.mixin.Mixin_MobEffectInstance_Puppet
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL21
import org.lwjgl.opengl.GL30
import org.lwjgl.opengl.GL32
import java.nio.ByteBuffer
*///?}

//? if >=26.3 {
internal const val FENCE_NO_TIMEOUT = GpuFence.NO_TIMEOUT

internal fun realNanos(): Long = SDLTimer.SDL_GetTicksNS()
//?} else {
/*internal const val FENCE_NO_TIMEOUT = Long.MAX_VALUE

internal fun realNanos(): Long = (GLFW.glfwGetTime() * 1.0E9).toLong()

internal class FramebufferSize(private val width: Int, private val height: Int) {
	fun width() = width
	fun height() = height
}

internal fun Window.queryFramebufferSize(): FramebufferSize = MemoryStack.stackPush().use { stack ->
	val width = stack.mallocInt(1)
	val height = stack.mallocInt(1)
	GLFW.glfwGetFramebufferSize(handle(), width, height)
	FramebufferSize(width[0], height[0])
}

internal fun ClientLevel.addBreakingBlockEffects(pos: BlockPos, direction: Direction, playSound: Boolean) =
	//? if >=1.21.10 {
	addBreakingBlockEffect(pos, direction)
	//?} else
	/^Minecraft.getInstance().particleEngine.crack(pos, direction)^/
*///?}

//? if >=26.2 {
internal fun maxTextureSize(): Int = RenderSystem.getDevice().deviceInfo.limits().maxTextureSize()

internal fun setTimeSource(source: TimeSource.NanoTimeSource) = Util.setTimeSource(source)
//?} else {
/*internal fun maxTextureSize(): Int = RenderSystem.getDevice().maxTextureSize

internal fun setTimeSource(source: TimeSource.NanoTimeSource) {
	Util.timeSource = source
}

internal val Gui.hud: Gui get() = this

internal fun Gui.screen(): Screen? = Minecraft.getInstance().screen

internal fun Gui.setScreen(screen: Screen?) = Minecraft.getInstance().setScreen(screen)

internal val Gui.isHidden: Boolean get() = Minecraft.getInstance().options.hideGui

internal fun Gui.toggle() {
	val options = Minecraft.getInstance().options
	options.hideGui = !options.hideGui
}

internal fun GameRenderer.mainRenderTarget(): RenderTarget = Minecraft.getInstance().mainRenderTarget

*///?}

//? if >=1.21.8 <26.2 {
/*internal fun GpuBuffer.map(read: Boolean, write: Boolean): GpuBuffer.MappedView =
	RenderSystem.getDevice().createCommandEncoder().mapBuffer(this, read, write)

internal fun CommandEncoder.submit() {}
*///?}

//? if >=26.1 {
internal fun resizeRenderTargets(width: Int, height: Int) = Minecraft.getInstance().gameRenderer.resize(width, height)

internal fun GuiGraphicsExtractor.blitPreview(view: GpuTextureView, x0: Int, y0: Int, x1: Int, y1: Int, u0: Float, u1: Float, v0: Float, v1: Float) =
	blit(view, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR), x0, y0, x1, y1, u0, u1, v0, v1)
//?} else {
/*internal fun resizeRenderTargets(width: Int, height: Int) {
	val mc = Minecraft.getInstance()
	mc.mainRenderTarget.resize(width, height)
	mc.gameRenderer.resize(width, height)
}

internal fun Minecraft.resizeGui() {
	//? if >=1.21.8 {
	window.guiScale = window.calculateScale(options.guiScale().get(), isEnforceUnicode)
	//?} else
	/^window.guiScale = window.calculateScale(options.guiScale().get(), isEnforceUnicode).toDouble()^/
	screen?.resize(window.guiScaledWidth, window.guiScaledHeight)
	mouseHandler.setIgnoreFirstMove()
}

internal fun LocalPlayer.sendOverlayMessage(message: Component) = displayClientMessage(message, true)
*///?}

internal fun LocalPlayer.sendOverlayMessage(message: String) = sendOverlayMessage(Component.literal(message))

//? if >=1.21.11 <26.1 {
/*internal fun GuiGraphicsExtractor.blitPreview(view: GpuTextureView, x0: Int, y0: Int, x1: Int, y1: Int, u0: Float, u1: Float, v0: Float, v1: Float) =
	(this as Mixin_Graphics_Blit).`keyframe$submitBlit`(RenderPipelines.GUI_TEXTURED, view, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR), x0, y0, x1, y1, u0, u1, v0, v1, -1)
*///?}

//? if >=1.21.8 <1.21.11 {
/*internal fun GuiGraphicsExtractor.blitPreview(view: GpuTextureView, x0: Int, y0: Int, x1: Int, y1: Int, u0: Float, u1: Float, v0: Float, v1: Float) {
	view.texture().setTextureFilter(FilterMode.LINEAR, false)
	(this as Mixin_Graphics_Blit).`keyframe$submitBlit`(RenderPipelines.GUI_TEXTURED, view, x0, y0, x1, y1, u0, u1, v0, v1, -1)
}
*///?}

//? if >=1.21.10 <1.21.11 {
/*internal fun ClientPacketListener.hasClientLoaded(): Boolean = Minecraft.getInstance().player?.hasClientLoaded() == true

internal var DebugScreenEntryList.isOverlayVisible: Boolean
	get() = isF3Visible
	set(value) = setF3Visible(value)
*///?}

//? if <1.21.11 {
/*internal fun Screen.init(width: Int, height: Int) = init(Minecraft.getInstance(), width, height)

internal fun Screen.resize(width: Int, height: Int) = resize(Minecraft.getInstance(), width, height)

internal val SoundInstance.identifier get() = location
*///?}

//? if <1.21.10 {
/*internal fun ClientPacketListener.hasClientLoaded(): Boolean = true

internal fun Window.handle(): Long = window

internal fun Minecraft.hasControlDown() = Screen.hasControlDown()

internal fun Minecraft.hasAltDown() = Screen.hasAltDown()

internal val Minecraft.debugEntries get() = DebugEntries

internal object DebugEntries {
	var isOverlayVisible: Boolean
		get() = Minecraft.getInstance().gui.debugOverlay.showDebugScreen()
		set(value) {
			if (value != isOverlayVisible) Minecraft.getInstance().gui.debugOverlay.toggleOverlay()
		}
}
*///?}

//? if <1.21.8 {
/*//? if <1.21.4
internal fun RenderTarget.resize(width: Int, height: Int) = resize(width, height, Minecraft.ON_OSX)

internal fun MouseHandler.getScaledXPos(window: Window): Double = xpos() * window.guiScaledWidth / window.screenWidth

internal fun MouseHandler.getScaledYPos(window: Window): Double = ypos() * window.guiScaledHeight / window.screenHeight

internal var LocalPlayer.portalEffectIntensity: Float
	get() = spinningEffectIntensity
	set(value) {
		spinningEffectIntensity = value
	}

internal var LocalPlayer.oPortalEffectIntensity: Float
	get() = oSpinningEffectIntensity
	set(value) {
		oSpinningEffectIntensity = value
	}

internal var Inventory.selectedSlot: Int
	get() = selected
	set(value) {
		selected = value
	}

internal fun MobEffectInstance.tickClient() {
	(this as Mixin_MobEffectInstance_Puppet).`keyframe$tickDownDuration`()
}

internal fun PoseStack.pushMatrix(): PoseStack = apply { pushPose() }

internal fun PoseStack.popMatrix() = popPose()

internal fun PoseStack.translate(x: Float, y: Float): PoseStack = apply { translate(x, y, 0f) }

internal fun PoseStack.scale(factor: Number): PoseStack = apply { scale(factor.toFloat(), factor.toFloat(), 1f) }

internal fun swapRedBlue(color: Int): Int =
	(color and 0xFF00FF00.toInt()) or ((color and 0xFF) shl 16) or ((color ushr 16) and 0xFF)

//? if >=1.21.4 {
internal fun NativeImage.setPixelABGR(x: Int, y: Int, abgr: Int) = setPixel(x, y, swapRedBlue(abgr))
//?} else {
internal fun NativeImage.getPixel(x: Int, y: Int): Int = getPixelRGBA(x, y)

internal fun NativeImage.setPixelABGR(x: Int, y: Int, abgr: Int) = setPixelRGBA(x, y, abgr)

internal fun NativeImage.setPixel(x: Int, y: Int, argb: Int) = setPixelRGBA(x, y, swapRedBlue(argb))
//?}

internal fun GuiGraphicsExtractor.blitPreview(view: GpuTextureView, x0: Int, y0: Int, x1: Int, y1: Int, u0: Float, u1: Float, v0: Float, v1: Float) {
	flush()
	RenderSystem.setShaderTexture(0, view.texture.target.colorTextureId)
	//? if >=1.21.4 {
	RenderSystem.setShader(net.minecraft.client.renderer.CoreShaders.POSITION_TEX)
	//?} else
	RenderSystem.setShader(GameRenderer::getPositionTexShader)
	val matrix = pose().last().pose()
	val vertices = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX)
	vertices.addVertex(matrix, x0.toFloat(), y0.toFloat(), 0f).setUv(u0, v0)
	vertices.addVertex(matrix, x0.toFloat(), y1.toFloat(), 0f).setUv(u0, v1)
	vertices.addVertex(matrix, x1.toFloat(), y1.toFloat(), 0f).setUv(u1, v1)
	vertices.addVertex(matrix, x1.toFloat(), y0.toFloat(), 0f).setUv(u1, v0)
	BufferUploader.drawWithShader(vertices.buildOrThrow())
}

internal enum class TextureFormat { RGBA8 }

internal class GpuTexture(val target: RenderTarget) {
	val format get() = TextureFormat.RGBA8

	fun getWidth(mip: Int) = target.width

	fun getHeight(mip: Int) = target.height

	fun close() = target.destroyBuffers()

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

internal val RenderTarget.colorTexture get() = GpuTexture(this)

internal class GpuFence(private val sync: Long) {
	fun awaitCompletion(timeout: Long): Boolean =
		GL32.glClientWaitSync(sync, GL32.GL_SYNC_FLUSH_COMMANDS_BIT, timeout).let { it == GL32.GL_ALREADY_SIGNALED || it == GL32.GL_CONDITION_SATISFIED }

	fun close() = GL32.glDeleteSync(sync)
}

internal class GpuBuffer(private val size: Int) {
	val id = GlStateManager._glGenBuffers()

	init {
		GlStateManager._glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, id)
		GL15.glBufferData(GL21.GL_PIXEL_PACK_BUFFER, size.toLong(), GL15.GL_STREAM_READ)
		GlStateManager._glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0)
	}

	fun map(read: Boolean, write: Boolean): MappedView {
		GlStateManager._glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, id)
		val data = GL30.glMapBufferRange(GL21.GL_PIXEL_PACK_BUFFER, 0, size.toLong(), GL30.GL_MAP_READ_BIT)!!
		GlStateManager._glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0)
		return MappedView(id, data)
	}

	fun close() = GlStateManager._glDeleteBuffers(id)

	class MappedView(private val id: Int, private val data: ByteBuffer) : AutoCloseable {
		fun data() = data

		override fun close() {
			GlStateManager._glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, id)
			GL15.glUnmapBuffer(GL21.GL_PIXEL_PACK_BUFFER)
			GlStateManager._glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0)
		}
	}

	companion object {
		const val USAGE_MAP_READ = 0
		const val USAGE_COPY_DST = 0
	}
}

internal object CommandEncoder {
	fun copyTextureToBuffer(texture: GpuTexture, buffer: GpuBuffer, offset: Int, callback: Runnable, mip: Int) {
		GlStateManager._glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, buffer.id)
		GlStateManager._bindTexture(texture.target.colorTextureId)
		GlStateManager._getTexImage(GL11.GL_TEXTURE_2D, mip, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, offset.toLong())
		GlStateManager._glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0)
		callback.run()
	}

	fun copyTextureToTexture(source: GpuTexture, destination: GpuTexture, mip: Int, x: Int, y: Int, sourceX: Int, sourceY: Int, width: Int, height: Int) {
		GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, source.target.frameBufferId)
		GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, destination.target.frameBufferId)
		GlStateManager._glBlitFrameBuffer(sourceX, sourceY, width, height, x, y, width, height, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST)
		GlStateManager._glBindFramebuffer(GlConst.GL_FRAMEBUFFER, 0)
		Minecraft.getInstance().mainRenderTarget.bindWrite(false)
	}

	fun writeToTexture(texture: GpuTexture, image: NativeImage) {
		GlStateManager._bindTexture(texture.target.colorTextureId)
		image.upload(0, 0, 0, false)
	}

	fun presentTexture(view: GpuTextureView) = view.texture.target.blitToScreen(view.texture.target.width, view.texture.target.height)

	fun createFence() = GpuFence(GL32.glFenceSync(GL32.GL_SYNC_GPU_COMMANDS_COMPLETE, 0))

	fun submit() {}
}

internal object RenderSystem.getDevice() {
	val maxTextureSize: Int get() = RenderSystem.maxSupportedTextureSize()

	fun createCommandEncoder() = CommandEncoder

	fun createBuffer(label: () -> String, usage: Int, size: Int) = GpuBuffer(size)

	fun createTexture(label: () -> String, usage: Int, format: TextureFormat, width: Int, height: Int, depth: Int, mips: Int): GpuTexture =
		//? if >=1.21.4 {
		GpuTexture(TextureTarget(width, height, false).apply { setFilterMode(GL11.GL_LINEAR) }).also {
		//?} else
		GpuTexture(TextureTarget(width, height, false, Minecraft.ON_OSX).apply { setFilterMode(GL11.GL_LINEAR) }).also {
			Minecraft.getInstance().mainRenderTarget.bindWrite(true)
		}

	fun createTextureView(texture: GpuTexture) = GpuTextureView(texture)
}
*///?}

//? if >=26.2 {
internal fun glTextureId(texture: GpuTexture): Int? = (texture as? GlTexture)?.glId()

internal fun glBufferId(buffer: GpuBuffer): Int? = (buffer as? GlBuffer)?.handle()
//?} elif >=1.21.8 {
/*internal fun glTextureId(texture: GpuTexture): Int? = (texture as? GlTexture)?.glId()

internal fun glBufferId(buffer: GpuBuffer): Int? = (buffer as? Mixin_GlBuffer_Export)?.`keyframe$getHandle`()
*///?} else {
/*internal fun glTextureId(texture: GpuTexture): Int? = texture.target.colorTextureId

internal fun glBufferId(buffer: GpuBuffer): Int? = buffer.id
*///?}

internal fun supportsGl32() = true

internal fun backBufferProblem(width: Int, height: Int): String? = null

internal fun identifier(namespace: String, path: String): Identifier = Identifier.fromNamespaceAndPath(namespace, path)
