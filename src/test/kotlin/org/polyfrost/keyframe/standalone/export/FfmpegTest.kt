package org.polyfrost.keyframe.standalone.export

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.HexFormat
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FfmpegTest {
	private val passing = "#!/bin/sh\nexit 0\n"
	private val failing = "#!/bin/sh\nexit 1\n"
	private val root = Files.createTempDirectory("keyframe-ffmpeg-test")

	@AfterTest
	fun cleanUp() {
		root.toFile().deleteRecursively()
	}

	private fun zipOf(vararg entries: Pair<String, String>): ByteArray {
		val bytes = ByteArrayOutputStream()
		ZipOutputStream(bytes).use { zip ->
			for ((name, content) in entries) {
				zip.putNextEntry(ZipEntry(name))
				zip.write(content.toByteArray())
				zip.closeEntry()
			}
		}
		return bytes.toByteArray()
	}

	private fun sha256(bytes: ByteArray) = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))

	private fun pinFor(zip: ByteArray, entry: String = "bin/ffmpeg") = Ffmpeg.Pin("unused", sha256(zip), entry)

	private fun writeInstall(hash: String, script: String): Path {
		val binary = root.resolve(hash).createDirectories().resolve("ffmpeg")
		binary.writeText(script)
		binary.toFile().setExecutable(true)
		return binary
	}

	@Test
	fun `pins cover every supported platform`() {
		assertEquals("ffmpeg", assertNotNull(Ffmpeg.pinFor("Linux", "amd64")).entry)
		assertEquals("ffmpeg", assertNotNull(Ffmpeg.pinFor("Linux", "aarch64")).entry)
		assertEquals("ffmpeg", assertNotNull(Ffmpeg.pinFor("Mac OS X", "x86_64")).entry)
		assertEquals("ffmpeg", assertNotNull(Ffmpeg.pinFor("Mac OS X", "aarch64")).entry)
		assertContains(assertNotNull(Ffmpeg.pinFor("Windows 11", "amd64")).entry, "win64-gpl-9.0/bin/ffmpeg.exe")
		assertContains(assertNotNull(Ffmpeg.pinFor("Windows 11", "aarch64")).entry, "winarm64-gpl-9.0/bin/ffmpeg.exe")
		assertNull(Ffmpeg.pinFor("FreeBSD", "amd64"))
		assertNull(Ffmpeg.pinFor("Linux", "riscv64"))
	}

	@Test
	fun `pins are well formed`() {
		for (pin in Ffmpeg.PINS.values) {
			assertTrue(pin.sha256.matches(Regex("[0-9a-f]{64}")), pin.url)
			assertTrue(pin.url.startsWith("https://"), pin.url)
			assertFalse(pin.url.contains("latest"), pin.url)
		}
	}

	@Test
	fun `hash mismatch is rejected before the archive is opened`() {
		val pin = Ffmpeg.Pin("unused", sha256("expected".toByteArray()), "ffmpeg")
		val failure = assertFailsWith<IOException> {
			Ffmpeg.install(pin, root) { it.writeBytes("not a zip".toByteArray()) }
		}
		assertContains(assertNotNull(failure.message), "SHA-256")
		assertEquals(emptyList(), root.listDirectoryEntries())
	}

	@Test
	fun `only the named entry is extracted`() {
		val zip = zipOf("../escaped" to "bad", "bin/ffprobe" to "bad", "bin/ffmpeg" to passing)
		val pin = pinFor(zip)
		val binary = Ffmpeg.install(pin, root) { it.writeBytes(zip) }

		assertEquals(root.resolve(pin.sha256).resolve("ffmpeg"), binary)
		assertEquals(passing, binary.readText())
		assertEquals(listOf("ffmpeg"), binary.parent.listDirectoryEntries().map { it.name })
		assertEquals(listOf(pin.sha256), root.listDirectoryEntries().map { it.name })
	}

	@Test
	fun `missing entry fails and leaves nothing behind`() {
		val zip = zipOf("other" to passing)
		assertFailsWith<IOException> { Ffmpeg.install(pinFor(zip), root) { it.writeBytes(zip) } }
		assertEquals(emptyList(), root.listDirectoryEntries())
	}

	@Test
	fun `binary that fails the smoke test is not installed`() {
		val zip = zipOf("bin/ffmpeg" to failing)
		assertFailsWith<IOException> { Ffmpeg.install(pinFor(zip), root) { it.writeBytes(zip) } }
		assertEquals(emptyList(), root.listDirectoryEntries())
	}

	@Test
	fun `working install is reused without fetching`() {
		val zip = zipOf("bin/ffmpeg" to passing)
		val pin = pinFor(zip)
		val binary = writeInstall(pin.sha256, passing)

		assertEquals(binary, Ffmpeg.install(pin, root) { error("should not fetch") })
	}

	@Test
	fun `broken install is replaced`() {
		val zip = zipOf("bin/ffmpeg" to passing)
		val pin = pinFor(zip)
		writeInstall(pin.sha256, failing)

		assertEquals(passing, Ffmpeg.install(pin, root) { it.writeBytes(zip) }.readText())
	}

	@Test
	fun `concurrent install winning counts as success`() {
		val zip = zipOf("bin/ffmpeg" to passing)
		val pin = pinFor(zip)
		lateinit var winner: Path
		val binary = Ffmpeg.install(pin, root) {
			winner = writeInstall(pin.sha256, passing)
			it.writeBytes(zip)
		}

		assertEquals(winner, binary)
		assertEquals(listOf(pin.sha256), root.listDirectoryEntries().map { it.name })
	}

	@Test
	fun `older hash directories are deleted and other files are kept`() {
		val old = root.resolve("a".repeat(64)).createDirectories()
		val otherInstall = root.resolve("install-123").createDirectories()
		val zip = zipOf("bin/ffmpeg" to passing)

		Ffmpeg.install(pinFor(zip), root) { it.writeBytes(zip) }

		assertFalse(old.exists())
		assertTrue(otherInstall.exists())
	}

	@Test
	fun `download size is bounded`() {
		val file = root.resolve("download")
		val progress = mutableListOf<Float>()

		Ffmpeg.copyBounded(ByteArrayInputStream(ByteArray(8)), file, 8, 8, progress::add)
		assertEquals(1f, progress.last())

		assertFailsWith<IOException> {
			Ffmpeg.copyBounded(ByteArrayInputStream(ByteArray(9)), file, -1, 8) {}
		}
	}

	@Test
	fun `system path that fails names ffmpegPath and never installs`() {
		val failure = assertFailsWith<IOException> {
			Ffmpeg.resolve(root.resolve("missing").toString(), root) { error("should not download") }
		}
		assertContains(assertNotNull(failure.message), "ffmpegPath")
		assertEquals(emptyList(), root.listDirectoryEntries())
	}

	@Test
	fun `system path that runs is returned`() {
		val binary = writeInstall("system", passing)
		assertEquals(binary, Ffmpeg.resolve(" $binary ", root) { error("should not download") })
	}

	@Test
	fun `path lookup finds the first executable ffmpeg`() {
		val notExecutable = root.resolve("a").createDirectories().resolve("ffmpeg")
		notExecutable.writeText(passing)
		val binary = writeInstall("b", passing)
		val path = listOf(root.resolve("missing"), notExecutable.parent, binary.parent).joinToString(File.pathSeparator)
		assertEquals(binary, Ffmpeg.onPath(":$path", "ffmpeg"))
		assertNull(Ffmpeg.onPath(root.resolve("missing").toString(), "ffmpeg"))
	}

	private fun settings(
		encoder: VideoEncoder = VideoEncoder.X264,
		colorFormat: ColorFormat = ColorFormat.YUV420,
		container: Container = Container.MP4,
		keyframeInterval: Float = 2f,
		extraArgs: String = "",
	) = ExportSettings(1920, 1080, 60, keyframeInterval, encoder, 18, colorFormat, container, extraArgs, false, true, true, "", Path.of("exports"))

	@Test
	fun `video command matches the plan`() {
		val command = Ffmpeg.videoCommand(Path.of("ffmpeg"), settings(extraArgs = " -preset  slow\t-tune film "), Path.of("out.mkv"))
		assertEquals(
			"ffmpeg -hide_banner -loglevel warning -y -f rawvideo -s 1920x1080 -r 60 -pix_fmt rgb0 -i pipe:0 -an " +
				"-vf vflip,scale=in_range=pc:out_range=tv:out_color_matrix=bt709,format=yuv420p " +
				"-c:v libx264 -g 120 -crf 18 " +
				"-colorspace bt709 -color_primaries bt709 -color_trc bt709 -color_range tv " +
				"-preset slow -tune film -f matroska out.mkv",
			command.joinToString(" "),
		)
	}

	@Test
	fun `blank extra args and lossless quality add nothing`() {
		val command = Ffmpeg.videoCommand(Path.of("ffmpeg"), settings(VideoEncoder.FFV1, extraArgs = "  "), Path.of("out.mkv"))
		assertFalse("" in command)
		assertFalse("-crf" in command)
	}

	@Test
	fun `encoder and color format reach the command`() {
		val command = Ffmpeg.videoCommand(Path.of("ffmpeg"), settings(VideoEncoder.VP9, ColorFormat.YUV444_10), Path.of("out.mkv")).joinToString(" ")
		assertContains(command, "format=yuv444p10le")
		assertContains(command, "-c:v libvpx-vp9 -g 120 -crf 18 -b:v 0 ")
	}

	@Test
	fun `vaapi uploads a semi-planar format to the device`() {
		val command = Ffmpeg.videoCommand(Path.of("ffmpeg"), settings(VideoEncoder.VAAPI_HEVC, ColorFormat.YUV420_10), Path.of("out.mkv")).joinToString(" ")
		assertContains(command, "-y -init_hw_device vaapi=va -filter_hw_device va -f rawvideo")
		assertContains(command, "format=p010le,hwupload -c:v hevc_vaapi -g 120 -rc_mode CQP -qp 18 ")
		val probe = Ffmpeg.probeCommand(Path.of("ffmpeg"), VideoEncoder.VAAPI_H264).joinToString(" ")
		assertContains(probe, "-vf format=nv12,hwupload -c:v h264_vaapi")
		val av1 = Ffmpeg.videoCommand(Path.of("ffmpeg"), settings(VideoEncoder.VAAPI_AV1), Path.of("out.mkv")).joinToString(" ")
		assertContains(av1, "-c:v av1_vaapi -g 120 -rc_mode CQP -qp 72 ")
	}

	@Test
	fun `nv12 input skips the flip and color conversion`() {
		val x264 = Ffmpeg.videoCommand(Path.of("ffmpeg"), settings(), Path.of("out.mkv"), nv12 = true).joinToString(" ")
		assertContains(x264, "-f rawvideo -s 1920x1080 -r 60 -pix_fmt nv12 -color_range tv -colorspace bt709 -i pipe:0 -an -vf format=yuv420p -c:v libx264 ")
		val vaapi = Ffmpeg.videoCommand(Path.of("ffmpeg"), settings(VideoEncoder.VAAPI_H264), Path.of("out.mkv"), nv12 = true).joinToString(" ")
		assertContains(vaapi, "-vf format=nv12,hwupload -c:v h264_vaapi")
	}

	@Test
	fun `fades land on exact frames in video and audio`() {
		val faded = settings().copy(fadeIn = 0.5f, fadeOut = 1f)
		val video = Ffmpeg.videoCommand(Path.of("ffmpeg"), faded, Path.of("out.mkv"), totalFrames = 300).joinToString(" ")
		assertContains(video, "out_color_matrix=bt709,fade=t=in:s=0:n=30,fade=t=out:s=240:n=60,format=yuv420p ")
		val audio = Ffmpeg.muxCommand(Path.of("ffmpeg"), Path.of("v.mkv"), Path.of("a.pcm"), faded, Path.of("o.mp4"), 300).joinToString(" ")
		assertContains(audio, "-af afade=t=in:st=0:d=0.5,afade=t=out:st=4.0:d=1.0 -c:v copy")
	}

	@Test
	fun `transition graph crossfades and cuts`() {
		val graph = Ffmpeg.transitionGraph(listOf(4.0, 3.0, 2.0), listOf(1.0, 0.0, 0.0), settings().copy(audio = true))
		assertContains(graph, "[v0][v1]xfade=transition=fade:duration=1.0:offset=3.0[x1]")
		assertContains(graph, "[a0][a1]acrossfade=d=1.0[y1]")
		assertContains(graph, "[x1][v2]concat=n=2:v=1:a=0[x2]")
		assertContains(graph, "[x2]format=yuv420p[video]")
		assertContains(graph, "[y2]anull[audio]")
		val silent = Ffmpeg.transitionGraph(listOf(4.0, 3.0), listOf(0.5, 0.0), settings())
		assertFalse("[a0]" in silent)
	}

	@Test
	fun `keyframe gap is at least one frame`() {
		assertEquals(120, Ffmpeg.keyframeGap(2f, 60))
		assertEquals(3, Ffmpeg.keyframeGap(0.1f, 30))
		assertEquals(1, Ffmpeg.keyframeGap(0.01f, 20))
	}

	@Test
	fun `mux command with audio matches the plan`() {
		val command = Ffmpeg.muxCommand(Path.of("ffmpeg"), Path.of("v.mkv"), Path.of("a.pcm"), settings(), Path.of("o.mp4"))
		assertEquals(
			"ffmpeg -hide_banner -loglevel warning -y -i v.mkv -f f32le -ar 48000 -ac 2 -i a.pcm " +
				"-c:v copy -c:a aac -b:a 192k -movflags +faststart -f mp4 o.mp4",
			command.joinToString(" "),
		)
	}

	@Test
	fun `mux command without audio copies video only`() {
		val command = Ffmpeg.muxCommand(Path.of("ffmpeg"), Path.of("v.mkv"), null, settings(), Path.of("o.mp4"))
		assertEquals(
			"ffmpeg -hide_banner -loglevel warning -y -i v.mkv -c:v copy -movflags +faststart -f mp4 o.mp4",
			command.joinToString(" "),
		)
	}

	@Test
	fun `webm muxes opus without faststart`() {
		val command = Ffmpeg.muxCommand(Path.of("ffmpeg"), Path.of("v.mkv"), Path.of("a.pcm"), settings(VideoEncoder.VP9, container = Container.WEBM), Path.of("o.webm"))
		assertEquals(
			"ffmpeg -hide_banner -loglevel warning -y -i v.mkv -f f32le -ar 48000 -ac 2 -i a.pcm " +
				"-c:v copy -c:a libopus -b:a 192k -f webm o.webm",
			command.joinToString(" "),
		)
	}

	@Test
	fun `hevc is tagged hvc1 only in iso containers`() {
		val mp4 = Ffmpeg.muxCommand(Path.of("ffmpeg"), Path.of("v.mkv"), null, settings(VideoEncoder.X265), Path.of("o.mp4"))
		val mkv = Ffmpeg.muxCommand(Path.of("ffmpeg"), Path.of("v.mkv"), null, settings(VideoEncoder.X265, container = Container.MKV), Path.of("o.mkv"))
		assertContains(mp4.joinToString(" "), "-tag:v hvc1")
		assertFalse("-tag:v" in mkv)
		assertEquals("matroska", mkv[mkv.size - 2])
	}

	@Test
	fun `unsupported export formats are rejected before rendering`() {
		assertNull(formatProblem(VideoEncoder.X264, Container.MP4, ColorFormat.YUV420))
		assertNull(formatProblem(VideoEncoder.PRORES, Container.MOV, ColorFormat.YUV422_10))
		assertEquals("libx264 cannot be saved as webm", formatProblem(VideoEncoder.X264, Container.WEBM, ColorFormat.YUV420))
		assertEquals("prores_ks cannot be saved as mp4", formatProblem(VideoEncoder.PRORES, Container.MP4, ColorFormat.YUV422_10))
		assertEquals("libsvtav1 does not support yuv444p", formatProblem(VideoEncoder.SVT_AV1, Container.MP4, ColorFormat.YUV444))
	}

	@Test
	fun `best encoder prefers hardware that fits the output`() {
		val amd = listOf(VideoEncoder.X264, VideoEncoder.X265, VideoEncoder.VAAPI_H264, VideoEncoder.VAAPI_HEVC, VideoEncoder.VAAPI_AV1)
		assertEquals(VideoEncoder.VAAPI_H264, bestEncoder(amd, Container.MP4, ColorFormat.YUV420))
		assertEquals(VideoEncoder.VAAPI_HEVC, bestEncoder(amd, Container.MP4, ColorFormat.YUV420_10))
		assertEquals(VideoEncoder.VAAPI_AV1, bestEncoder(amd, Container.WEBM, ColorFormat.YUV420))
		assertEquals(VideoEncoder.X264, bestEncoder(amd, Container.MP4, ColorFormat.YUV444))
		assertNull(bestEncoder(listOf(VideoEncoder.X264), Container.WEBM, ColorFormat.YUV420))
	}

	@Test
	fun probeUsesAFormatTheEncoderAccepts() {
		val ffmpeg = Path.of("ffmpeg")
		val nvenc = Ffmpeg.probeCommand(ffmpeg, VideoEncoder.NVENC_H264)
		assertEquals("h264_nvenc", nvenc[nvenc.indexOf("-c:v") + 1])
		assertEquals("yuv420p", nvenc[nvenc.indexOf("-pix_fmt") + 1])
		val prores = Ffmpeg.probeCommand(ffmpeg, VideoEncoder.PRORES)
		assertEquals("yuv422p10le", prores[prores.indexOf("-pix_fmt") + 1])
	}
}
