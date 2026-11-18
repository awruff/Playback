package org.polyfrost.keyframe.standalone.export

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiElement
import net.minecraft.client.render.Window
import net.minecraft.client.render.platform.GlStateManager
import net.minecraft.client.render.vertex.DefaultVertexFormat
import net.minecraft.client.render.vertex.Tesselator
import org.polyfrost.keyframe.standalone.compat.*
import org.polyfrost.keyframe.standalone.editor.Editor
import org.polyfrost.keyframe.standalone.editor.hudArea
import org.polyfrost.keyframe.standalone.playback.Keyframe
import kotlin.math.ceil

private const val BLACK = 0xFF000000.toInt()
private const val MIN_GUI_WIDTH = 320
private const val MIN_GUI_HEIGHT = 240

object PreviewFrame {
	@JvmStatic
	var guiWidth = 0
		private set

	@JvmStatic
	var guiHeight = 0
		private set

	fun draw() {
		val area = PreviewTexture.bounds ?: return
		if (!Keyframe.previewing) return
		val view = PreviewTexture.target() ?: return
		val mc = Minecraft.getInstance()
		val left = (area.left * mc.width).toDouble()
		val top = (area.top * mc.height).toDouble()
		val right = (area.right * mc.width).toDouble()
		val bottom = (area.bottom * mc.height).toDouble()
		val scale = 1f / Window(mc).scale
		GlStateManager.pushMatrix()
		GlStateManager.scalef(scale, scale, 1f)
		GuiElement.fill(left.toInt(), top.toInt(), right.toInt(), bottom.toInt(), BLACK)
		GlStateManager.enableTexture()
		GlStateManager.color4f(1f, 1f, 1f, 1f)
		val texture = view.texture
		GlStateManager.bindTexture(texture.id)
		val u0 = PreviewTexture.crop.u0.toDouble() * texture.u
		val u1 = PreviewTexture.crop.u1.toDouble() * texture.u
		val v = texture.v.toDouble()
		val tesselator = Tesselator.getInstance()
		val buffer = tesselator.buffer
		buffer.begin(7, DefaultVertexFormat.POSITION_TEX)
		buffer.vertex(left, top, 0.0).texture(u0, v).nextVertex()
		buffer.vertex(left, bottom, 0.0).texture(u0, 0.0).nextVertex()
		buffer.vertex(right, bottom, 0.0).texture(u1, 0.0).nextVertex()
		buffer.vertex(right, top, 0.0).texture(u1, v).nextVertex()
		tesselator.end()
		GlStateManager.popMatrix()
	}

	@JvmStatic
	fun fitGui(draw: Runnable) {
		if (!Keyframe.previewing || PreviewTexture.bounds == null) return draw.run()
		val mc = Minecraft.getInstance()
		val window = Window(mc)
		val area = hudArea(window.width, window.height, PreviewTexture.crop, exportGuiHeight())
		val pixels = window.scale * area.scale
		GlStateManager.viewport((area.x * window.scale).toInt(), 0, (area.width * pixels).toInt(), mc.height)
		guiWidth = area.width
		guiHeight = area.height
		try {
			mc.gameRenderer.setupGuiState()
			draw.run()
		} finally {
			guiWidth = 0
			guiHeight = 0
			GlStateManager.viewport(0, 0, mc.width, mc.height)
			mc.gameRenderer.setupGuiState()
		}
	}

	private fun exportGuiHeight(): Int {
		val mc = Minecraft.getInstance()
		val limit = mc.options.guiScale.takeIf { it != 0 } ?: 1000
		var scale = 1
		while (scale < limit && Editor.exportWidth / (scale + 1) >= MIN_GUI_WIDTH && Editor.exportHeight / (scale + 1) >= MIN_GUI_HEIGHT) scale++
		if (mc.isUnicode && scale % 2 != 0 && scale != 1) scale--
		return ceil(Editor.exportHeight.toDouble() / scale).toInt()
	}
}
