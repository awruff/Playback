package org.polyfrost.keyframe.standalone.editor

import net.minecraft.client.Minecraft
import org.polyfrost.keyframe.standalone.compat.*
import org.polyfrost.keyframe.standalone.export.Export
import org.polyfrost.keyframe.standalone.export.TitleSpan
import org.polyfrost.keyframe.standalone.playback.Keyframe
//? if >=1.21.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor
//?} else {
/*import net.minecraft.client.gui.GuiElement
import net.minecraft.client.render.Window
import net.minecraft.client.render.platform.GlStateManager
import org.polyfrost.keyframe.standalone.export.PreviewFrame
*///?}

private const val WHITE = -1
private const val BACKDROP = 0x99000000.toInt()
private const val PAD = 3
private const val EDGE = 0.08f
private const val MAX_WIDTH = 0.9f

internal data class TitlePlacement(val x: Int, val y: Int, val scale: Float)

internal fun placeTitle(title: TitleSpan, guiWidth: Int, guiHeight: Int, textWidth: Int, lineHeight: Int): TitlePlacement {
	val scale = minOf(guiHeight * title.size.height / lineHeight, guiWidth * MAX_WIDTH / (textWidth + PAD * 2))
	val width = guiWidth / scale
	val height = guiHeight / scale
	val y = when (title.position) {
		TitlePosition.TOP -> height * EDGE
		TitlePosition.MIDDLE -> (height - lineHeight) / 2
		TitlePosition.BOTTOM -> height * (1 - EDGE) - lineHeight
	}
	return TitlePlacement(((width - textWidth) / 2).toInt(), y.toInt(), scale)
}

object TitleOverlay {
	private fun shown(): List<TitleSpan> {
		if (Export.active) {
			val time = Export.titleSeconds() ?: return emptyList()
			return Export.titles.filter { it.text.isNotBlank() && time >= it.from && time < it.to }
		}
		if (!Keyframe.previewing) return emptyList()
		return titlesAt(Editor.titles, Editor.playhead).filter { it.text.isNotBlank() }.map { TitleSpan(it.text, 0f, 0f, it.position, it.size) }
	}

	//? if >=1.21.1 {
	@JvmStatic
	fun extract(graphics: GuiGraphicsExtractor) {
		val titles = shown()
		if (titles.isEmpty()) return
		val font = Minecraft.getInstance().font
		for (title in titles) {
			val textWidth = font.width(title.text)
			val (x, y, scale) = placeTitle(title, graphics.guiWidth(), graphics.guiHeight(), textWidth, font.lineHeight)
			graphics.pose().pushMatrix().scale(scale)
			graphics.fill(x - PAD, y - PAD, x + textWidth + PAD, y + font.lineHeight + PAD - 1, BACKDROP)
			//? if >=26.1 {
			graphics.text(font, title.text, x, y, WHITE, true)
			//?} else
			/*graphics.drawString(font, title.text, x, y, WHITE, true)*/
			graphics.pose().popMatrix()
		}
	}
	//?} else {
	/*@JvmStatic
	fun draw() {
		val titles = shown()
		if (titles.isEmpty()) return
		val mc = Minecraft.getInstance()
		val window = Window(mc)
		val guiWidth = PreviewFrame.guiWidth.takeIf { it > 0 } ?: window.width
		val guiHeight = PreviewFrame.guiHeight.takeIf { it > 0 } ?: window.height
		val font = mc.textRenderer
		for (title in titles) {
			val textWidth = font.getWidth(title.text)
			val (x, y, scale) = placeTitle(title, guiWidth, guiHeight, textWidth, font.fontHeight)
			GlStateManager.pushMatrix()
			GlStateManager.scalef(scale, scale, 1f)
			GuiElement.fill(x - PAD, y - PAD, x + textWidth + PAD, y + font.fontHeight + PAD - 1, BACKDROP)
			font.drawWithShadow(title.text, x.toFloat(), y.toFloat(), WHITE)
			GlStateManager.popMatrix()
		}
	}
	*///?}
}
