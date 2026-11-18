@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.keyframe.standalone.editor

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.polyfrost.keyframe.standalone.recording.Recorder
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.Chip
import org.polyfrost.oneconfig.api.ui.v1.components.Icon
import org.polyfrost.oneconfig.api.ui.v1.components.fadingEdges
import org.polyfrost.oneconfig.api.ui.v1.components.onClick
import org.polyfrost.oneconfig.api.ui.v1.components.rememberInteractionSource
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.oneconfig.utils.v1.NetworkUtils

private val FADES = listOf(0 to "Off", 10 to "0.5s", 20 to "1s", 40 to "2s")
private val POSITIONS = listOf(TitlePosition.TOP to "Top", TitlePosition.MIDDLE to "Middle", TitlePosition.BOTTOM to "Bottom")
private const val MAX_TITLE_LENGTH = 80
private val MENU_WIDTH = 190.dp
private val MENU_ITEM_HEIGHT = 28.dp

internal val inspecting: Boolean get() = Editor.selected != null || Editor.selectedTitle != null

@Composable
internal fun ColumnScope.Inspector() {
	val clipIndex = Editor.selected
	val titleIndex = Editor.selectedTitle
	val clip = clipIndex?.let(Editor.clips::getOrNull)
	val title = titleIndex?.let(Editor.titles::getOrNull)
	Row(Modifier.fillMaxWidth().padding(bottom = PANEL_GAP), verticalAlignment = Alignment.CenterVertically) {
		Label(
			when {
				clip != null -> "Clip ${clipIndex + 1}"
				title != null -> "Title"
				else -> ""
			},
			Modifier.weight(1f),
			size = 15.sp,
			weight = FontWeight.SemiBold,
		)
		PanelIcon("close", "Close") {
			Editor.selected = null
			Editor.selectedTitle = null
		}
	}
	val scroll = rememberScrollState()
	Column(Modifier.weight(1f).fadingEdges(scroll, OneConfigTheme.current.sidebarBackground).verticalScroll(scroll).padding(bottom = 12.dp)) {
		when {
			clip != null -> ClipInspector(clipIndex, clip)
			title != null -> key(titleIndex) { TitleInspector(titleIndex, title) }
		}
	}
}

@Composable
private fun ClipInspector(index: Int, clip: TimelineClip) {
	val info = Editor.info(clip)
	Label(info?.let(Editor::label) ?: clip.file, color = muted(), size = 12.sp)
	Label("${formatTimecode(clip.duration)} long", color = muted(), size = 11.sp)
	SmallHeading("Speed")
	ChipRow {
		SPEEDS.forEach { speed -> Chip(formatSpeed(speed), clip.speed == speed, { Editor.changeClip(index) { it.copy(speed = speed) } }) }
	}
	SmallHeading("Fade in")
	FadeChips(clip.fadeIn) { ticks -> Editor.changeClip(index) { it.copy(fadeIn = ticks) } }
	SmallHeading("Fade out")
	FadeChips(clip.fadeOut) { ticks -> Editor.changeClip(index) { it.copy(fadeOut = ticks) } }
	if (index < Editor.clips.lastIndex) {
		SmallHeading("Blend into the next clip")
		FadeChips(clip.crossfade) { ticks -> Editor.changeClip(index) { it.copy(crossfade = ticks) } }
	}
	Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(PANEL_GAP)) {
		ActionButton("Duplicate", "copy-plus") { Editor.duplicate(index) }
		ActionButton("Delete", "trash") { Editor.deleteSelected() }
	}
}

@Composable
private fun FadeChips(current: Int, onPick: (Int) -> Unit) {
	ChipRow {
		FADES.forEach { (ticks, label) -> Chip(label, current == ticks, { onPick(ticks) }) }
	}
}

