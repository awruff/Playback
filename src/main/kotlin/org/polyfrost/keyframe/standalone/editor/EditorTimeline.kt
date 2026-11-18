@file:OptIn(ExperimentalOneConfigUiApi::class, ExperimentalComposeUiApi::class)

package org.polyfrost.keyframe.standalone.editor

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.minecraft.client.Minecraft
import org.polyfrost.compose.backend.asComposeImageBitmap
import org.polyfrost.compose.render.PolyBitmap
import org.polyfrost.keyframe.standalone.compat.*
import org.polyfrost.keyframe.standalone.recording.RecordingInfo
import org.polyfrost.keyframe.standalone.recording.Thumbnail
import org.polyfrost.keyframe.standalone.recording.activityPeak
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.oneconfig.api.ui.v1.theme.concentric
import kotlin.math.ceil

private val RULER_HEIGHT = 16.dp
private val TITLE_LANE = 20.dp
private val CLIP_GAP = 2.dp
private val GRIP_HEIGHT = 22.dp
private val MENU_FLOOR = 2.dp
private val HIGHLIGHT_WIDTH = 2.dp
private val HIGHLIGHT_HEIGHT = 8.dp
private val ACTIVITY_STEP = 3.dp
private val RULER_LABEL_GAP = 64.dp
private const val ACTIVITY_HEIGHT = 0.7f
private const val ACTIVITY_OVER_THUMBNAILS = 0.35f
private const val SELECT_MILLIS = 150
private const val WHEEL_ZOOM = 1.25f
private const val WHEEL_DISTANCE = 4f
private const val THUMBNAIL_CACHE = 256
private val EDGE_GRAB = 6.dp
private val LABEL_MIN_WIDTH = 64.dp
private const val DRAG_SLOP = 4f
private val TITLE_COLOR = Color(0xFF8E6BD8)
private val RULER_SECONDS = listOf(1, 2, 5, 10, 15, 30, 60, 120, 300, 600, 1800)

internal sealed interface Hit {
	data object Ruler : Hit
	data class Edge(val index: Int, val atEnd: Boolean) : Hit
	data class Grip(val index: Int) : Hit
	data class Body(val index: Int) : Hit
	data class Title(val index: Int) : Hit
	data class TitleEdge(val index: Int, val atEnd: Boolean) : Hit
}

private val Hit.clip: Int?
	get() = when (this) {
		is Hit.Body -> index
		is Hit.Grip -> index
		is Hit.Edge -> index
		else -> null
	}

private val Hit.title: Int?
	get() = when (this) {
		is Hit.Title -> index
		is Hit.TitleEdge -> index
		else -> null
	}

private fun cursor(hit: Hit): PointerIcon = when {
	Editor.blade && hit is Hit.Body -> PointerIcon.Crosshair
	hit is Hit.Edge || hit is Hit.TitleEdge -> PointerIcon.Crosshair
	hit is Hit.Grip || hit is Hit.Title -> PointerIcon.Hand
	else -> PointerIcon.Default
}

internal class Layout(
	val clips: List<TimelineClip>,
	val titles: List<TimelineTitle>,
	val pixelsPerTick: Float,
	val rulerHeight: Float,
	val titleHeight: Float,
	val edgeGrab: Float,
	val gripHeight: Float,
) {
	val starts: IntArray = starts(clips)
	val clipTop: Float get() = rulerHeight + titleHeight

	fun left(index: Int): Float = starts[index] * pixelsPerTick

	fun right(index: Int): Float = (starts[index] + clips[index].duration) * pixelsPerTick

	fun timeAt(x: Float): Int = (x / pixelsPerTick).toInt()

	fun hit(position: Offset): Hit {
		if (position.y < rulerHeight) return Hit.Ruler
		if (position.y < clipTop) {
			for (index in titles.indices.reversed()) {
				val left = titles[index].start * pixelsPerTick
				val right = titles[index].end * pixelsPerTick
				if (position.x !in left..right) continue
				return edgeHit(left, right, position.x)?.let { Hit.TitleEdge(index, it) } ?: Hit.Title(index)
			}
			return Hit.Ruler
		}
		for (index in clips.indices.reversed()) {
			val left = left(index)
			val right = right(index)
			if (position.x !in left..right) continue
			edgeHit(left, right, position.x)?.let { return Hit.Edge(index, it) }
			return if (position.y < clipTop + gripHeight) Hit.Grip(index) else Hit.Body(index)
		}
		return Hit.Ruler
	}

	private fun edgeHit(left: Float, right: Float, x: Float): Boolean? {
		val grab = minOf(edgeGrab, (right - left) / 3)
		return when {
			x - left < grab -> false
			right - x < grab -> true
			else -> null
		}
	}

	fun slotAt(x: Float): Int = dropSlot(clips.indices.map { (left(it) + right(it)) / 2 }, x)
}

