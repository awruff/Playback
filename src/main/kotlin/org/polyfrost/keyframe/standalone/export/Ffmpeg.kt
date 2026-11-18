package org.polyfrost.keyframe.standalone.export

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.DigestInputStream
import java.security.MessageDigest
import java.time.Duration
import java.util.HexFormat
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile
import kotlin.math.roundToInt

enum class Container(val extension: String, val muxer: String, val audioCodec: String) {
	MP4("mp4", "mp4", "aac"),
	MKV("mkv", "matroska", "aac"),
	MOV("mov", "mov", "aac"),
	WEBM("webm", "webm", "libopus"),
}

enum class ColorFormat(val pixelFormat: String) {
	YUV420("yuv420p"),
	YUV422("yuv422p"),
	YUV444("yuv444p"),
	YUV420_10("yuv420p10le"),
	YUV422_10("yuv422p10le"),
	YUV444_10("yuv444p10le"),
}

enum class Codec(vararg containers: Container) {
	H264(Container.MP4, Container.MKV, Container.MOV),
	HEVC(Container.MP4, Container.MKV, Container.MOV),
	AV1(Container.MP4, Container.MKV, Container.WEBM),
	VP9(Container.MP4, Container.MKV, Container.WEBM),
	PRORES(Container.MKV, Container.MOV),
	FFV1(Container.MKV),
	;

	val containers = containers.toSet()
}

private val ALL_FORMATS = ColorFormat.entries.toSet()
private val YUV420_ONLY = setOf(ColorFormat.YUV420)
private val YUV420_8_10 = setOf(ColorFormat.YUV420, ColorFormat.YUV420_10)

enum class VideoEncoder(val ffmpegName: String, val codec: Codec, val qualityArgs: String, val formats: Set<ColorFormat>, val vaapi: Boolean = false) {
	X264("libx264", Codec.H264, "-crf {q}", ALL_FORMATS),
	X265("libx265", Codec.HEVC, "-crf {q}", ALL_FORMATS),
	SVT_AV1("libsvtav1", Codec.AV1, "-crf {q}", YUV420_8_10),
	VP9("libvpx-vp9", Codec.VP9, "-crf {q} -b:v 0", ALL_FORMATS),
	NVENC_H264("h264_nvenc", Codec.H264, "-rc vbr -cq {q} -b:v 0", setOf(ColorFormat.YUV420, ColorFormat.YUV444)),
	NVENC_HEVC("hevc_nvenc", Codec.HEVC, "-rc vbr -cq {q} -b:v 0", YUV420_8_10 + ColorFormat.YUV444),
	NVENC_AV1("av1_nvenc", Codec.AV1, "-rc vbr -cq {q} -b:v 0", YUV420_8_10),
	AMF_H264("h264_amf", Codec.H264, "-rc cqp -qp_i {q} -qp_p {q} -qp_b {q}", YUV420_ONLY),
	AMF_HEVC("hevc_amf", Codec.HEVC, "-rc cqp -qp_i {q} -qp_p {q}", YUV420_8_10),
	QSV_H264("h264_qsv", Codec.H264, "-global_quality {q}", YUV420_ONLY),
	QSV_HEVC("hevc_qsv", Codec.HEVC, "-global_quality {q}", YUV420_8_10),
	VAAPI_H264("h264_vaapi", Codec.H264, "-rc_mode CQP -qp {q}", YUV420_ONLY, vaapi = true),
	VAAPI_HEVC("hevc_vaapi", Codec.HEVC, "-rc_mode CQP -qp {q}", YUV420_8_10, vaapi = true),
	VAAPI_AV1("av1_vaapi", Codec.AV1, "-rc_mode CQP -qp {qindex}", YUV420_8_10, vaapi = true),
	PRORES("prores_ks", Codec.PRORES, "", setOf(ColorFormat.YUV422_10, ColorFormat.YUV444_10)),
	FFV1("ffv1", Codec.FFV1, "", ALL_FORMATS),
}

object Ffmpeg {
	internal data class Pin(val url: String, val sha256: String, val entry: String)

	private const val BTBN = "https://github.com/BtbN/FFmpeg-Builds/releases/download/autobuild-2026-10-31-12-59"
	private const val RIEDL = "https://ffmpeg.martin-riedl.de/download"
	private const val MAX_DOWNLOAD_BYTES = 512L * 1024 * 1024
	private val HASH_DIRECTORY = Regex("[0-9a-f]{64}")

