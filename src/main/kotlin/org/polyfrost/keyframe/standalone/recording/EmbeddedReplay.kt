package org.polyfrost.keyframe.standalone.recording

import net.minecraft.SharedConstants
import org.polyfrost.keyframe.standalone.editor.TimelineClip
import java.io.DataOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal object EmbeddedReplay {
	val BOX_ID: UUID = UUID.fromString("7a1c0f4e-3b52-4d8e-9c61-5f0b2e8d4a73")
	const val ATTACHMENT_NAME = "keyframe-recordings.zip"

	fun pack(clips: List<TimelineClip>, work: Path): Path {
		val protocol = SharedConstants.getCurrentVersion().protocolVersion()
		val zip = work.resolve(ATTACHMENT_NAME)
		ZipOutputStream(Files.newOutputStream(zip)).use { out ->
			for ((file, end) in clips.groupBy { it.file }.mapValues { (_, uses) -> uses.maxOf { it.end } }) {
				val trimmed = work.resolve("$file.trimmed")
				copyRecords(Recorder.replayFile(file), trimmed, protocol) { it.tick <= end }
				out.putNextEntry(ZipEntry(file))
				Files.copy(trimmed, out)
				out.closeEntry()
				Files.delete(trimmed)
			}
		}
		return zip
	}

	fun appendBox(video: Path, zip: Path) {
		val size = Files.size(zip)
		DataOutputStream(Files.newOutputStream(video, StandardOpenOption.APPEND).buffered()).use { out ->
			val header = 8L + 16
			if (header + size <= 0xFFFF_FFFFL) {
				out.writeInt((header + size).toInt())
				out.writeBytes("uuid")
			} else {
				out.writeInt(1)
				out.writeBytes("uuid")
				out.writeLong(header + 8 + size)
			}
			out.writeLong(BOX_ID.mostSignificantBits)
			out.writeLong(BOX_ID.leastSignificantBits)
			Files.copy(zip, out)
		}
	}
}
