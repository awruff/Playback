package org.polyfrost.keyframe.standalone.recording

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import java.util.zip.CRC32
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream

object ReplayFile {
	const val CONFIG_FRAME = 0
	const val PLAY_FRAME = 1
	const val TABLES = 2
	const val SNAPSHOT = 3
	const val EVENT = 4
	const val CAMERA = 5
	const val MARKER = 6
	const val INPUT = 7
	const val SCREEN_TRANSITION = 8
	const val CLIP = 9
	const val HIGHLIGHT = 10
	const val SESSION_BREAK = 11
	const val SCREEN_STATE = 12
	const val CHANNEL = 13
	const val THUMBNAIL = 14
	const val LAST_TYPE = THUMBNAIL

	const val MARKER_STOP: Byte = 0
	const val MARKER_START: Byte = 1

	const val EXTENSION = "pbrm"
}

private val MAGIC = "PBRM".toByteArray(Charsets.US_ASCII)
private const val FORMAT_VERSION = 2
private const val ROLL_BYTES = 1 shl 20
private const val ROLL_TICKS = 100
private const val MAX_PAYLOAD_LEN = 32 shl 20
private const val MAX_RAW_LEN = 64 shl 20
private const val MAX_COMPRESSED_LEN = MAX_RAW_LEN + (MAX_RAW_LEN shr 8)
private const val CHUNK_HEADER_LEN = 16
private const val CLIP_PAYLOAD_LEN = 8

data class ReplayHeader(
	val protocol: Int,
	val gameVersion: String,
	val uuid: UUID,
	val name: String,
	val isLocalServer: Boolean,
)

class ReplayRecord(val type: Int, val tick: Int, val payload: ByteArray)

data class Clip(val start: Int, val end: Int)

data class Highlight(val tick: Int, val colour: Int, val description: String)

internal fun encodeClip(clip: Clip): ByteArray = ByteBuffer.allocate(CLIP_PAYLOAD_LEN).putInt(clip.start).putInt(clip.end).array()

internal fun decodeClip(payload: ByteArray): Clip = ByteBuffer.wrap(payload).run { Clip(int, int) }

internal fun encodeHighlight(colour: Int, description: String): ByteArray {
	val bytes = ByteArrayOutputStream()
	DataOutputStream(bytes).run {
		writeInt(colour)
		writeUTF(description)
	}
	return bytes.toByteArray()
}

internal fun decodeHighlight(tick: Int, payload: ByteArray): Highlight =
	DataInputStream(ByteArrayInputStream(payload)).run { Highlight(tick, readInt(), readUTF()) }

class ReplayWriter(path: Path, header: ReplayHeader) : Closeable {
	private val queue = LinkedBlockingQueue<ReplayRecord>()
	private val output = Files.newOutputStream(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
	private val raw = ByteArrayOutputStream()
	private val thread = Thread(::drain, "Keyframe Recorder")
	private var firstTick = 0
	private var lastTick = 0

	@Volatile
	var failure: IOException? = null
		private set

	init {
		try {
			output.write(encodeHeader(header))
		} catch (e: IOException) {
			output.close()
			throw e
		}
		thread.isDaemon = true
		thread.start()
	}

	fun write(type: Int, tick: Int, payload: ByteArray) {
		if (failure == null && payload.size <= MAX_PAYLOAD_LEN) {
			queue.add(ReplayRecord(type, tick, payload))
		}
	}

	override fun close() {
		queue.add(END)
		thread.join()
	}

	private fun drain() {
		try {
			output.use {
				while (true) {
					val record = queue.take()
					if (record === END) break
					append(record)
				}
				flushChunk()
			}
		} catch (e: IOException) {
			failure = e
		}
	}

	private fun append(record: ReplayRecord) {
		if (raw.size() == 0) {
			firstTick = record.tick
			lastTick = record.tick
		}
		val tick = maxOf(record.tick, lastTick)
		raw.write(record.type)
		raw.writeVarInt(tick - lastTick)
		raw.writeVarInt(record.payload.size)
		raw.write(record.payload)
		lastTick = tick
		if (raw.size() >= ROLL_BYTES || tick - firstTick >= ROLL_TICKS || record.type == ReplayFile.CLIP) flushChunk()
	}

	private fun flushChunk() {
		if (raw.size() == 0) return
		val compressed = ByteArrayOutputStream()
		DeflaterOutputStream(compressed).use(raw::writeTo)
		val crc = CRC32().apply { update(compressed.toByteArray()) }.value.toInt()
		val chunk = ByteBuffer.allocate(CHUNK_HEADER_LEN + compressed.size())
			.putInt(compressed.size())
			.putInt(crc)
			.putInt(firstTick)
			.putInt(raw.size())
			.put(compressed.toByteArray())
		output.write(chunk.array())
		raw.reset()
	}

	private companion object {
		val END = ReplayRecord(-1, 0, ByteArray(0))
	}
}

class ReplayReader(path: Path, protocol: Int) : Closeable {
	private val input = DataInputStream(Files.newInputStream(path).buffered())
	private var chunk: ByteBuffer = ByteBuffer.allocate(0)
	private var tick = 0
	private var ended = false