private object ThumbnailImages {
	private val images = object : LinkedHashMap<Thumbnail, ImageBitmap?>(64, 0.75f, true) {
		override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Thumbnail, ImageBitmap?>): Boolean = size > THUMBNAIL_CACHE
	}

	operator fun get(thumbnail: Thumbnail): ImageBitmap? {
		if (thumbnail in images) return images[thumbnail]
		return PolyBitmap.decode(thumbnail.jpeg)?.asComposeImageBitmap().also { images[thumbnail] = it }
	}
}

internal fun thumbnailAt(thumbnails: List<Thumbnail>, tick: Int): Thumbnail? {
	if (thumbnails.isEmpty()) return null
	val found = thumbnails.binarySearchBy(tick) { it.tick }
	return thumbnails[if (found >= 0) found else maxOf(-found - 2, 0)]
}

@Composable
internal fun Timeline(modifier: Modifier) {
	val clipShape = OneConfigTheme.current.backgroundShape.concentric(PANEL_PADDING)
	BoxWithConstraints(modifier.clip(clipShape)) {
		val density = LocalDensity.current
		val total = maxOf(Editor.total, 1)
		val viewportPx = with(density) { maxWidth.toPx() }
		val zoom by animateFloatAsState(Editor.zoom, panelMotion())
		val pixelsPerTick = viewportPx / total * zoom
		val layout = with(density) {
			Layout(Editor.clips, Editor.titles, pixelsPerTick, RULER_HEIGHT.toPx(), TITLE_LANE.toPx(), EDGE_GRAB.toPx(), GRIP_HEIGHT.toPx())
		}
		val current by rememberUpdatedState(layout)
		var dragging by remember { mutableStateOf<Int?>(null) }
		var slot by remember { mutableIntStateOf(0) }
		var hover by remember { mutableStateOf<Hit>(Hit.Ruler) }
		var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
		val theme = OneConfigTheme.current
		val accent = OneConfigTheme.accent
		val secondary = theme.textColorSecondary
		val clipColor = theme.chipBackground
		val contentWidth = with(density) { (viewportPx * zoom).toDp() }
		val selection = remember { Animatable(1f) }

		LaunchedEffect(Editor.selected, Editor.selectedTitle) {
			selection.snapTo(0f)
			selection.animateTo(1f, tween(SELECT_MILLIS))
		}

		var zoomAnchor by remember { mutableStateOf<Offset?>(null) }

		LaunchedEffect(Editor.playhead, zoom) {
			zoomAnchor?.let {
				Editor.scroll.scrollTo((it.x * pixelsPerTick - it.y).toInt())
				if (zoom == Editor.zoom) zoomAnchor = null
				return@LaunchedEffect
			}
			val x = (Editor.playhead * pixelsPerTick).toInt()
			if (x !in Editor.scroll.value..<Editor.scroll.value + viewportPx.toInt()) Editor.scroll.scrollTo(x - viewportPx.toInt() / 2)
		}

		Box(
			Modifier.fillMaxSize()
				.pointerInput(Unit) {
					awaitPointerEventScope {
						while (true) {
							val event = awaitPointerEvent(PointerEventPass.Initial)
							if (event.type != PointerEventType.Scroll) continue
							val change = event.changes.first()
							val notch = change.scrollDelta.x + change.scrollDelta.y
							val mc = Minecraft.getInstance()
							if (mc.hasControlDown() || mc.hasAltDown()) {
								val before = Editor.zoom
								Editor.zoomBy(if (notch < 0) WHEEL_ZOOM else 1 / WHEEL_ZOOM)
								if (Editor.zoom != before) zoomAnchor = Offset((Editor.scroll.value + change.position.x) / current.pixelsPerTick, change.position.x)
							} else {
								Editor.scroll.dispatchRawDelta(notch * WHEEL_DISTANCE * density.density)
							}
							change.consume()
						}
					}
				}
				.horizontalScroll(Editor.scroll),
		) {
			Box(Modifier.width(contentWidth).fillMaxHeight()) {
				Canvas(
					Modifier.fillMaxSize()
						.onGloballyPositioned { coordinates = it }
						.pointerHoverIcon(cursor(hover))
						.pointerInput(Unit) {
							awaitEachGesture {
								val down = awaitFirstDown()
								val start = down.position
								val hit = current.hit(start)
								if (currentEvent.button == PointerButton.Secondary) {
									val root = coordinates?.localToRoot(start) ?: start
									hit.clip?.let { Editor.selected = it }
									hit.title?.let { Editor.selectedTitle = it }
									Editor.menu = ContextMenu(root.x, root.y, hit.clip, hit.title, current.timeAt(start.x))
									return@awaitEachGesture
								}
								if (specialPress(hit, start.x, current)) return@awaitEachGesture
								when (hit) {
									Hit.Ruler -> {
										if (start.y >= current.clipTop) {
											Editor.selected = null
											Editor.selectedTitle = null
										}
										scrub(start.x) { current }
									}
									is Hit.Edge -> trim(hit, start.x) { current }
									is Hit.Body -> {
										Editor.selected = hit.index
										scrub(start.x) { current }
									}
									is Hit.Grip -> {
										val target = reorder(start, { current }) {
											dragging = hit.index
											slot = it
										}
										dragging = null
										if (target != null) {
											Editor.moveClip(hit.index, target)
										} else {
											Editor.selected = hit.index
											Editor.seek(current.timeAt(start.x))
										}
									}
									is Hit.Title -> dragTitle(hit.index, null, start) { current }
									is Hit.TitleEdge -> dragTitle(hit.index, hit.atEnd, start) { current }
								}
							}
						}
						.pointerInput(Unit) {
							awaitPointerEventScope {
								while (true) {
									val event = awaitPointerEvent()
									val change = event.changes.firstOrNull() ?: continue
									if (change.type == PointerType.Mouse) hover = current.hit(change.position)
								}
							}
						},
				) {
					val visible = Editor.scroll.value.toFloat()..Editor.scroll.value + viewportPx
					drawRuler(layout, secondary, density.density)
					layout.titles.forEachIndexed { index, title ->
						val highlight = if (Editor.selectedTitle == index) selection.value else 0f
						drawTitle(layout, title, clipShape, highlight, accent)
					}
					layout.clips.forEachIndexed { index, clip ->
						val highlight = if (Editor.selected == index) selection.value else 0f
						drawClip(layout, index, clip, clipShape, lerp(clipColor, accent.copy(alpha = 0.45f), highlight), highlight, accent, secondary, dragging == index, visible)
					}
					drawCrossfades(layout, accent)
					dragging?.let {
						val x = if (slot < layout.clips.size) layout.left(slot) else layout.right(layout.clips.lastIndex)
						drawRect(accent, Offset(x - 1.dp.toPx(), layout.clipTop), Size(2.dp.toPx(), size.height - layout.clipTop))
					}
				}
				RulerLabels(layout, viewportPx)
				layout.titles.forEachIndexed { index, title ->
					val left = title.start * pixelsPerTick
					val width = with(density) { ((title.end - title.start) * pixelsPerTick).toDp() }
					if (width >= 24.dp) {
						Label(
							title.text.ifBlank { "Title" },
							Modifier.offset { IntOffset(left.toInt(), layout.rulerHeight.toInt()) }
								.padding(start = 6.dp, top = 3.dp)
								.width(width - 10.dp),
							Color.White,
							10.sp,
							FontWeight.Medium,
						)
					}
				}
				layout.clips.forEachIndexed { index, clip ->
					val width = with(density) { (clip.duration * pixelsPerTick).toDp() }
					if (width >= LABEL_MIN_WIDTH) {
						val speed = if (clip.speed != 1f) "  ${formatSpeed(clip.speed)}" else ""
						Label(
							"Clip ${index + 1}  ${formatTime(clip.duration)}$speed",
							Modifier.offset { IntOffset(layout.left(index).toInt(), layout.clipTop.toInt()) }
								.padding(start = 8.dp, top = 5.dp)
								.width(width - 12.dp),
							theme.textColor,
							11.sp,
							FontWeight.Medium,
						)
					}
				}
				Box(
					Modifier.offset { IntOffset((Editor.playhead * pixelsPerTick).toInt(), 0) }
						.width(2.dp)
						.fillMaxHeight()
						.background(accent),
				)
			}
		}
	}
}