	internal val PINS = mapOf(
		"windows-x64" to Pin(
			"$BTBN/ffmpeg-n9.0.2-39-g7adfbe40dc-win64-gpl-9.0.zip",
			"693f3e224bc184b40524a62ca47526cfebaf2504414021b92fe75f677ad868b9",
			"ffmpeg-n9.0.2-39-g7adfbe40dc-win64-gpl-9.0/bin/ffmpeg.exe",
		),
		"windows-arm64" to Pin(
			"$BTBN/ffmpeg-n9.0.2-39-g7adfbe40dc-winarm64-gpl-9.0.zip",
			"8b3c394762f26716ccef03ac1b7ac76430759080e1255aa9bc01d74db590bcb5",
			"ffmpeg-n9.0.2-39-g7adfbe40dc-winarm64-gpl-9.0/bin/ffmpeg.exe",
		),
		"linux-x64" to Pin(
			"$RIEDL/linux/amd64/1789931100_9.0.2/ffmpeg.zip",
			"fa8ecf4abbd290d98f7d188b8649cc6b391ae209a98452be955a15aab1909d7f",
			"ffmpeg",
		),
		"linux-arm64" to Pin(
			"$RIEDL/linux/arm64/1789931697_9.0.2/ffmpeg.zip",
			"93a76ae90db5474eecdf951a729857c64f3de23567228d6a7d5e6e8e3cd1021b",
			"ffmpeg",
		),
		"macos-x64" to Pin(
			"$RIEDL/macos/amd64/1789931006_9.0.2/ffmpeg.zip",
			"7c6b4125b191cbf773832dc51f424cf2b6bb7da43007d1e066f95909e47cacd4",
			"ffmpeg",
		),
		"macos-arm64" to Pin(
			"$RIEDL/macos/arm64/1789931890_9.0.2/ffmpeg.zip",
			"c8ed4c4e6978a03c485edbfe4e0a5dc2380f8a30bba5150531b31b094492d924",
			"ffmpeg",
		),
	)

	private val HARDWARE = VideoEncoder.entries.filter { it.ffmpegName.substringAfter('_') in setOf("nvenc", "amf", "qsv", "vaapi") }

	private val systemWithHardware: Path? by lazy {
		onPath(System.getenv("PATH").orEmpty(), if (File.separatorChar == '\\') "ffmpeg.exe" else "ffmpeg")
			?.takeIf { ffmpeg -> runs(ffmpeg) && HARDWARE.any { succeeds(probeCommand(ffmpeg, it)) } }
	}

	fun resolve(ffmpegPath: String, installRoot: Path, progress: (Float) -> Unit = {}): Path {
		if (ffmpegPath.isNotBlank()) return system(ffmpegPath.trim())
		systemWithHardware?.let { return it }

		val pin = pinFor(System.getProperty("os.name"), System.getProperty("os.arch"))
			?: throw IOException("No ffmpeg build is available for this platform. Set ffmpegPath to your own ffmpeg install.")
		try {
			return install(pin, installRoot) { download(pin.url, it, progress) }
		} catch (e: IOException) {
			throw IOException("Could not install ffmpeg (${e.message ?: e.javaClass.simpleName}). Set ffmpegPath to your own ffmpeg install.", e)
		}
	}

	private val supported = ConcurrentHashMap<Path, List<VideoEncoder>>()

	fun supportedEncoders(ffmpeg: Path): List<VideoEncoder> =
		supported.computeIfAbsent(ffmpeg) { VideoEncoder.entries.filter { succeeds(probeCommand(ffmpeg, it)) } }

	internal fun probeCommand(ffmpeg: Path, encoder: VideoEncoder): List<String> = buildList {
		addAll(listOf(ffmpeg.toString(), "-nostdin", "-hide_banner", "-loglevel", "error"))
		if (encoder.vaapi) addAll(VAAPI_DEVICE)
		addAll(listOf("-f", "lavfi", "-i", "color=black:s=640x360:r=30", "-frames:v", "1"))
		val format = pixelFormat(encoder, encoder.formats.first())
		if (encoder.vaapi) addAll(listOf("-vf", "format=$format,hwupload")) else addAll(listOf("-pix_fmt", format))
		addAll(listOf("-c:v", encoder.ffmpegName, "-f", "null", "-"))
	}

	private val VAAPI_DEVICE = listOf("-init_hw_device", "vaapi=va", "-filter_hw_device", "va")

