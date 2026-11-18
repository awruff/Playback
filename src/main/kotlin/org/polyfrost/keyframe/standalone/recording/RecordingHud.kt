package org.polyfrost.keyframe.standalone.recording

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import org.polyfrost.compose.composables.PolyBox
import org.polyfrost.compose.composables.PolyMcText
import org.polyfrost.compose.composables.PolyModifier
import org.polyfrost.compose.composables.PolyRow
import org.polyfrost.compose.composables.PolyText
import org.polyfrost.compose.composables.align
import org.polyfrost.compose.composables.background
import org.polyfrost.compose.composables.padding
import org.polyfrost.compose.composables.size
import org.polyfrost.compose.layout.PolyAlign
import org.polyfrost.compose.layout.PolyInsets
import org.polyfrost.compose.render.PolyColor
import org.polyfrost.oneconfig.api.config.v1.annotations.Color
import org.polyfrost.oneconfig.api.config.v1.annotations.Slider
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch
import org.polyfrost.oneconfig.api.config.v1.annotations.Text
import org.polyfrost.oneconfig.api.hud.v1.Font
import org.polyfrost.oneconfig.api.hud.v1.Hud
import org.polyfrost.oneconfig.api.hud.v1.HudManager
import java.util.function.BooleanSupplier

private const val UNMEASURED = -1f
private const val POPPINS_SIZE = 8f
private const val BLINK_NANOS = 500_000_000L

class RecordingHud : Hud("keyframe_recording.json", "Recording", Category.INFO) {
	@Text(title = "Text")
	var text = "Recording"

	@Switch(title = "Show circle")
	var showCircle = true

	@Slider(title = "Circle radius", min = 1f, max = 12f, step = 0.5f)
	var circleRadius = 3.5f

	@Color(title = "Circle color")
	var circleColor = PolyColor(0xFFFF5555.toInt())

	@Switch(title = "Blink")
	var blink = false

	@Slider(title = "Gap", min = 0f, max = 12f, step = 0.5f)
	var gap = 3f

	private data class Look(val text: String, val radius: Float, val color: PolyColor, val gap: Float)

	@Transient
	private var look = mutableStateOf<Look?>(null)

	@Transient
	private var lit = mutableStateOf(true)

	init {
		padLeft = 4f
		padRight = 4f
		padTop = 4f
		padBottom = 4f
		staticWidth = false
		staticW = UNMEASURED
		staticH = UNMEASURED
	}

	override fun canMergeBackground(): Boolean = true

	override fun showByDefault(): Boolean = true

	override fun shouldShow(): Boolean = recording.asBoolean

	override val alwaysRedraw: Boolean
		get() = super.alwaysRedraw || (showCircle && circleColor.chroma)

	override fun updateFrequency(): Long = if (blink) BLINK_NANOS else -1L

	override fun update(): Boolean {
		val next = Look(
			when (caseType) {
				1 -> text.uppercase()
				2 -> text.lowercase()
				else -> text
			},
			if (showCircle) circleRadius else 0f,
			circleColor,
			gap,
		)
		val current = look.value
		val on = !blink || HudManager.isEditing || (System.nanoTime() / BLINK_NANOS) % 2 == 0L
		if (current == next && lit.value == on) return true
		look.value = next
		lit.value = on
		contentChanged()
		return true
	}

	@Composable
	override fun Content() {
		val current = look.value ?: return
		val static = staticWidth && staticW > 0f && staticH > 0f
		val insets = PolyInsets(padLeft, padTop, padRight, padBottom)
		val outer = if (static) hudBackground().size(staticW, staticH).padding(insets) else hudBackground().padding(insets)

		PolyBox(modifier = outer) {
			PolyRow(
				gap = if (current.radius > 0f && current.text.isNotEmpty()) current.gap else 0f,
				modifier = if (static) PolyModifier.align(alignment) else PolyModifier,
			) {
				if (current.radius > 0f) {
					val size = current.radius * 2f
					val color = if (lit.value) current.color else current.color.withAlpha(0)
					PolyBox(modifier = PolyModifier.size(size, size).background(color, current.radius).align(PolyAlign.Center)) {}
				}
				if (current.text.isNotEmpty()) Label(current.text)
			}
		}
	}

	@Composable
	private fun Label(text: String) {
		val color = PolyColor(textColor, textChroma, textChromaSpeed)
		val modifier = PolyModifier.align(PolyAlign.Center)
		if (font == Font.Minecraft) {
			val styled = buildString {
				if (textBold) append("§l")
				if (textItalic) append("§o")
				if (textUnderline) append("§n")
				append(text)
			}
			PolyMcText(
				styled,
				color = color,
				outlineColor = if (showShadow && fullShadow) PolyColor(shadowColor, shadowChroma, shadowChromaSpeed) else null,
				shadow = showShadow && !fullShadow,
				scale = textScale,
				modifier = modifier,
			)
		} else {
			PolyText(
				text,
				color = color,
				fontSize = POPPINS_SIZE * textScale,
				shadow = showShadow,
				shadowColor = PolyColor(shadowColor, shadowChroma, shadowChromaSpeed),
				shadowOffset = shadowOffsetX,
				font = getPoppinsFontName(),
				modifier = modifier,
			)
		}
	}

	override fun minimumSize(): Pair<Float, Float> = (padLeft + padRight + 2f) to (padTop + padBottom + 2f)

	override fun setup() {
		super.setup()
		if (isReal) {
			for (option in listOf("text", "showCircle", "circleRadius", "circleColor", "blink", "gap")) updateWhenChanged(option)
		}
		update()
		reseedStaticSizeIfNeeded()
		captureStaticSizeDefaults()
	}

	override fun clone(): Hud = (super.clone() as RecordingHud).also {
		it.look = mutableStateOf(null)
		it.lit = mutableStateOf(true)
	}

	companion object {
		@Volatile
		private var recording = BooleanSupplier { false }

		@JvmStatic
		fun register(configId: String, recording: BooleanSupplier) {
			this.recording = recording
			HudManager.register(RecordingHud(), configId, "/assets/keyframe/icon.png")
		}
	}
}
