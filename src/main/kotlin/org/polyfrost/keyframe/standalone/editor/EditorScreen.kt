@file:OptIn(ExperimentalOneConfigUiApi::class, ExperimentalLayoutApi::class)

package org.polyfrost.keyframe.standalone.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mojang.blaze3d.platform.InputConstants
import org.polyfrost.keyframe.standalone.StandaloneConfig
import org.polyfrost.keyframe.standalone.export.PreviewFrame
import org.polyfrost.keyframe.standalone.export.PreviewTexture
import org.polyfrost.keyframe.standalone.recording.RecordingInfo
//? if >=1.21.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor
//?} else {
/*import net.minecraft.client.gui.GuiElement
*///?}
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.a11y.Motion
import org.polyfrost.oneconfig.api.ui.v1.components.Chip
import org.polyfrost.oneconfig.api.ui.v1.components.Icon
import org.polyfrost.oneconfig.api.ui.v1.components.IconButton
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.components.Tooltip
import org.polyfrost.oneconfig.api.ui.v1.components.fadingEdges
import org.polyfrost.oneconfig.api.ui.v1.components.onClick
import org.polyfrost.oneconfig.api.ui.v1.components.rememberInteractionSource
import org.polyfrost.oneconfig.api.ui.v1.keybind.TextInputFocus
import org.polyfrost.oneconfig.api.ui.v1.keybind.trackTextInputFocus
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.oneconfig.api.ui.v1.theme.ProvideOneConfigTheme
import org.polyfrost.oneconfig.api.ui.v1.theme.concentric
import org.polyfrost.oneconfig.internal.ui.components.settings.LocalOptionWidth
import org.polyfrost.oneconfig.internal.ui.components.settings.Option
import org.polyfrost.compose.minecraft.screen.ComposeScreen

private const val PLAY_ICON = "/assets/keyframe/ico/play.svg"
private const val PAUSE_ICON = "/assets/keyframe/ico/pause.svg"

private class Shape(val name: String, val wide: Int, val tall: Int)

private val SHAPES = listOf(
	Shape("Wide", 16, 9),
	Shape("Vertical", 9, 16),
	Shape("Square", 1, 1),
	Shape("Classic", 4, 3),
)
private val SIZES = listOf("1080p" to 1080, "1440p" to 1440, "4K" to 2160)
private val SETTINGS = listOf("audio", "hud")
private val MORE_SETTINGS = listOf("container", "autoEncoder", "encoder", "quality", "colorFormat", "keyframeInterval", "preview", "outputFolder", "extraArgs", "ffmpegPath")
private const val MAX_NAME_LENGTH = 64

internal val PANEL_PADDING = 12.dp
internal val PANEL_GAP = 8.dp
private val CONTROL_HEIGHT = 32.dp
private val ICON_BUTTON_SIZE = 26.dp
private const val BACKDROP_ALPHA = 0.5f
internal const val FADE_MILLIS = 150
private const val PRESS_SCALE = 0.97f
private val DANGER = Color(0xFFE35B5B)
private val DIALOG_WIDTH = 340.dp
private var focusedInput: () -> Rect = { Rect.Zero }

internal fun <T> panelMotion(threshold: T? = null): FiniteAnimationSpec<T> =
	if (!Motion.reduced) spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow, threshold) else snap()

class EditorScreen : ComposeScreen(RenderMode.ON_DEMAND) {
	private var backdrop = Color.Transparent

	@Composable
	override fun compose() {
		ProvideOneConfigTheme {
			backdrop = OneConfigTheme.current.pageBackground.copy(alpha = BACKDROP_ALPHA)
			Box(Modifier.fillMaxSize()) {
				EditorPages()
				Editor.menu?.let { ContextMenuLayer(it) }
				Crossfade(Editor.deleting, animationSpec = tween(FADE_MILLIS)) { if (it != null) DeleteDialog(it) }
			}
		}
	}

	//? if >=1.21.11
	override fun blurAlpha(): Float = 1f