	private fun pixelFormat(encoder: VideoEncoder, format: ColorFormat): String = when {
		!encoder.vaapi -> format.pixelFormat
		format == ColorFormat.YUV420_10 -> "p010le"
		else -> "nv12"
	}

	fun videoCommand(ffmpeg: Path, settings: ExportSettings, output: Path, nv12: Boolean = false, totalFrames: Int = 0): List<String> = buildList {
		addAll(listOf(ffmpeg.toString(), "-hide_banner", "-loglevel", "warning", "-y"))
		if (settings.encoder.vaapi) addAll(VAAPI_DEVICE)
		addAll(listOf("-f", "rawvideo", "-s", "${settings.width}x${settings.height}", "-r", "${settings.fps}"))
		val fades = videoFades(settings, totalFrames)
		if (nv12) {
			addAll(listOf("-pix_fmt", "nv12", "-color_range", "tv", "-colorspace", "bt709", "-i", "pipe:0", "-an", "-vf", (fades + outputFormat(settings)).joinToString(",")))
		} else {
			val filters = listOf("vflip", "scale=in_range=pc:out_range=tv:out_color_matrix=bt709") + fades + outputFormat(settings)
			addAll(listOf("-pix_fmt", "rgb0", "-i", "pipe:0", "-an", "-vf", filters.joinToString(",")))
		}
		addAll(encodeArgs(settings))
		addAll(listOf("-f", "matroska", output.toString()))
	}

	private fun outputFormat(settings: ExportSettings): List<String> =
		listOfNotNull("format=${pixelFormat(settings.encoder, settings.colorFormat)}", "hwupload".takeIf { settings.encoder.vaapi })

	private fun encodeArgs(settings: ExportSettings): List<String> = buildList {
		addAll(listOf("-c:v", settings.encoder.ffmpegName, "-g", "${keyframeGap(settings.keyframeInterval, settings.fps)}"))
		addAll(splitArgs(settings.encoder.qualityArgs.replace("{q}", "${settings.quality}").replace("{qindex}", "${settings.quality * 4}")))
		addAll(listOf("-colorspace", "bt709", "-color_primaries", "bt709", "-color_trc", "bt709", "-color_range", "tv"))
		addAll(splitArgs(settings.extraArgs))
	}

	internal fun videoFades(settings: ExportSettings, totalFrames: Int): List<String> {
		val fadeIn = fadeFrames(settings.fadeIn, settings.fps, totalFrames)
		val fadeOut = fadeFrames(settings.fadeOut, settings.fps, totalFrames)
		return listOfNotNull(
			"fade=t=in:s=0:n=$fadeIn".takeIf { fadeIn > 0 },
			"fade=t=out:s=${totalFrames - fadeOut}:n=$fadeOut".takeIf { fadeOut > 0 },
		)
	}

	internal fun audioFades(settings: ExportSettings, totalFrames: Int): List<String> {
		val fps = settings.fps.toDouble()
		val fadeIn = fadeFrames(settings.fadeIn, settings.fps, totalFrames) / fps
		val fadeOut = fadeFrames(settings.fadeOut, settings.fps, totalFrames) / fps
		return listOfNotNull(
			"afade=t=in:st=0:d=$fadeIn".takeIf { fadeIn > 0 },
			"afade=t=out:st=${totalFrames / fps - fadeOut}:d=$fadeOut".takeIf { fadeOut > 0 },
		)
	}

	fun muxCommand(ffmpeg: Path, video: Path, pcm: Path?, settings: ExportSettings, output: Path, totalFrames: Int = 0): List<String> = buildList {
		addAll(listOf(ffmpeg.toString(), "-hide_banner", "-loglevel", "warning", "-y", "-i", video.toString()))
		if (pcm != null) {
			addAll(listOf("-f", "f32le", "-ar", "$AUDIO_SAMPLE_RATE", "-ac", "2", "-i", pcm.toString()))
			val fades = audioFades(settings, totalFrames)
			if (fades.isNotEmpty()) addAll(listOf("-af", fades.joinToString(",")))
		}
		addAll(listOf("-c:v", "copy"))
		if (pcm != null) addAll(listOf("-c:a", settings.container.audioCodec, "-b:a", "192k"))
		addAll(containerArgs(settings, output, null))
	}

