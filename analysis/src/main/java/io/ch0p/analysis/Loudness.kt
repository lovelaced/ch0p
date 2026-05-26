package io.ch0p.analysis

import kotlin.math.sqrt

/**
 * Per-window RMS energy from mono PCM — the cheap "excitement" proxy and the curve used
 * to snap cuts to audio troughs. Pure Kotlin so it unit-tests on the JVM.
 * (Perceptual K-weighted LUFS via libebur128 is a later native upgrade.)
 */
object Loudness {

    /** Raw RMS per window (linear 0..1, where 1.0 = full-scale). */
    fun rmsCurve(pcm: ShortArray, sampleRate: Int, windowMs: Int = 50): FloatArray {
        if (pcm.isEmpty() || sampleRate <= 0) return FloatArray(0)
        val win = (sampleRate.toLong() * windowMs / 1000).toInt().coerceAtLeast(1)
        val n = pcm.size / win
        val out = FloatArray(n)
        for (w in 0 until n) {
            val base = w * win
            var sum = 0.0
            for (i in 0 until win) {
                val s = pcm[base + i] / 32768.0
                sum += s * s
            }
            out[w] = sqrt(sum / win).toFloat()
        }
        return out
    }

    /** RMS curve normalized so the loudest window is 1.0 (clip-relative). */
    fun normalizedCurve(pcm: ShortArray, sampleRate: Int, windowMs: Int = 50): FloatArray {
        val c = rmsCurve(pcm, sampleRate, windowMs)
        val max = c.maxOrNull() ?: 0f
        if (max <= 0f) return c
        return FloatArray(c.size) { c[it] / max }
    }
}