private fun rulerStep(layout: Layout, density: Float): Int {
	val minSeconds = RULER_LABEL_GAP.value * density / (layout.pixelsPerTick * TICKS_PER_SECOND)
	return RULER_SECONDS.firstOrNull { it >= minSeconds } ?: RULER_SECONDS.last()
}

@Composable
private fun RulerLabels(layout: Layout, viewportPx: Float) {
	val density = LocalDensity.current.density
	val step = rulerStep(layout, density)
	val stepPx = step * TICKS_PER_SECOND * layout.pixelsPerTick
	val first = (Editor.scroll.value / stepPx).toInt()
	val last = ((Editor.scroll.value + viewportPx) / stepPx).toInt()
	val color = muted()
	for (mark in first..last) {
		Label(
			formatTime(mark * step * TICKS_PER_SECOND),
			Modifier.offset { IntOffset((mark * stepPx).toInt(), 0) }.padding(start = 3.dp, top = 1.dp),
			color,
			9.sp,
		)
	}
}

private suspend fun AwaitPointerEventScope.specialPress(hit: Hit, x: Float, layout: Layout): Boolean {
	when {
		currentEvent.button == PointerButton.Tertiary -> pan()
		Editor.blade && hit is Hit.Body -> Editor.cutAt(layout.timeAt(x))
		else -> return false
	}
	return true
}

