package org.polyfrost.keyframe.standalone.export

import org.polyfrost.keyframe.standalone.editor.TICKS_PER_SECOND
import org.polyfrost.keyframe.standalone.editor.TitlePosition
import org.polyfrost.keyframe.standalone.editor.TitleSize
import java.nio.file.Path

internal const val AUDIO_SAMPLE_RATE = 48_000L

data class TitleSpan(val text: String, val from: Float, val to: Float, val position: TitlePosition, val size: TitleSize)

data class ExportSettings(
	val width: Int,
	val height: Int,
	val fps: Int,
	val keyframeInterval: Float,
	val encoder: VideoEncoder,
	val quality: Int,
	val colorFormat: ColorFormat,
	val container: Container,
	val extraArgs: String,
	val audio: Boolean,
	val hud: Boolean,
	val preview: Boolean,
	val ffmpegPath: String,
	val outputDir: Path,
	val autoEncoder: Boolean = false,
	val speed: Float = 1f,
	val fadeIn: Float = 0f,
	val fadeOut: Float = 0f,
	val titles: List<TitleSpan> = emptyList(),
)

internal fun exportFrames(ticks: Int, fps: Int, speed: Float): Int = (ticks.toLong() * fps / (TICKS_PER_SECOND * speed.toDouble())).toInt()

internal fun fadeFrames(seconds: Float, fps: Int, totalFrames: Int): Int = Math.clamp(Math.round(seconds * fps).toLong(), 0, totalFrames)

internal fun formatProblem(encoder: VideoEncoder, container: Container, colorFormat: ColorFormat): String? = when {
	container !in encoder.codec.containers -> "${encoder.ffmpegName} cannot be saved as ${container.extension}"
	colorFormat !in encoder.formats -> "${encoder.ffmpegName} does not support ${colorFormat.pixelFormat}"
	else -> null
}

private val FASTEST_FIRST = listOf(
	VideoEncoder.NVENC_H264, VideoEncoder.AMF_H264, VideoEncoder.QSV_H264, VideoEncoder.VAAPI_H264,
	VideoEncoder.NVENC_HEVC, VideoEncoder.AMF_HEVC, VideoEncoder.QSV_HEVC, VideoEncoder.VAAPI_HEVC,
	VideoEncoder.NVENC_AV1, VideoEncoder.VAAPI_AV1,
	VideoEncoder.X264, VideoEncoder.SVT_AV1, VideoEncoder.X265, VideoEncoder.VP9, VideoEncoder.PRORES, VideoEncoder.FFV1,
)

internal fun ExportSettings.withBestEncoder(ffmpeg: Path): ExportSettings =
	if (autoEncoder) copy(encoder = bestEncoder(Ffmpeg.supportedEncoders(ffmpeg), container, colorFormat) ?: encoder) else this

internal fun bestEncoder(supported: List<VideoEncoder>, container: Container, colorFormat: ColorFormat): VideoEncoder? =
	FASTEST_FIRST.firstOrNull { it in supported && formatProblem(it, container, colorFormat) == null }