	//? if >=1.21.1 {
	override fun extractBackground(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, tickDelta: Float) {
		//? if <1.21.8 {
		/*ctx.flush()
		PreviewTexture.capture()
		*///?}
		super.extractBackground(ctx, mouseX, mouseY, tickDelta)
		//? if >=1.21.8 <1.21.11
		/*ctx.blurBeforeThisStratum()*/
		ctx.fill(0, 0, width, height, backdrop.toArgb())
		PreviewFrame.draw(ctx)
	}

	override fun onClose() {
		super.onClose()
		Editor.close()
	}
	//?} else {
	/*override fun render(mouseX: Int, mouseY: Int, tickDelta: Float) {
		PreviewTexture.capture()
		GuiElement.fill(0, 0, width, height, backdrop.toArgb())
		PreviewFrame.draw()
		super.render(mouseX, mouseY, tickDelta)
	}
	*///?}

	override fun handleKeyPressed(key: Int, modifiers: Int): Boolean {
		if (Editor.deleting != null) {
			if (key == InputConstants.KEY_ESCAPE) Editor.deleting = null
			return true
		}
		if (TextInputFocus.isTyping) return false
		if (Editor.menu != null) {
			Editor.menu = null
			if (key == InputConstants.KEY_ESCAPE) return true
		}
		//? if <1.21.1 {
		/*if (key == InputConstants.KEY_ESCAPE) {
			Editor.close()
			return true
		}
		*///?}
		val ctrl = modifiers and (InputConstants.MOD_CONTROL or InputConstants.MOD_SUPER) != 0
		val shift = modifiers and InputConstants.MOD_SHIFT != 0
		if (ctrl) {
			when (key) {
				InputConstants.KEY_Z -> if (shift) Editor.redo() else Editor.undo()
				InputConstants.KEY_Y -> Editor.redo()
				InputConstants.KEY_K, InputConstants.KEY_B -> Editor.cutAt(Editor.playhead)
				InputConstants.KEY_X -> Editor.cutSelected()
				InputConstants.KEY_C -> Editor.copySelected()
				InputConstants.KEY_V -> Editor.paste()
				InputConstants.KEY_DELETE -> Editor.clear()
				InputConstants.KEY_M, InputConstants.KEY_E -> if (Editor.canExport) Editor.export()
				InputConstants.KEY_EQUALS -> Editor.zoomBy(2f)
				InputConstants.KEY_MINUS -> Editor.zoomBy(0.5f)
				else -> return false
			}
			return true
		}
		when (key) {
			InputConstants.KEY_SPACE -> Editor.togglePlaying()
			InputConstants.KEY_J -> Editor.shuttleBack()
			InputConstants.KEY_K -> Editor.stop()
			InputConstants.KEY_L -> Editor.shuttleForward()
			InputConstants.KEY_S -> Editor.cutAt(Editor.playhead)
			InputConstants.KEY_T -> Editor.addTitle()
			InputConstants.KEY_C, InputConstants.KEY_B -> Editor.blade = true
			InputConstants.KEY_V, InputConstants.KEY_A -> Editor.blade = false
			InputConstants.KEY_Q -> Editor.trimToPlayhead(atEnd = false)
			InputConstants.KEY_W -> Editor.trimToPlayhead(atEnd = true)
			InputConstants.KEY_DELETE, InputConstants.KEY_BACKSPACE -> Editor.deleteSelected()
			InputConstants.KEY_LEFT -> Editor.step(if (shift) -TICKS_PER_SECOND else -1)
			InputConstants.KEY_RIGHT -> Editor.step(if (shift) TICKS_PER_SECOND else 1)
			InputConstants.KEY_UP -> Editor.previousEdit()
			InputConstants.KEY_DOWN -> Editor.nextEdit()
			InputConstants.KEY_HOME -> Editor.seek(0)
			InputConstants.KEY_END -> Editor.seek(Editor.total)
			InputConstants.KEY_EQUALS -> Editor.zoomBy(2f)
			InputConstants.KEY_MINUS -> Editor.zoomBy(0.5f)
			InputConstants.KEY_BACKSLASH -> Editor.zoom = 1f
			else -> {
				if (key != InputConstants.KEY_Z || !shift) return false
				Editor.zoom = 1f
			}
		}
		return true
	}
}