private suspend fun AwaitPointerEventScope.pan() {
	while (true) {
		val change = awaitPointerEvent().changes.first()
		if (change.changedToUp()) return
		Editor.scroll.dispatchRawDelta(-change.positionChange().x)
		change.consume()
	}
}

private suspend fun AwaitPointerEventScope.scrub(x: Float, layout: () -> Layout) {
	var last = x
	Editor.scrub(layout().timeAt(last))
	while (true) {
		val change = awaitPointerEvent().changes.first()
		last = change.position.x
		if (change.changedToUp()) break
		Editor.scrub(layout().timeAt(last))
	}
	Editor.seek(layout().timeAt(last))
}

private suspend fun AwaitPointerEventScope.trim(hit: Hit.Edge, x: Float, layout: () -> Layout) {
	Editor.selected = hit.index
	val before = layout().clips
	val clip = before[hit.index]
	val info = Editor.info(clip)
	val pixelsPerTick = layout().pixelsPerTick
	while (true) {
		val change = awaitPointerEvent().changes.first()
		if (change.changedToUp()) break
		val delta = ((change.position.x - x) / pixelsPerTick * clip.speed).toInt()
		Editor.preview(
			if (hit.atEnd) {
				trimEnd(before, hit.index, clip.end + delta, info?.lastTick ?: clip.end)
			} else {
				trimStart(before, hit.index, clip.start + delta, info?.firstTick ?: clip.start)
			},
		)
	}
	Editor.finishTrim(before, hit.index, hit.atEnd)
}

