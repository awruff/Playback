package org.polyfrost.keyframe.standalone

import net.fabricmc.loader.api.FabricLoader
import net.minecraft.util.Util
import org.polyfrost.keyframe.standalone.playback.Keyframe
import org.polyfrost.keyframe.standalone.recording.ReplayRecord
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.function.BiConsumer
import java.util.function.BooleanSupplier
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.LongSupplier
import java.util.function.Supplier

object Channels {
	private val LOGGER = LoggerFactory.getLogger(Standalone.MOD_ID)

	private val samplers = ConcurrentHashMap<String, Supplier<String?>>()
	private val written = HashMap<String, String?>()
	private val replayed = ConcurrentHashMap<String, String>()

	fun install() {
		val share = FabricLoader.getInstance().objectShare
		share.put("keyframe:register_channel", BiConsumer<String, Supplier<String?>>(::register))
		share.put("keyframe:unregister_channel", Consumer<String>(::unregister))
		share.put("keyframe:channel", Function<String, String?> { id -> if (Keyframe.active) replayed[id] else null })
		share.put("keyframe:replaying", BooleanSupplier { Keyframe.active })
		share.put("keyframe:nanos", LongSupplier { Util.getNanos() })
	}

	internal fun register(id: String, sampler: Supplier<String?>) {
		samplers[id] = sampler
	}

	internal fun unregister(id: String) {
		samplers.remove(id)
	}

	internal fun sample(write: (ByteArray) -> Unit) {
		for ((id, sampler) in samplers) {
			try {
				val value = sampler.get()
				if (written.containsKey(id) && written[id] == value) continue
				write(encodeChannel(id, value))
				written[id] = value
			} catch (e: Exception) {
				LOGGER.warn("Channel {} failed to sample, dropping it", id, e)
				samplers.remove(id)
			}
		}
	}

	internal fun resetWritten() = written.clear()

	internal fun onRecord(record: ReplayRecord) {
		val (id, value) = decodeChannel(record.payload) ?: return
		if (value == null) replayed.remove(id) else replayed[id] = value
	}

	internal fun clearReplayed() = replayed.clear()
}

internal fun encodeChannel(id: String, value: String?): ByteArray {
	val bytes = ByteArrayOutputStream()
	DataOutputStream(bytes).use {
		it.writeUTF(id)
		val bytes = value?.toByteArray(Charsets.UTF_8)
		it.writeInt(bytes?.size ?: -1)
		if (bytes != null) it.write(bytes)
	}
	return bytes.toByteArray()
}

internal fun decodeChannel(payload: ByteArray): Pair<String, String?>? = try {
	DataInputStream(ByteArrayInputStream(payload)).use {
		val id = it.readUTF()
		val length = it.readInt()
		id to if (length < 0) null else String(it.readNBytes(length).also { bytes -> if (bytes.size != length) throw IOException("Truncated channel") }, Charsets.UTF_8)
	}
} catch (_: IOException) {
	null
}