@Composable
internal fun Modifier.releasesFocus(): Modifier {
	val focus = LocalFocusManager.current
	return pointerInput(Unit) {
		awaitEachGesture {
			val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
			if (down.position !in focusedInput()) focus.clearFocus()
		}
	}
}

@Composable
private fun Panel(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
	val theme = OneConfigTheme.current
	Column(
		modifier
			.clip(theme.backgroundShape)
			.background(theme.pageBackground)
			.border(1.dp, theme.borderColor, theme.backgroundShape)
			.padding(PANEL_PADDING),
		content = content,
	)
}

@Composable
internal fun Label(
	text: String,
	modifier: Modifier = Modifier,
	color: Color = OneConfigTheme.current.textColor,
	size: TextUnit = 13.sp,
	weight: FontWeight = FontWeight.Normal,
	lines: Int = 1,
) {
	Text(text, modifier, color, OneConfigTheme.typography.body.copy(fontSize = size, fontWeight = weight), lines, TextOverflow.Ellipsis)
}

@Composable
internal fun muted(): Color = OneConfigTheme.current.textColor.copy(alpha = 0.7f)

@Composable
internal fun PanelIcon(
	icon: String,
	tip: String,
	enabled: Boolean = true,
	above: Boolean = false,
	onClick: () -> Unit,
) {
	val text = OneConfigTheme.current.textColor
	Tooltip({ Label(tip) }, Modifier, if (above) Alignment.TopCenter else Alignment.BottomCenter) {
		IconButton(icon, tip, onClick, Modifier.size(ICON_BUTTON_SIZE), enabled = enabled, restTint = text.copy(alpha = 0.7f))
	}
}

@Composable
internal fun SmallHeading(text: String) {
	Label(text, Modifier.padding(top = 12.dp, bottom = 6.dp), OneConfigTheme.current.textColorSecondary, 12.sp, FontWeight.Medium)
}

@Composable
internal fun ActionButton(
	label: String,
	icon: String?,
	modifier: Modifier = Modifier,
	enabled: Boolean = true,
	primary: Boolean = false,
	accent: Color = OneConfigTheme.accent,
	onClick: () -> Unit,
) {
	val theme = OneConfigTheme.current
	val source = rememberInteractionSource()
	val hovered by source.collectIsHoveredAsState()
	val background by animateColorAsState(
		when {
			!enabled -> theme.componentBackground.copy(alpha = 0.4f)
			primary -> if (hovered) accent.copy(alpha = 0.85f) else accent
			hovered -> theme.chipBackground
			else -> theme.componentBackground
		},
	)
	val foreground by animateColorAsState(
		when {
			!enabled -> theme.textColorSecondary
			primary -> theme.accentTextColor
			else -> theme.textColor
		},
	)
	val pressed by source.collectIsPressedAsState()
	val press by animateFloatAsState(if (pressed) PRESS_SCALE else 1f, tween(FADE_MILLIS))
	Box(
		modifier
			.height(CONTROL_HEIGHT)
			.graphicsLayer {
				scaleX = press
				scaleY = press
			}
			.clip(theme.buttonShape)
			.background(background)
			.onClick(source, enabled, onClick)
			.padding(horizontal = 10.dp),
		contentAlignment = Alignment.Center,
	) {
		Crossfade(label to icon, animationSpec = tween(FADE_MILLIS)) { (text, glyph) ->
			Row(
				horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
				verticalAlignment = Alignment.CenterVertically,
			) {
				if (glyph != null) Icon(glyph, null, Modifier.size(15.dp), foreground)
				Label(text, color = foreground, weight = FontWeight.Medium)
			}
		}
	}
}