private suspend fun AwaitPointerEventScope.dragTitle(index: Int, atEnd: Boolean?, start: Offset, layout: () -> Layout) {
	Editor.selectedTitle = index
	val before = Editor.titles
	val title = before[index]
	val pixelsPerTick = layout().pixelsPerTick
	val total = maxOf(Editor.total, title.end)
	var moved = false
	while (true) {
		val change = awaitPointerEvent().changes.first()
		if (change.changedToUp()) break
		if (!moved && (change.position - start).getDistance() < DRAG_SLOP) continue
		moved = true
		val delta = ((change.position.x - start.x) / pixelsPerTick).toInt()
		val next = when (atEnd) {
			null -> {
				val length = title.end - title.start
				val from = Math.clamp((title.start + delta).toLong(), 0, maxOf(total - length, 0))
				title.copy(start = from, end = from + length)
			}
			false -> title.copy(start = Math.clamp((title.start + delta).toLong(), 0, title.end - 1))
			true -> title.copy(end = Math.clamp((title.end + delta).toLong(), title.start + 1, total))
		}
		Editor.previewTitles(before.toMutableList().apply { set(index, next) })
	}
	if (moved) Editor.finishTitleEdit(before) else Editor.seek(layout().timeAt(start.x))
}

private suspend fun AwaitPointerEventScope.reorder(start: Offset, layout: () -> Layout, onSlot: (Int) -> Unit): Int? {
	var target: Int? = null
	while (true) {
		val change = awaitPointerEvent().changes.first()
		if (change.changedToUp()) return target
		if (target == null && (change.position - start).getDistance() < DRAG_SLOP) continue
		target = layout().slotAt(change.position.x).also(onSlot)
	}
}

private fun DrawScope.drawRuler(layout: Layout, color: Color, density: Float) {
	val step = rulerStep(layout, density)
	val minor = maxOf(step / 5, 1)
	var second = 0
	while (second * TICKS_PER_SECOND * layout.pixelsPerTick < size.width) {
		val x = second * TICKS_PER_SECOND * layout.pixelsPerTick
		val major = second % step == 0
		val tall = if (major) 6 else 3
		drawRect(color.copy(alpha = if (major) 0.6f else 0.3f), Offset(x, layout.rulerHeight - tall * density), Size(density, tall * density))
		second += minor
	}
}

private fun DrawScope.drawTitle(layout: Layout, title: TimelineTitle, shape: Shape, highlight: Float, accent: Color) {
	val left = title.start * layout.pixelsPerTick
	val width = maxOf((title.end - title.start) * layout.pixelsPerTick - CLIP_GAP.toPx(), 1f)
	val height = layout.titleHeight - CLIP_GAP.toPx()
	val outline = shape.createOutline(Size(width, height), layoutDirection, this)
	translate(left, layout.rulerHeight) {
		drawOutline(outline, TITLE_COLOR.copy(alpha = 0.75f))
		if (highlight > 0f) drawOutline(outline, accent.copy(alpha = highlight), style = Stroke(1.dp.toPx()))
	}
}

private fun DrawScope.drawCrossfades(layout: Layout, accent: Color) {
	for (index in 0 until layout.clips.lastIndex) {
		val overlap = overlap(layout.clips, index)
		if (overlap <= 0) continue
		val from = layout.left(index + 1)
		val to = from + overlap * layout.pixelsPerTick
		val top = layout.clipTop
		val bottom = top + layout.gripHeight
		drawRect(accent.copy(alpha = 0.35f), Offset(from, top), Size(to - from, layout.gripHeight))
		drawLine(accent, Offset(from, bottom), Offset(to, top), 1.dp.toPx())
		drawLine(accent, Offset(from, top), Offset(to, bottom), 1.dp.toPx())
	}
}

private fun DrawScope.drawClip(
	layout: Layout,
	index: Int,
	clip: TimelineClip,
	shape: Shape,
	background: Color,
	highlight: Float,
	accent: Color,
	muted: Color,
	dimmed: Boolean,
	visible: ClosedFloatingPointRange<Float>,
) {
	val left = layout.left(index)
	val width = maxOf(layout.right(index) - left - CLIP_GAP.toPx(), 1f)
	val height = size.height - layout.clipTop
	val outline = shape.createOutline(Size(width, height), layoutDirection, this)
	val alpha = if (dimmed) 0.4f else 1f
	translate(left, layout.clipTop) {
		drawOutline(outline, background.copy(alpha = background.alpha * alpha))
		clipPath(Path().apply { addOutline(outline) }) {
			val info = Editor.info(clip)
			val thumbnails = info?.thumbnails.orEmpty()
			if (thumbnails.isNotEmpty()) {
				drawThumbnails(thumbnails, clip, layout, width, height, alpha, (visible.start - left)..(visible.endInclusive - left))
			}
			drawRect(muted.copy(alpha = 0.12f * alpha), size = Size(width, layout.gripHeight))
			if (info != null) {
				val range = if (thumbnails.isNotEmpty()) ACTIVITY_OVER_THUMBNAILS else ACTIVITY_HEIGHT
				drawActivity(info, clip, width, height, range, muted.copy(alpha = 0.55f * alpha), accent.copy(alpha = alpha), layout.pixelsPerTick)
			}
			drawFades(clip, width, height, layout)
		}
		if (highlight > 0f) drawOutline(outline, accent.copy(alpha = alpha * highlight), style = Stroke(1.dp.toPx()))
	}
}

