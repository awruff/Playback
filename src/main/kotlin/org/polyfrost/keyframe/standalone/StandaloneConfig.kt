package org.polyfrost.keyframe.standalone

import com.mojang.blaze3d.platform.InputConstants
import net.fabricmc.loader.api.FabricLoader
import org.polyfrost.keyframe.standalone.editor.MAX_FRAME_RATE
import org.polyfrost.keyframe.standalone.editor.MIN_FRAME_RATE
import org.polyfrost.keyframe.standalone.editor.clampFrameRate
import org.polyfrost.keyframe.standalone.export.ColorFormat
import org.polyfrost.keyframe.standalone.export.Container
import org.polyfrost.keyframe.standalone.export.ExportSettings
import org.polyfrost.keyframe.standalone.export.VideoEncoder
import org.polyfrost.keyframe.standalone.export.bestEncoder
import org.polyfrost.keyframe.standalone.recording.Recorder
import org.polyfrost.keyframe.standalone.recording.ShadowReplay
import org.polyfrost.oneconfig.api.config.v1.Config
import org.polyfrost.oneconfig.api.config.v1.annotations.Dropdown
import org.polyfrost.oneconfig.api.config.v1.annotations.Keybind
import org.polyfrost.oneconfig.api.config.v1.annotations.Number as NumberOption
import org.polyfrost.oneconfig.api.config.v1.annotations.Slider
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch
import org.polyfrost.oneconfig.api.config.v1.annotations.Text
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.OptionWidgets
import org.polyfrost.oneconfig.api.ui.v1.keybind.KeybindHelper
import org.polyfrost.oneconfig.api.ui.v1.keybind.OneConfigKeybind

object StandaloneConfig : Config("keyframe-standalone.json", "assets/keyframe/icon.png", "Keyframe", Category.UTILITY) {
	@NumberOption(title = "Width", unit = "px", min = 2f, max = 16384f, category = "Export")
	var width = 1920

	@NumberOption(title = "Height", unit = "px", min = 2f, max = 16384f, category = "Export")
	var height = 1080

	@NumberOption(title = "Frame rate", unit = "fps", min = MIN_FRAME_RATE.toFloat(), max = MAX_FRAME_RATE.toFloat(), category = "Export")
	var fps = 60

	@NumberOption(title = "Keyframe interval", description = "Longest gap between keyframes. Lower seeks faster, higher makes smaller files", unit = "s", min = 0.1f, max = 60f, category = "Export")
	var keyframeInterval = 2f

	@Switch(title = "Pick encoder automatically", description = "Uses the fastest encoder that works on this PC with the chosen file type and color format", category = "Export")
	var autoEncoder = true

	@Dropdown(
		title = "Encoder",
		description = "Encoders that do not work on this PC are hidden",
		options = ["H.264 (x264)", "HEVC (x265)", "AV1 (SVT-AV1)", "VP9", "H.264 (NVENC)", "HEVC (NVENC)", "AV1 (NVENC)", "H.264 (AMF)", "HEVC (AMF)", "H.264 (Quick Sync)", "HEVC (Quick Sync)", "H.264 (VAAPI)", "HEVC (VAAPI)", "AV1 (VAAPI)", "ProRes", "FFV1"],
		category = "Export",
	)
	var encoder = VideoEncoder.X264

	@NumberOption(title = "Quality", description = "Lower is better. The scale depends on the encoder, and ProRes and FFV1 ignore it", min = 0f, max = 63f, category = "Export")
	var quality = 18

	@Dropdown(
		title = "Color format",
		description = "4:2:0 8-bit plays almost everywhere",
		options = ["4:2:0 8-bit", "4:2:2 8-bit", "4:4:4 8-bit", "4:2:0 10-bit", "4:2:2 10-bit", "4:4:4 10-bit"],
		category = "Export",
	)
	var colorFormat = ColorFormat.YUV420

	@Dropdown(title = "File type", options = ["MP4", "MKV", "MOV", "WebM"], category = "Export")
	var container = Container.MP4