@Composable
internal fun ColumnScope.RecordingList() {
	val library = Editor.library
	when {
		library.isNotEmpty() -> LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
			items(library, key = { it.fileName }) {
				RecordingRow(it, Modifier.animateItem(placementSpec = panelMotion(IntOffset.VisibilityThreshold)))
			}
		}
		Editor.scanning -> Label("Reading your recordings", color = muted())
		else -> Label(
			"No recordings yet. Join a world and run /keyframe-record to start one.",
			color = muted(),
			lines = 4,
		)
	}
}

@Composable
private fun RecordingRow(info: RecordingInfo, modifier: Modifier) {
	val theme = OneConfigTheme.current
	val source = rememberInteractionSource()
	val hovered by source.collectIsHoveredAsState()
	val background by animateColorAsState(if (hovered) theme.chipBackground else Color.Transparent)
	val shape = theme.backgroundShape.concentric(PANEL_PADDING)
	var renaming by remember { mutableStateOf(false) }
	Row(
		modifier.fillMaxWidth()
			.clip(shape)
			.background(background)
			.hoverable(source)
			.pointerInput(info) { detectTapGestures(onDoubleTap = { if (!renaming) Editor.add(info) }) }
			.padding(horizontal = 10.dp, vertical = 8.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Crossfade(renaming, Modifier.weight(1f), tween(FADE_MILLIS)) { editing ->
			if (editing) {
				TextInput(Editor.label(info), Modifier.fillMaxWidth(), focusNow = true, filter = { it.take(MAX_NAME_LENGTH) }) {
					Editor.rename(info, it)
					renaming = false
					Editor.label(info)
				}
			} else {
				Column {
					Label(Editor.label(info), color = theme.textColor, weight = FontWeight.Medium)
					Label(recordingDetail(info), color = muted(), size = 11.sp)
				}
			}
		}
		AnimatedVisibility(hovered && !renaming) {
			PanelIcon("text-input", "Rename") { renaming = true }
		}
		AnimatedVisibility(hovered && !renaming) {
			PanelIcon("trash", "Delete") { Editor.deleting = info }
		}
		AnimatedVisibility(!renaming) {
			PanelIcon("plus", "Add to the timeline") { Editor.add(info) }
		}
	}
}

@Composable
private fun DeleteDialog(info: RecordingInfo) {
	Box(
		Modifier.fillMaxSize()
			.background(Color.Black.copy(alpha = 0.5f))
			.pointerInput(Unit) { detectTapGestures { Editor.deleting = null } },
		contentAlignment = Alignment.Center,
	) {
		Panel(Modifier.width(DIALOG_WIDTH).pointerInput(Unit) { detectTapGestures {} }) {
			Label("Delete \"${Editor.label(info)}\"?", size = 15.sp, weight = FontWeight.SemiBold)
			Label(
				"This removes the recording from your computer. It can't be undone.",
				Modifier.padding(top = PANEL_GAP),
				muted(),
				lines = 2,
			)
			val used = Editor.clipsUsing(info)
			if (used > 0) {
				Label(
					if (used == 1) "1 clip on your timeline uses it and will be removed." else "$used clips on your timeline use it and will be removed.",
					Modifier.padding(top = 4.dp),
					muted(),
					lines = 2,
				)
			}
			Row(
				Modifier.fillMaxWidth().padding(top = PANEL_PADDING),
				horizontalArrangement = Arrangement.spacedBy(PANEL_GAP, Alignment.End),
			) {
				ActionButton("Cancel", null) { Editor.deleting = null }
				ActionButton("Delete", "trash", primary = true, accent = DANGER) { Editor.confirmDelete() }
			}
		}
	}
}

private fun recordingDetail(info: RecordingInfo): String {
	val ticks = info.clips.sumOf { it.end - it.start }.takeIf { it > 0 } ?: (info.lastTick - info.firstTick)
	val count = info.clips.size
	return if (count > 1) "${formatTime(ticks)} in $count clips" else formatTime(ticks)
}

@Composable
internal fun PreviewArea(modifier: Modifier) {
	val theme = OneConfigTheme.current
	val window = LocalWindowInfo.current.containerSize
	val crop = previewCrop(window.width.toFloat() / maxOf(window.height, 1), Editor.exportWidth.toFloat() / Editor.exportHeight)
	BoxWithConstraints(modifier) {
		val frameWidth = minOf(maxWidth, maxHeight * crop.aspect)
		val frameHeight = frameWidth / crop.aspect
		val left = (maxWidth - frameWidth) / 2
		val top = (maxHeight - frameHeight) / 2
		val surround = theme.pageBackground
		val cover by animateColorAsState(if (Editor.live) Color.Black.copy(alpha = fadeAmount(Editor.clips, Editor.playhead)) else theme.pageBackground)
		Canvas(Modifier.fillMaxSize()) {
			val x = left.toPx()
			val y = top.toPx()
			val w = frameWidth.toPx()
			val h = frameHeight.toPx()
			drawRect(surround, Offset.Zero, Size(size.width, y))
			drawRect(surround, Offset(0f, y + h), Size(size.width, size.height - y - h))
			drawRect(surround, Offset(0f, y), Size(x, h))
			drawRect(surround, Offset(x + w, y), Size(size.width - x - w, h))
		}
		Box(
			Modifier.offset(left, top)
				.size(frameWidth, frameHeight)
				.background(cover)
				.onGloballyPositioned {
					val root = it.findRootCoordinates().size
					val box = it.boundsInRoot()
					PreviewTexture.place(
						Rect(box.left / root.width, box.top / root.height, box.right / root.width, box.bottom / root.height),
						crop,
					)
				},
			contentAlignment = Alignment.Center,
		) {
			val message = when {
				Editor.clips.isEmpty() -> "Add a recording to start editing"
				Editor.waiting -> "Finding this moment in the replay"
				else -> null
			}
			Crossfade(message, animationSpec = tween(FADE_MILLIS)) { if (it != null) PreviewMessage(it) }
		}
	}
}

@Composable
private fun PreviewMessage(text: String) {
	val theme = OneConfigTheme.current
	Label(
		text,
		Modifier.clip(theme.buttonShape).background(theme.popupBackground).padding(horizontal = 12.dp, vertical = 8.dp),
		lines = 2,
	)
}

@Composable
internal fun ColumnScope.VideoSettings(optionWidth: Dp, background: Color) {
	val width = Editor.exportWidth
	val height = Editor.exportHeight
	val shortEdge = minOf(width, height)
	val problem = Editor.exportProblem
	val scroll = rememberScrollState()
	LaunchedEffect(problem) { if (problem != null) Editor.moreSettings = true }
	Column(Modifier.weight(1f).fadingEdges(scroll, background).verticalScroll(scroll).padding(bottom = 12.dp)) {
		SmallHeading("Aspect ratio")
		ChipRow {
			SHAPES.forEach { shape ->
				val (w, h) = frameSize(shape.wide, shape.tall, shortEdge)
				Tooltip({ Label(shape.name) }, Modifier, Alignment.BottomCenter) {
					Chip("${shape.wide}:${shape.tall}", w == width && h == height, { Editor.setFrame(w, h) })
				}
			}
		}
		SmallHeading("Size")
		ChipRow {
			SIZES.forEach { (label, edge) ->
				Chip(label, edge == shortEdge, {
					val scale = edge.toDouble() / shortEdge
					Editor.setFrame(evenSize(width * scale), evenSize(height * scale))
				})
			}
		}
		SmallHeading("Frame rate")
		FrameRateField()
		CompositionLocalProvider(LocalOptionWidth provides optionWidth) {
			SETTINGS.forEach { Setting(it) }
			MoreToggle(Editor.moreSettings) { Editor.moreSettings = !Editor.moreSettings }
			AnimatedVisibility(Editor.moreSettings) {
				Column { MORE_SETTINGS.forEach { Setting(it) } }
			}
		}
	}
}

@Composable
internal fun ExportFooter() {
	val theme = OneConfigTheme.current
	val problem = Editor.exportProblem
	Crossfade(problem, Modifier.padding(vertical = PANEL_GAP), tween(FADE_MILLIS)) {
		Label(
			it ?: "${Editor.exportWidth} × ${Editor.exportHeight}, ${formatTime(Editor.total)} long",
			color = if (it != null) theme.textColor else muted(),
			size = 12.sp,
			lines = 2,
		)
	}
	ActionButton("Export video", null, Modifier.fillMaxWidth(), Editor.clips.isNotEmpty() && problem == null, primary = true) { Editor.export() }
}

@Composable
private fun Setting(id: String) {
	val property = StandaloneConfig.getTree().getProp(id)
	if (property.get() is Boolean) {
		Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
			Label("${property.title}", Modifier.weight(1f), OneConfigTheme.current.textColorSecondary, 12.sp, FontWeight.Medium, lines = 2)
			Option(property)
		}
	} else {
		SmallHeading("${property.title}")
		Box(Modifier.focusArea()) { Option(property) }
	}
	property.description?.let { Label("$it", Modifier.padding(top = 4.dp), muted(), 11.sp, lines = 4) }
}

