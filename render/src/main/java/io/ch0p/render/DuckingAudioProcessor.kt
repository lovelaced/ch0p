package io.ch0p.render

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer

/**
 * Applies a time-varying gain to a background-music track so it sits under the speech: the
 * [envelope] holds the target music gain (0..1) sampled at [envHz] over the OUTPUT timeline —
 * low where the underlying clips have speech (ducked), higher in the gaps. Operates on
 * 16-bit PCM; mono or stereo. Attach to the music [EditedMediaItem] only.
 */
class DuckingAudioProcessor(
    private val envelope: FloatArray,
    private val envHz: Float,
) : BaseAudioProcessor() {

    private var channels = 2
    private var sampleRate = 44_100
    private var framesDone = 0L  // per-channel frames processed (output time)

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw UnhandledAudioFormatException(inputAudioFormat)
        }
        channels = inputAudioFormat.channelCount
        sampleRate = inputAudioFormat.sampleRate
        return inputAudioFormat  // unchanged format, just scaled samples
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        val out = replaceOutputBuffer(remaining)
        val bytesPerFrame = 2 * channels
        while (inputBuffer.remaining() >= bytesPerFrame) {
            val gain = gainAt(framesDone.toDouble() / sampleRate)
            for (ch in 0 until channels) {
                val s = inputBuffer.short.toInt()
                out.putShort((s * gain).toInt().coerceIn(-32768, 32767).toShort())
            }
            framesDone++
        }
        out.flip()
    }

    private fun gainAt(timeSec: Double): Float {
        if (envelope.isEmpty()) return DEFAULT_GAIN
        val i = (timeSec * envHz).toInt().coerceIn(0, envelope.size - 1)
        return envelope[i]
    }

    companion object {
        const val DEFAULT_GAIN = 0.6f
    }
}