	fun concatCommand(ffmpeg: Path, list: Path, settings: ExportSettings, output: Path, attachment: Path? = null): List<String> = buildList {
		addAll(listOf(ffmpeg.toString(), "-hide_banner", "-loglevel", "warning", "-y", "-f", "concat", "-safe", "0", "-i", list.toString(), "-c", "copy"))
		addAll(containerArgs(settings, output, attachment))
	}

	private fun containerArgs(settings: ExportSettings, output: Path, attachment: Path?): List<String> = buildList {
		val container = settings.container
		val isoBased = container == Container.MP4 || container == Container.MOV
		if (attachment != null) addAll(listOf("-attach", attachment.toString(), "-metadata:s:t", "mimetype=application/zip"))
		if (isoBased && settings.encoder.codec == Codec.HEVC) addAll(listOf("-tag:v", "hvc1"))
		if (isoBased) addAll(listOf("-movflags", "+faststart"))
		addAll(listOf("-f", container.muxer, output.toString()))
	}

	internal fun transitionGraph(durations: List<Double>, crossfades: List<Double>, settings: ExportSettings): String {
		val chains = mutableListOf<String>()
		durations.indices.forEach { i ->
			chains += "[$i:v]settb=AVTB,setpts=PTS-STARTPTS,fps=${settings.fps}[v$i]"
			if (settings.audio) chains += "[$i:a]aresample=$AUDIO_SAMPLE_RATE,asetpts=PTS-STARTPTS[a$i]"
		}
		var video = "v0"
		var sound = "a0"
		var length = durations[0]
		for (i in 1 until durations.size) {
			val fade = crossfades[i - 1]
			if (fade > 0) {
				chains += "[$video][v$i]xfade=transition=fade:duration=$fade:offset=${length - fade}[x$i]"
				if (settings.audio) chains += "[$sound][a$i]acrossfade=d=$fade[y$i]"
				length += durations[i] - fade
			} else {
				chains += "[$video][v$i]concat=n=2:v=1:a=0[x$i]"
				if (settings.audio) chains += "[$sound][a$i]concat=n=2:v=0:a=1[y$i]"
				length += durations[i]
			}
			video = "x$i"
			sound = "y$i"
		}
		chains += "[$video]${outputFormat(settings).joinToString(",")}[video]"
		if (settings.audio) chains += "[$sound]anull[audio]"
		return chains.joinToString(";")
	}

	fun transitionCommand(ffmpeg: Path, parts: List<Path>, graph: String, settings: ExportSettings, output: Path, attachment: Path? = null): List<String> = buildList {
		addAll(listOf(ffmpeg.toString(), "-hide_banner", "-loglevel", "warning", "-y"))
		if (settings.encoder.vaapi) addAll(VAAPI_DEVICE)
		for (part in parts) addAll(listOf("-i", part.toString()))
		addAll(listOf("-filter_complex", graph, "-map", "[video]"))
		if (settings.audio) addAll(listOf("-map", "[audio]", "-c:a", settings.container.audioCodec, "-b:a", "192k"))
		addAll(encodeArgs(settings))
		addAll(containerArgs(settings, output, attachment))
	}

	internal fun concatList(parts: List<Path>): String =
		parts.joinToString("\n") { "file '${it.toString().replace("'", "'\\''")}'" }

	internal fun onPath(path: String, name: String): Path? = path.split(File.pathSeparatorChar)
		.filter { it.isNotBlank() }
		.mapNotNull { runCatching { Path.of(it, name) }.getOrNull() }
		.firstOrNull { Files.isRegularFile(it) && Files.isExecutable(it) }

	internal fun keyframeGap(seconds: Float, fps: Int): Int = maxOf((seconds * fps).roundToInt(), 1)

	private fun splitArgs(args: String): List<String> = args.split(Regex("\\s+")).filter { it.isNotEmpty() }

	internal fun pinFor(osName: String, osArch: String): Pin? {
		val name = osName.lowercase(Locale.ROOT)
		val os = when {
			name.startsWith("windows") -> "windows"
			name.startsWith("linux") -> "linux"
			name.startsWith("mac") -> "macos"
			else -> return null
		}
		val arch = when (osArch.lowercase(Locale.ROOT)) {
			"amd64", "x86_64" -> "x64"
			"aarch64", "arm64" -> "arm64"
			else -> return null
		}
		return PINS["$os-$arch"]
	}