	@Text(title = "Output folder", description = "Empty saves to keyframe/exports", category = "Export")
	var outputFolder = ""

	@Switch(title = "Export game sound", description = "Uses your volume sliders. Music is picked during the export, so it can differ from the recording", category = "Export")
	var audio = true

	@Switch(title = "Show HUD and hand", category = "Export")
	var hud = true

	@Switch(title = "Show preview while exporting", description = "Off shows only the status text", category = "Export")
	var preview = true

	@Switch(title = "Embed the recording", description = "Stores the part of the recording each video was made from inside the video, so it can be opened again. Not supported for WebM", category = "Export")
	var embedRecording = true

	@Text(title = "Extra ffmpeg arguments", description = "Added after the options above, so they can override them", category = "Export")
	var extraArgs = ""

	@Text(title = "ffmpeg path", description = "Empty uses your system ffmpeg if it can encode on the GPU, and otherwise installs one", category = "Export")
	var ffmpegPath = ""

	@Switch(title = "Keep recording through the main menu", description = "Leaving a server doesn't finish the recording, so the main menu and the next server you join end up in the same replay", category = "Recording")
	var recordMainMenu = true

	@Keybind(title = "Toggle recording", description = "Starts or stops recording. Works on the title screen, where it finishes the recording", category = "Recording")
	var recordKey: OneConfigKeybind = KeybindHelper.builder().key(InputConstants.KEY_F9).name("Toggle recording").category("Keyframe").inScreens()
		.action(Recorder::toggle).register()

	@Switch(title = "Shadow Replay", description = "Lets you save the last few seconds at any time, even when you are not recording", category = "Shadow Replay")
	var shadowReplay = true

	@Keybind(title = "Save Shadow Replay", description = "Saves the last few seconds of gameplay as a clip", category = "Shadow Replay")
	var saveKey: OneConfigKeybind = KeybindHelper.builder().key(InputConstants.KEY_F8).name("Save Shadow Replay").category("Keyframe").inScreens()
		.action(ShadowReplay::saveLast).register()

	@Slider(title = "Clip length", description = "Seconds of gameplay in each clip", min = 5f, max = 120f, step = 5f, category = "Shadow Replay")
	var clipLength = 30

	@Switch(title = "Notify when a clip is saved", category = "Shadow Replay")
	var clipNotifications = true

	@Slider(title = "Seconds after highlight", description = "How much of each highlight clip comes after the event", min = 0f, max = 15f, step = 1f, category = "Shadow Replay", subcategory = "Highlights")
	var afterHighlight = 3

	@Switch(title = "Clip combos", category = "Shadow Replay", subcategory = "Highlights")
	var clipCombos = true

	@Switch(title = "Clip kills", category = "Shadow Replay", subcategory = "Highlights")
	var clipKills = true

	@Switch(title = "Clip bed breaks", category = "Shadow Replay", subcategory = "Highlights")
	var clipBeds = true

	val frameRate: Int get() = clampFrameRate(fps)

	@Transient
	private var supportedEncoders = emptyList<VideoEncoder>()

	@OptIn(ExperimentalOneConfigUiApi::class)
	fun limitEncoders(supported: List<VideoEncoder>) {
		supportedEncoders = supported
		val property = getTree().getProp("encoder")
		val labels = property.getMetadata<Array<String>>("options")!!
		property.addMetadata("optionValues", supported)
		property.addMetadata("optionLabels", supported.map { labels[it.ordinal] })
		if (encoder !in supported) encoder = supported.first()
		OptionWidgets.refresh(property)
	}

	fun exportSettings(): ExportSettings {
		val exports = FabricLoader.getInstance().gameDir.resolve(outputFolder.trim().ifEmpty { "keyframe/exports" })
		val encoder = if (autoEncoder) bestEncoder(supportedEncoders, container, colorFormat) ?: encoder else encoder
		return ExportSettings(width, height, frameRate, keyframeInterval, encoder, quality, colorFormat, container, extraArgs, audio, hud, preview, ffmpegPath, exports, autoEncoder)
	}
}
