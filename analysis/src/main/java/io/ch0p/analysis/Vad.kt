package io.ch0p.analysis

import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Energy-based voice-activity detection over mono 16 kHz PCM, producing a per-window
 * speech-probability curve (0..1). Adaptive noise floor + soft threshold + short
 * smoothing. Pure Kotlin, JVM-testable.
 *
 * This is the cheap baseline the research called a fallback; the planned upgrade is
 * Silero VAD (ONNX) for music/noise robustness. Combined with zero-crossing-rate gating
 * to suppress steady tones (hum/music) that energy alone would flag.
 */
object Vad {

    fun speechCurve(pcm: ShortArray, sampleRate: Int, windowMs: Int = 30): FloatArray {
        if (pcm.isEmpty() || sampleRate <= 0) return FloatArray(0)
        val win = (sampleRate.toLong() * windowMs / 1000).toInt().coerceAtLeast(1)
        val n = pcm.size / win
        if (n == 0) return FloatArray(0)

        val energy = FloatArray(n)
        val zcr = FloatArray(n)
        for (w in 0 until n) {
            val base = w * win
            var sumSq = 0.0
            var crossings = 0
            var prev = pcm[base].toInt()
            for (i in 0 until win) {
                val s = pcm[base + i].toInt()
                val x = s / 32768.0
                sumSq += x * x
                if ((s >= 0) != (prev >= 0)) crossings++
                prev = s
            }
            energy[w] = sqrt(sumSq / win).toFloat()
            zcr[w] = crossings.toFloat() / win
        }

        val floor = percentile(energy, 0.10)
        val peak = percentile(energy, 0.95)
        val range = (peak - floor).coerceAtLeast(1e-6f)
        val thr = floor + 0.15f * range

        val raw = FloatArray(n) { w ->
            val p = sigmoid((energy[w] - thr) / (0.10f * range))
            // Speech ZCR sits in a mid band; suppress steady tones (very low ZCR).
            val zcrGate = if (zcr[w] < 0.02f) 0.4f else 1f
            p * zcrGate
        }
        return smooth(raw)
    }

    /** 3-tap moving average to debounce single-frame flickers. */
    private fun smooth(a: FloatArray): FloatArray {
        if (a.size < 3) return a
        val out = FloatArray(a.size)
        out[0] = a[0]; out[a.size - 1] = a[a.size - 1]
        for (i in 1 until a.size - 1) out[i] = (a[i - 1] + a[i] + a[i + 1]) / 3f
        return out
    }

    private fun sigmoid(x: Float): Float = 1f / (1f + exp(-x))

    private fun percentile(values: FloatArray, p: Double): Float {
        if (values.isEmpty()) return 0f
        val sorted = values.sorted()
        val idx = ((sorted.size - 1) * p).toInt().coerceIn(0, sorted.size - 1)
        return sorted[idx]
    }
}