@Composable
private fun MoreToggle(open: Boolean, onToggle: () -> Unit) {
	val color = OneConfigTheme.current.textColorSecondary
	val turn by animateFloatAsState(if (open) 0f else 180f)
	Row(
		Modifier.padding(top = 12.dp).onClick(rememberInteractionSource(), onToggle),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(4.dp),
	) {
		Label("More settings", color = color, size = 12.sp, weight = FontWeight.Medium)
		Icon("up", null, Modifier.size(14.dp).rotate(turn), color)
	}
}

@Composable
private fun Modifier.focusArea(): Modifier {
	val bounds = remember { mutableStateOf(Rect.Zero) }
	return onGloballyPositioned { bounds.value = it.boundsInRoot() }
		.onFocusChanged { if (it.hasFocus) focusedInput = { bounds.value } }
}

@Composable
internal fun TextInput(
	value: String,
	modifier: Modifier = Modifier,
	unit: String? = null,
	focusNow: Boolean = false,
	filter: (String) -> String = { it },
	commit: (String) -> String,
) {
	val theme = OneConfigTheme.current
	val accent = OneConfigTheme.accent
	val focus = LocalFocusManager.current
	val requester = remember { FocusRequester() }
	var field by remember { mutableStateOf(TextFieldValue(value, TextRange(0, value.length))) }
	var focused by remember { mutableStateOf(false) }
	if (focusNow) LaunchedEffect(Unit) { requester.requestFocus() }
	Row(
		modifier.height(CONTROL_HEIGHT)
			.focusArea()
			.clip(theme.buttonShape)
			.background(theme.componentBackground)
			.border(1.dp, if (focused) accent else theme.borderColor, theme.buttonShape)
			.padding(horizontal = 10.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		BasicTextField(
			field,
			{ field = it.copy(text = filter(it.text)) },
			Modifier.weight(1f)
				.focusRequester(requester)
				.trackTextInputFocus()
				.onFocusChanged {
					if (focused && !it.isFocused) field = TextFieldValue(commit(field.text))
					focused = it.isFocused
				}
				.onPreviewKeyEvent {
					if (it.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
					when (it.key) {
						Key.Enter, Key.NumPadEnter -> focus.clearFocus()
						Key.Escape -> {
							field = TextFieldValue(value)
							focus.clearFocus()
						}
						else -> return@onPreviewKeyEvent false
					}
					true
				},
			singleLine = true,
			textStyle = TextStyle(theme.textColor, 13.sp, fontFamily = theme.fontFamily),
			cursorBrush = SolidColor(accent),
		)
		if (unit != null) Label(unit, color = muted(), size = 12.sp)
	}
}

@Composable
private fun FrameRateField() {
	Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PANEL_GAP)) {
		TextInput(
			"${Editor.exportFps}",
			Modifier.width(92.dp),
			unit = "fps",
			filter = { it.filter(Char::isDigit).take(MAX_FRAME_RATE_DIGITS) },
		) {
			val fps = frameRate(it, Editor.exportFps)
			Editor.setFrameRate(fps)
			"$fps"
		}
		if (Editor.exportFps > SMOOTH_FRAME_RATE) Label("Above $SMOOTH_FRAME_RATE fps exports slowly and most players drop the extra frames", color = DANGER, size = 11.sp)
		else Label("At least $MIN_FRAME_RATE", color = muted(), size = 11.sp)
	}
}