	internal fun install(pin: Pin, root: Path, fetch: (Path) -> Unit): Path {
		val target = root.resolve(pin.sha256)
		val binaryName = pin.entry.substringAfterLast('/')
		val binary = target.resolve(binaryName)

		if (!runs(binary)) {
			Files.createDirectories(root)
			val temp = Files.createTempDirectory(root, "install-")
			try {
				val archive = temp.resolve("ffmpeg.zip")
				fetch(archive)
				verify(archive, pin.sha256)

				val staged = Files.createDirectory(temp.resolve("staged"))
				val stagedBinary = staged.resolve(binaryName)
				extract(archive, pin.entry, stagedBinary)
				stagedBinary.toFile().setExecutable(true)
				if (!runs(stagedBinary)) throw IOException("the downloaded ffmpeg did not run")

				publish(staged, target, binary)
			} finally {
				temp.toFile().deleteRecursively()
			}
		}

		deleteOlderInstalls(root, pin.sha256)
		return binary
	}

	internal fun copyBounded(input: InputStream, file: Path, total: Long, limit: Long, progress: (Float) -> Unit) {
		Files.newOutputStream(file).use { output ->
			val buffer = ByteArray(1 shl 16)
			var written = 0L
			while (true) {
				val count = input.read(buffer)
				if (count < 0) break
				written += count
				if (written > limit) throw IOException("the download is larger than $limit bytes")
				output.write(buffer, 0, count)
				if (total > 0) progress(minOf(written.toFloat() / total, 1f))
			}
		}
	}

	private fun system(ffmpegPath: String): Path {
		val path = try {
			Path.of(ffmpegPath)
		} catch (e: InvalidPathException) {
			throw IOException("ffmpegPath \"$ffmpegPath\" is not a valid path. Fix it, or clear it to let Keyframe install ffmpeg.", e)
		}
		if (!runs(path)) throw IOException("ffmpegPath \"$ffmpegPath\" failed to run -version. Fix it, or clear it to let Keyframe install ffmpeg.")
		return path
	}

	private fun runs(ffmpeg: Path): Boolean = succeeds(listOf(ffmpeg.toString(), "-version"))

	private fun succeeds(command: List<String>): Boolean {
		val process = try {
			ProcessBuilder(command)
				.redirectErrorStream(true)
				.redirectOutput(ProcessBuilder.Redirect.DISCARD)
				.start()
		} catch (_: IOException) {
			return false
		}
		if (process.waitFor(30, TimeUnit.SECONDS)) return process.exitValue() == 0
		process.destroyForcibly()
		return false
	}

	private fun download(url: String, file: Path, progress: (Float) -> Unit) {
		HttpClient.newBuilder()
			.followRedirects(HttpClient.Redirect.NORMAL)
			.connectTimeout(Duration.ofSeconds(30))
			.build()
			.use { client ->
				val request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60)).build()
				val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
				response.body().use { body ->
					if (response.statusCode() != 200) throw IOException("the download returned HTTP ${response.statusCode()}")
					val total = response.headers().firstValueAsLong("content-length").orElse(-1)
					copyBounded(body, file, total, MAX_DOWNLOAD_BYTES, progress)
				}
			}
	}

	private fun verify(archive: Path, sha256: String) {
		val digest = MessageDigest.getInstance("SHA-256")
		DigestInputStream(Files.newInputStream(archive), digest).use { it.transferTo(OutputStream.nullOutputStream()) }
		if (!MessageDigest.isEqual(digest.digest(), HexFormat.of().parseHex(sha256))) {
			throw IOException("the downloaded archive failed its SHA-256 check")
		}
	}

	private fun extract(archive: Path, entry: String, destination: Path) {
		ZipFile(archive.toFile()).use { zip ->
			val zipEntry = zip.getEntry(entry) ?: throw IOException("the archive has no entry named $entry")
			zip.getInputStream(zipEntry).use { Files.copy(it, destination) }
		}
	}

	private fun publish(staged: Path, target: Path, binary: Path) {
		try {
			Files.move(staged, target, StandardCopyOption.ATOMIC_MOVE)
		} catch (_: IOException) {
			if (runs(binary)) return
			target.toFile().deleteRecursively()
			Files.move(staged, target, StandardCopyOption.ATOMIC_MOVE)
		}
	}

	private fun deleteOlderInstalls(root: Path, current: String) {
		root.toFile().listFiles()
			?.filter { it.name != current && HASH_DIRECTORY.matches(it.name) }
			?.forEach { it.deleteRecursively() }
	}
}
