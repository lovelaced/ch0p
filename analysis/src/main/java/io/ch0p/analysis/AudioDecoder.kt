package io.ch0p.analysis

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.ByteArrayOutputStream
import java.nio.ByteOrder

/**
 * Decodes the first audio track of a file to mono 16 kHz 16-bit PCM — the format the
 * loudness/VAD/YAMNet stages expect. Returns an empty array if there is no audio track
 * or decoding fails (the pipeline then treats audio signals as silent).
 *
 * Device-only (MediaCodec); not host-testable.
 */
object AudioDecoder {

    private const val TARGET_RATE = 16_000
    private const val TIMEOUT_US = 10_000L

    fun decodeMono16k(path: String): ShortArray {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(path)
            var trackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                if ((f.getString(MediaFormat.KEY_MIME) ?: "").startsWith("audio/")) {
                    trackIndex = i; format = f; break
                }
            }
            if (trackIndex < 0 || format == null) return ShortArray(0)
            extractor.selectTrack(trackIndex)

            val mime = format.getString(MediaFormat.KEY_MIME)!!
            var srcRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT))
                format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 1

            codec = MediaCodec.createDecoderByType(mime).apply {
                configure(format, null, null, 0)
                start()
            }

            // Accumulate mono 16-bit little-endian PCM as raw bytes (avoids boxing).
            val mono = ByteArrayOutputStream()
            val info = MediaCodec.BufferInfo()
            var inputEos = false
            var outputEos = false

            while (!outputEos) {
                if (!inputEos) {
                    val inIdx = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inIdx >= 0) {
                        val inBuf = codec.getInputBuffer(inIdx)!!
                        val size = extractor.readSampleData(inBuf, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputEos = true
                        } else {
                            codec.queueInputBuffer(inIdx, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outIdx = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                when {
                    outIdx >= 0 -> {
                        val outBuf = codec.getOutputBuffer(outIdx)!!
                        val shorts = outBuf.order(ByteOrder.nativeOrder()).asShortBuffer()
                        val count = info.size / 2
                        var i = 0
                        while (i + channels <= count) {
                            val sample = if (channels <= 1) {
                                shorts.get(i).toInt()
                            } else {
                                var sum = 0
                                for (c in 0 until channels) sum += shorts.get(i + c)
                                sum / channels
                            }
                            mono.write(sample and 0xFF)
                            mono.write((sample shr 8) and 0xFF)
                            i += channels
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputEos = true
                    }
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val nf = codec.outputFormat
                        if (nf.containsKey(MediaFormat.KEY_SAMPLE_RATE)) srcRate = nf.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        if (nf.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) channels = nf.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                }
            }

            return resample(toShorts(mono.toByteArray()), srcRate, TARGET_RATE)
        } catch (_: Exception) {
            return ShortArray(0)
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun toShorts(bytes: ByteArray): ShortArray {
        val out = ShortArray(bytes.size / 2)
        for (i in out.indices) {
            val lo = bytes[i * 2].toInt() and 0xFF
            val hi = bytes[i * 2 + 1].toInt()
            out[i] = ((hi shl 8) or lo).toShort()
        }
        return out
    }

    /** Linear-interpolation resampler. */
    private fun resample(input: ShortArray, srcRate: Int, dstRate: Int): ShortArray {
        if (input.isEmpty() || srcRate <= 0) return ShortArray(0)
        if (srcRate == dstRate) return input
        val outLen = (input.size.toLong() * dstRate / srcRate).toInt()
        if (outLen <= 0) return ShortArray(0)
        val out = ShortArray(outLen)
        val ratio = srcRate.toDouble() / dstRate
        for (i in 0 until outLen) {
            val pos = i * ratio
            val i0 = pos.toInt()
            val i1 = (i0 + 1).coerceAtMost(input.size - 1)
            val frac = pos - i0
            out[i] = (input[i0] * (1 - frac) + input[i1] * frac).toInt().toShort()
        }
        return out
    }
}