@Composable
internal fun ChipRow(content: @Composable () -> Unit) {
	FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { content() }
}

@Composable
private fun BarGroup(gap: Dp = 4.dp, content: @Composable () -> Unit) {
	Row(horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) { content() }
}

@Composable
internal fun Transport() {
	BarGroup(PANEL_GAP) {
		Tooltip({ Label("Play or pause (Space). J, K and L shuttle") }, Modifier, Alignment.TopCenter) {
			ActionButton(if (Editor.playing) "Pause" else "Play", if (Editor.playing) PAUSE_ICON else PLAY_ICON, Modifier.width(84.dp), Editor.clips.isNotEmpty()) {
				Editor.togglePlaying()
			}
		}
		Label("${formatTimecode(Editor.playhead)} / ${formatTime(Editor.total)}", Modifier.width(104.dp), weight = FontWeight.Medium)
		AnimatedVisibility(Editor.reversing || (Editor.playing && Editor.speed > 1)) {
			Label("${if (Editor.reversing) "-" else ""}${Editor.speed}×", color = muted(), size = 12.sp)
		}
	}
}

@Composable
internal fun EditTools() {
	BarGroup {
		Tooltip({ Label("Split (S or Ctrl+K). C is the blade tool, V goes back") }, Modifier, Alignment.TopCenter) {
			ActionButton("Split", "scissors", enabled = Editor.clips.isNotEmpty(), primary = Editor.blade) { Editor.cutAt(Editor.playhead) }
		}
		Tooltip({ Label("Delete (Del). Ctrl+Del removes every clip") }, Modifier, Alignment.TopCenter) {
			ActionButton("Delete", "trash", enabled = inspecting) { Editor.deleteSelected() }
		}
		Tooltip({ Label("Add a title at the playhead (T)") }, Modifier, Alignment.TopCenter) {
			ActionButton("Title", "text", enabled = Editor.clips.isNotEmpty()) { Editor.addTitle() }
		}
	}
}