@Composable
private fun TitleInspector(index: Int, title: TimelineTitle) {
	SmallHeading("Text")
	TextInput(title.text, Modifier.fillMaxWidth(), filter = { it.take(MAX_TITLE_LENGTH) }) { text ->
		Editor.changeTitle(index) { it.copy(text = text) }
		text
	}
	SmallHeading("Position")
	ChipRow {
		POSITIONS.forEach { (position, label) -> Chip(label, title.position == position, { Editor.changeTitle(index) { it.copy(position = position) } }) }
	}
	SmallHeading("Size")
	ChipRow {
		TitleSize.entries.forEach { size -> Chip(size.label, title.size == size, { Editor.changeTitle(index) { it.copy(size = size) } }) }
	}
	Label(
		"Shows from ${formatTimecode(title.start)} for ${formatTimecode(title.end - title.start)}. Drag it on the timeline to move it.",
		Modifier.padding(top = 12.dp),
		muted(),
		11.sp,
		lines = 3,
	)
	Row(Modifier.padding(top = 16.dp)) {
		ActionButton("Delete", "trash") { Editor.deleteSelected() }
	}
}

private class MenuEntry(val label: String, val icon: String?, val enabled: Boolean = true, val action: () -> Unit)

private fun entries(menu: ContextMenu): List<MenuEntry> = when {
	menu.clip != null -> {
		val clip = Editor.clips.getOrNull(menu.clip)
		listOfNotNull(
			MenuEntry("Split here", "scissors") { Editor.cutAt(menu.time) },
			MenuEntry("Duplicate", "copy-plus") { Editor.duplicate(menu.clip) },
			MenuEntry("Copy", "copy") { Editor.copySelected() },
			MenuEntry("Delete", "trash") { Editor.deleteSelected() },
			clip?.let { MenuEntry("Show in folder", "folder") { NetworkUtils.browseLink(Recorder.replayFile(it.file).parent.toUri().toString()) } },
		)
	}
	menu.title != null -> listOf(
		MenuEntry("Delete title", "trash") { Editor.deleteSelected() },
	)
	else -> listOf(
		MenuEntry("Add a title here", "text") { Editor.addTitle(menu.time) },
		MenuEntry("Paste", "clipboard", Editor.canPaste) { Editor.paste() },
		MenuEntry("Cut out menus", "eye-off", Editor.hasMenuTime) { Editor.removeMenus() },
	)
}

@Composable
internal fun ContextMenuLayer(menu: ContextMenu) {
	val theme = OneConfigTheme.current
	val items = entries(menu)
	BoxWithConstraints(Modifier.fillMaxSize().pointerInput(menu) { detectTapGestures { Editor.menu = null } }) {
		val density = LocalDensity.current
		val width = with(density) { MENU_WIDTH.toPx() }
		val height = with(density) { (MENU_ITEM_HEIGHT * items.size + 8.dp).toPx() }
		val x = minOf(menu.x, constraints.maxWidth - width).toInt()
		val y = minOf(menu.y, constraints.maxHeight - height).toInt()
		Column(
			Modifier.offset { IntOffset(x, y) }
				.width(MENU_WIDTH)
				.clip(theme.buttonShape)
				.background(theme.popupBackground)
				.border(1.dp, theme.borderColor, theme.buttonShape)
				.pointerInput(Unit) { detectTapGestures {} }
				.padding(4.dp),
		) {
			items.forEach { MenuItem(it) }
		}
	}
}

@Composable
private fun MenuItem(entry: MenuEntry) {
	val theme = OneConfigTheme.current
	val source = rememberInteractionSource()
	val hovered by source.collectIsHoveredAsState()
	val background by animateColorAsState(if (hovered && entry.enabled) theme.chipBackground else Color.Transparent)
	val color = if (entry.enabled) theme.textColor else theme.textColorSecondary
	Row(
		Modifier.fillMaxWidth()
			.height(MENU_ITEM_HEIGHT)
			.clip(theme.buttonShape)
			.background(background)
			.hoverable(source)
			.onClick(source, entry.enabled) {
				Editor.menu = null
				entry.action()
			}
			.padding(horizontal = 8.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(8.dp),
	) {
		Box(Modifier.size(14.dp)) { if (entry.icon != null) Icon(entry.icon, null, Modifier.size(14.dp), color) }
		Label(entry.label, color = color, size = 12.sp)
	}
}
