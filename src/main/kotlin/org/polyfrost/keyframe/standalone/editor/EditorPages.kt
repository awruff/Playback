@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.keyframe.standalone.editor

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.onClick
import org.polyfrost.oneconfig.api.ui.v1.components.rememberInteractionSource
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme

private val BAR_HEIGHT = 48.dp
private val TIMELINE_HEIGHT = 214.dp
private val TRACK_HEIGHT = 156.dp
private val INSPECTOR_WIDTH = 248.dp
private val MIN_RECORDINGS_WIDTH = 264.dp
private val MIN_VIDEO_WIDTH = 220.dp
private val TAB_LINE = 2.dp
private const val WIDE_ASPECT = 16f / 9f

@Composable
internal fun EditorPages() {
	val exporting = Editor.exportPage && Editor.clips.isNotEmpty()
	BoxWithConstraints(Modifier.fillMaxSize()) {
		val paneWidth = { bottom: Dp, min: Dp ->
			val stageHeight = maxHeight - BAR_HEIGHT - bottom - 2.dp
			val frameWidth = stageHeight * WIDE_ASPECT
			maxOf(min, maxWidth - frameWidth - 1.dp)
		}
		val recordingsWidth = paneWidth(TIMELINE_HEIGHT, MIN_RECORDINGS_WIDTH)
		val videoWidth = paneWidth(BAR_HEIGHT, MIN_VIDEO_WIDTH)
		Column(Modifier.fillMaxSize().releasesFocus()) {
			TopBar(exporting)
			HorizontalLine()
			Row(Modifier.fillMaxWidth().weight(1f)) {
				SlidePane(!exporting, recordingsWidth, Alignment.End) {
					Label("Recordings", Modifier.padding(bottom = PANEL_GAP), size = 15.sp, weight = FontWeight.SemiBold)
					RecordingList()
				}
				PreviewArea(Modifier.weight(1f).fillMaxHeight())
				SlidePane(!exporting && inspecting, INSPECTOR_WIDTH, Alignment.Start) { Inspector() }
				SlidePane(exporting, videoWidth, Alignment.Start) {
					VideoSettings(videoWidth - PANEL_PADDING * 2, OneConfigTheme.current.sidebarBackground)
					ExportFooter()
				}
			}
			HorizontalLine()
			TimelinePane(exporting)
		}
	}
}

@Composable
private fun TopBar(exporting: Boolean) {
	val hasClips = Editor.clips.isNotEmpty()
	Row(
		Modifier.fillMaxWidth()
			.height(BAR_HEIGHT)
			.background(OneConfigTheme.current.sidebarBackground)
			.padding(horizontal = PANEL_PADDING),
		verticalAlignment = Alignment.CenterVertically,
	) {
		PanelIcon("left-arrow", "Close the editor") { Editor.close() }
		PageTab("Edit", !exporting, true) { Editor.exportPage = false }
		PageTab("Export", exporting, hasClips) { Editor.exportPage = true }
	}
}

@Composable
private fun PageTab(title: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
	val theme = OneConfigTheme.current
	val line by animateColorAsState(if (selected) OneConfigTheme.accent else Color.Transparent, tween(FADE_MILLIS))
	Box(
		Modifier.fillMaxHeight().onClick(rememberInteractionSource(), enabled, onClick).padding(horizontal = 14.dp),
		contentAlignment = Alignment.Center,
	) {
		Label(
			title,
			color = when {
				!enabled -> theme.textColorSecondary
				selected -> theme.textColor
				else -> muted()
			},
			weight = FontWeight.Medium,
		)
		Box(Modifier.matchParentSize().wrapContentHeight(Alignment.Bottom).height(TAB_LINE).background(line))
	}
}

@Composable
private fun SlidePane(open: Boolean, width: Dp, edge: Alignment.Horizontal, content: @Composable ColumnScope.() -> Unit) {
	val shown by animateDpAsState(if (open) width else 0.dp, panelMotion(Dp.VisibilityThreshold))
	if (shown == 0.dp) return
	val theme = OneConfigTheme.current
	if (edge == Alignment.Start) VerticalLine()
	Box(Modifier.width(shown).fillMaxHeight().clipToBounds().background(theme.sidebarBackground)) {
		Column(
			Modifier.fillMaxHeight()
				.wrapContentWidth(edge, unbounded = true)
				.width(width)
				.padding(PANEL_PADDING),
			content = content,
		)
	}
	if (edge == Alignment.End) VerticalLine()
}

@Composable
private fun TimelinePane(exporting: Boolean) {
	val height by animateDpAsState(if (exporting) BAR_HEIGHT else TIMELINE_HEIGHT, panelMotion(Dp.VisibilityThreshold))
	val tools by animateFloatAsState(if (exporting) 0f else 1f, tween(FADE_MILLIS))
	Column(
		Modifier.fillMaxWidth()
			.height(height)
			.clipToBounds()
			.background(OneConfigTheme.current.sidebarBackground)
			.padding(horizontal = PANEL_PADDING),
	) {
		Row(
			Modifier.fillMaxWidth().height(BAR_HEIGHT),
			horizontalArrangement = Arrangement.spacedBy(16.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			Transport()
			if (tools > 0f) {
				Row(
					Modifier.weight(1f).graphicsLayer { alpha = tools },
					horizontalArrangement = Arrangement.spacedBy(16.dp),
					verticalAlignment = Alignment.CenterVertically,
				) {
					EditTools()
					Spacer(Modifier.weight(1f))
					ViewTools()
				}
			}
		}
		if (height > BAR_HEIGHT) {
			TimelineTrack(
				Modifier.fillMaxWidth()
					.wrapContentHeight(Alignment.Top, unbounded = true)
					.height(TRACK_HEIGHT),
			)
		}
	}
}

@Composable
private fun lineColor(): Color = OneConfigTheme.current.run { borderColor.compositeOver(sidebarBackground) }

@Composable
private fun HorizontalLine() {
	Box(Modifier.fillMaxWidth().height(1.dp).background(lineColor()))
}

@Composable
private fun VerticalLine() {
	Box(Modifier.width(1.dp).fillMaxHeight().background(lineColor()))
}
