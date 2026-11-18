package org.polyfrost.keyframe.standalone.recording

import net.minecraft.client.Minecraft
import org.polyfrost.keyframe.standalone.StandaloneConfig
import org.polyfrost.keyframe.standalone.compat.*
import org.polyfrost.keyframe.standalone.editor.TICKS_PER_SECOND
import org.polyfrost.oneconfig.api.notifications.v1.NotificationType
import org.polyfrost.oneconfig.api.notifications.v1.Notifications
import org.polyfrost.oneconfig.utils.v1.NetworkUtils

internal fun mergeClip(pending: List<Clip>, clip: Clip): List<Clip> {
	val (overlapping, rest) = pending.partition { it.start <= clip.end && clip.start <= it.end }
	return rest + overlapping.fold(clip) { merged, other -> Clip(minOf(merged.start, other.start), maxOf(merged.end, other.end)) }
}

internal fun clipsAtClose(pending: List<Clip>, tick: Int): List<Clip> =
	pending.map { it.copy(end = minOf(it.end, tick)) }.filter { it.end > it.start }

object ShadowReplay {
	fun saveLast() {
		val player = Minecraft.getInstance().player
		if (!StandaloneConfig.shadowReplay || player == null) return
		val seconds = StandaloneConfig.clipLength
		val now = Recorder.currentTick
		if (Recorder.requestClip(maxOf(now - seconds * TICKS_PER_SECOND, 0), now)) {
			player.sendOverlayMessage("Saving the last $seconds seconds")
		}
	}

	internal fun announceSaved() {
		if (!StandaloneConfig.clipNotifications) return
		Minecraft.getInstance().execute {
			Notifications.builder("Keyframe", "Saved a clip")
				.type(NotificationType.SUCCESS)
				.action("Open folder") { NetworkUtils.browseLink(Recorder.clipsDirectory.toUri().toString()) }
				.send()
		}
	}
}
