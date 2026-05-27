package io.ch0p.analysis

import kotlin.math.sqrt

/**
 * Computes a single makeup gain to bring the selected clips toward a target loudness, so output
 * audio isn't wildly quiet or hot. RMS-based (not true LUFS, but effective and cheap) over the
 * already-cached proxy PCM; clamped to a sane range and paired with a limiter at render time.
 */
object LoudnessNormalizer {

    private const val SR = 16_000

    fun gainForSpans(proxyPath: String, spansMs: List<LongRange>, targetRms: Float = 0.12f): Float {
        if (spansMs.isEmpty()) return 1f
        val pcm = AudioDecoder.decodeMono16k(proxyPath)  // cached from analysis
        if (pcm.isEmpty()) return 1f
        var sumSq = 0.0
        var n = 0L
        for (span in spansMs) {
            val from = (span.first * SR / 1000).toInt().coerceIn(0, pcm.size)
            val to = (span.last * SR / 1000).toInt().coerceIn(from, pcm.size)
            var i = from
            while (i < to) { val s = pcm[i] / 32768.0; sumSq += s * s; n++; i++ }
        }
        if (n == 0L) return 1f
        val rms = sqrt(sumSq / n)
        if (rms < 1e-4) return 1f                       // silence: don't amplify noise
        return (targetRms / rms).toFloat().coerceIn(0.5f, 4f)
    }
}