@Composable
internal fun ViewTools() {
	AnimatedVisibility(Editor.hasMenuTime, enter = fadeIn(tween(FADE_MILLIS)), exit = fadeOut(tween(FADE_MILLIS))) {
		Tooltip({ Label("Remove every moment a menu was open (shown in blue)") }, Modifier, Alignment.TopCenter) {
			ActionButton("Cut out menus", "eye-off") { Editor.removeMenus() }
		}
	}
	BarGroup {
		PanelIcon("undo", "Undo (Ctrl+Z)", Editor.canUndo, above = true) { Editor.undo() }
		PanelIcon("redo", "Redo (Ctrl+Shift+Z)", Editor.canRedo, above = true) { Editor.redo() }
	}
	BarGroup {
		PanelIcon("minus", "Zoom out (-)", Editor.zoom > 1f, above = true) { Editor.zoomBy(0.5f) }
		PanelIcon("plus", "Zoom in (=)", Editor.zoom < MAX_ZOOM, above = true) { Editor.zoomBy(2f) }
	}
}

@Composable
internal fun TimelineTrack(modifier: Modifier) {
	Crossfade(Editor.clips.isNotEmpty(), modifier, tween(FADE_MILLIS)) {
		if (it) Timeline(Modifier.fillMaxSize())
	}
}