	val header: ReplayHeader = try {
		input.readHeader().also {
			if (it.protocol != protocol) {
				throw IOException("Recorded with protocol ${it.protocol} (${it.gameVersion}), this game uses $protocol")
			}
		}
	} catch (e: IOException) {
		input.close()
		throw e
	}

	fun next(): ReplayRecord? {
		if (ended) return null
		try {
			while (true) {
				while (!chunk.hasRemaining()) chunk = readChunk()
				val type = chunk.get().toInt() and 0xFF
				val delta = chunk.readVarInt()
				val length = chunk.readVarInt()
				if (delta < 0 || length !in 0..chunk.remaining()) throw IOException("Malformed record")
				tick += delta
				if (type > ReplayFile.LAST_TYPE) {
					chunk.position(chunk.position() + length)
					continue
				}
				val payload = ByteArray(length)
				chunk.get(payload)
				return ReplayRecord(type, tick, payload)
			}
		} catch (_: IOException) {
			ended = true
			return null
		}
	}

	fun lastClip(): Clip? {
		var start = -1
		var clip: Clip? = null
		var last = 0
		while (true) {
			val record = next() ?: break
			last = record.tick
			if (record.type == ReplayFile.CLIP) clip = decodeClip(record.payload)
			if (record.type != ReplayFile.MARKER) continue
			if (record.payload[0] == ReplayFile.MARKER_START) {
				start = record.tick
			} else if (start >= 0) {
				clip = Clip(start, record.tick)
				start = -1
			}
		}
		return if (start >= 0) Clip(start, last) else clip
	}

	private fun readChunk(): ByteBuffer {
		val compressedLen = input.readInt()
		val crc = input.readInt()
		tick = input.readInt()
		val rawLen = input.readInt()
		if (compressedLen !in 0..MAX_COMPRESSED_LEN || rawLen !in 0..MAX_RAW_LEN) throw IOException("Chunk too large")
		val compressed = ByteArray(compressedLen)
		input.readFully(compressed)
		if (CRC32().apply { update(compressed) }.value.toInt() != crc) throw IOException("Bad chunk CRC")
		val raw = InflaterInputStream(ByteArrayInputStream(compressed)).use { it.readNBytes(rawLen + 1) }
		if (raw.size != rawLen) throw IOException("Chunk length mismatch")
		return ByteBuffer.wrap(raw)
	}

	override fun close() = input.close()
}

private fun encodeHeader(header: ReplayHeader): ByteArray {
	val bytes = ByteArrayOutputStream()
	DataOutputStream(bytes).run {
		write(MAGIC)
		writeShort(FORMAT_VERSION)
		writeInt(header.protocol)
		writeUTF(header.gameVersion)
		writeLong(header.uuid.mostSignificantBits)
		writeLong(header.uuid.leastSignificantBits)
		writeUTF(header.name)
		writeBoolean(header.isLocalServer)
	}
	return bytes.toByteArray()
}

private fun DataInputStream.readHeader(): ReplayHeader {
	val magic = ByteArray(MAGIC.size)
	readFully(magic)
	if (!magic.contentEquals(MAGIC)) throw IOException("Not a Keyframe recording")
	val version = readUnsignedShort()
	if (version != FORMAT_VERSION) throw IOException("Unsupported recording format $version")
	return ReplayHeader(readInt(), readUTF(), UUID(readLong(), readLong()), readUTF(), readBoolean())
}

private fun ByteArrayOutputStream.writeVarInt(value: Int) {
	var rest = value
	while (rest and 0x7F.inv() != 0) {
		write(rest and 0x7F or 0x80)
		rest = rest ushr 7
	}
	write(rest)
}

private fun ByteBuffer.readVarInt(): Int {
	var value = 0
	for (shift in 0..28 step 7) {
		if (!hasRemaining()) break
		val byte = get().toInt()
		value = value or ((byte and 0x7F) shl shift)
		if (byte >= 0) return value
	}
	throw IOException("Malformed varint")
}

internal fun copyRecords(from: Path, to: Path, protocol: Int, keep: (ReplayRecord) -> Boolean) {
	val temp = to.resolveSibling("${to.fileName}.tmp")
	Files.deleteIfExists(temp)
	ReplayReader(from, protocol).use { reader ->
		val writer = ReplayWriter(temp, reader.header)
		generateSequence(reader::next).filter(keep).forEach { writer.write(it.type, it.tick, it.payload) }
		writer.close()
		writer.failure?.let { throw it }
	}
	Files.move(temp, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
}