private fun DrawScope.drawThumbnails(
	thumbnails: List<Thumbnail>,
	clip: TimelineClip,
	layout: Layout,
	width: Float,
	height: Float,
	alpha: Float,
	visible: ClosedFloatingPointRange<Float>,
) {
	val top = layout.gripHeight
	val tall = height - top
	val sample = ThumbnailImages[thumbnails.first()] ?: return
	val wide = maxOf(tall * sample.width / sample.height, 8f)
	var x = maxOf(0f, (visible.start / wide).toInt() * wide)
	while (x < minOf(width, visible.endInclusive)) {
		val tick = clip.sourceTick(((x + wide / 2) / layout.pixelsPerTick).toInt())
		val image = thumbnailAt(thumbnails, tick)?.let { ThumbnailImages[it] }
		if (image != null) {
			drawImage(
				image,
				IntOffset.Zero,
				IntSize(image.width, image.height),
				IntOffset(x.toInt(), top.toInt()),
				IntSize(ceil(wide).toInt(), ceil(tall).toInt()),
				alpha = 0.9f * alpha,
			)
		}
		x += wide
	}
}

private fun DrawScope.drawFades(clip: TimelineClip, width: Float, height: Float, layout: Layout) {
	val shade = Color.Black.copy(alpha = 0.35f)
	val fadeIn = minOf(clip.fadeIn, clip.duration) * layout.pixelsPerTick
	fun tri(x0: Float, x1: Float) = drawPath(Path().apply {
		moveTo(x0, 0f)
		lineTo(x1, 0f)
		lineTo(x0, height)
		close()
	}, shade)
	if (fadeIn > 0f) tri(0f, fadeIn)
	val fadeOut = minOf(clip.fadeOut, clip.duration) * layout.pixelsPerTick
	if (fadeOut > 0f) tri(width, width - fadeOut)
}

private fun DrawScope.drawActivity(
	info: RecordingInfo,
	clip: TimelineClip,
	width: Float,
	height: Float,
	range: Float,
	color: Color,
	menuColor: Color,
	pixelsPerTick: Float,
) {
	val step = ACTIVITY_STEP.toPx()
	val floor = MENU_FLOOR.toPx()
	val peak = height * range
	val graph = Path().apply {
		moveTo(0f, height)
		var x = 0f
		while (x < width) {
			val from = clip.sourceTick((x / pixelsPerTick).toInt())
			val to = clip.sourceTick(((x + step) / pixelsPerTick).toInt())
			lineTo(x, height - maxOf(peak * activityPeak(info.activity, from, to), floor))
			x += step
		}
		lineTo(width, height - floor)
		lineTo(width, height)
		close()
	}
	drawPath(graph, color)
	info.screens.forEach { screen ->
		val start = clip.timelineOffset(maxOf(screen.start, clip.start)) * pixelsPerTick
		val end = minOf(clip.timelineOffset(minOf(screen.end, clip.end)) * pixelsPerTick, width)
		if (end > start) clipRect(start, 0f, end, height) { drawPath(graph, menuColor) }
	}
	val tickSize = Size(HIGHLIGHT_WIDTH.toPx(), HIGHLIGHT_HEIGHT.toPx())
	info.highlights.forEach {
		if (it.tick in clip.start..<clip.end) {
			drawRect(Color(it.colour).copy(alpha = menuColor.alpha), Offset(clip.timelineOffset(it.tick) * pixelsPerTick, height - tickSize.height), tickSize)
		}
	}
}
