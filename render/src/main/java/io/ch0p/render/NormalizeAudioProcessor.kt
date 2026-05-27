package io.ch0p.render

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer

/**
 * Applies a fixed makeup [gain] to 16-bit PCM and hard-limits to full scale, so the normalized
 * speech doesn't clip after boosting. [gain] comes from
 * [io.ch0p.analysis.LoudnessNormalizer.gainForSpans]. Attach to the video clips' audio.
 */
class NormalizeAudioProcessor(private val gain: Float) : BaseAudioProcessor() {

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        val out = replaceOutputBuffer(remaining)
        while (inputBuffer.remaining() >= 2) {
            val v = (inputBuffer.short * gain).toInt().coerceIn(-32768, 32767)  // makeup + limiter
            out.putShort(v.toShort())
        }
        out.flip()
    }
}
