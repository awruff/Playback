package org.polyfrost.keyframe.standalone.export

import org.lwjgl.openal.SOFTLoopback
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

private const val BYTES_PER_SAMPLE = 2 * Float.SIZE_BYTES
internal const val TICK_SAMPLES = (AUDIO_SAMPLE_RATE / 20).toInt()

private val buffer = ByteBuffer.allocateDirect(TICK_SAMPLES * BYTES_PER_SAMPLE)

internal fun renderLoopback(device: Long, samples: Int, output: FileChannel?) {
	buffer.clear().limit(samples * BYTES_PER_SAMPLE)
	SOFTLoopback.alcRenderSamplesSOFT(device, buffer, samples)
	while (output != null && buffer.hasRemaining()) output.write(buffer)
}
