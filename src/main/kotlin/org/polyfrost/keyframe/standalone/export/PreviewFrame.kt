package org.polyfrost.keyframe.standalone.export

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import org.polyfrost.keyframe.standalone.compat.*
import org.polyfrost.keyframe.standalone.editor.Editor
import org.polyfrost.keyframe.standalone.editor.hudArea
import org.polyfrost.keyframe.standalone.mixin.Mixin_Window_Editor
import org.polyfrost.keyframe.standalone.playback.Keyframe
import kotlin.math.ceil

private const val BLACK = 0xFF000000.toInt()

object PreviewFrame {
	fun draw(graphics: GuiGraphicsExtractor) {
		val area = PreviewTexture.bounds ?: return
		if (!Keyframe.previewing) return
		val view = PreviewTexture.target() ?: return
		val mc = Minecraft.getInstance()
		val window = mc.window
		val left = (area.left * window.width).toInt()
		val top = (area.top * window.height).toInt()
		val right = (area.right * window.width).toInt()
		val bottom = (area.bottom * window.height).toInt()
		graphics.pose().pushMatrix().scale(1f / window.guiScale)
		graphics.fill(left, top, right, bottom, BLACK)
		graphics.blitPreview(view, left, top, right, bottom, PreviewTexture.crop.u0, PreviewTexture.crop.u1, 1f, 0f)
		graphics.pose().popMatrix()
	}

	fun fitGui(graphics: GuiGraphicsExtractor, extract: Runnable) {
		if (!Keyframe.previewing || PreviewTexture.bounds == null) return extract.run()
		val mc = Minecraft.getInstance()
		val window = mc.window
		val guiWidth = window.guiScaledWidth
		val guiHeight = window.guiScaledHeight
		val area = hudArea(guiWidth, guiHeight, PreviewTexture.crop, exportGuiHeight())
		@Suppress("CAST_NEVER_SUCCEEDS")
		val sizes = window as Mixin_Window_Editor
		graphics.pose().pushMatrix().translate(area.x, 0f).scale(area.scale)
		sizes.`keyframe$setGuiScaledWidth`(area.width)
		sizes.`keyframe$setGuiScaledHeight`(area.height)
		try {
			extract.run()
		} finally {
			sizes.`keyframe$setGuiScaledWidth`(guiWidth)
			sizes.`keyframe$setGuiScaledHeight`(guiHeight)
			graphics.pose().popMatrix()
		}
	}

	private fun exportGuiHeight(): Int {
		val mc = Minecraft.getInstance()
		val window = mc.window
		val width = window.width
		val height = window.height
		window.width = Editor.exportWidth
		window.height = Editor.exportHeight
		val scale = window.calculateScale(mc.options.guiScale().get(), mc.isEnforceUnicode)
		window.width = width
		window.height = height
		return ceil(Editor.exportHeight.toDouble() / scale).toInt()
	}
}
