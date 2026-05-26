package io.ch0p.ingest.telemetry

import kotlin.math.max

/**
 * Bridges raw [Telemetry] into the uniform signal grid the edit-engine consumes. Pure
 * Kotlin (operates on FloatArray + timestamps), so it is unit-tested without a device.
 *
 * Telemetry boosts the `action` channel (physical motion the camera felt) and creates
 * `interest` bumps around operator HiLight tags — letting the editor favour exactly the
 * moments a human marked, even with no speech.
 */
object TelemetryFusion {

    /** Resample a timestamped energy curve onto [n] bins over [durationMs] (max-per-bin, forward-filled). */
    fun toGrid(energy: FloatArray, timesMs: LongArray, n: Int, durationMs: Long): FloatArray {
        val out = FloatArray(n)
        if (n == 0 || durationMs <= 0 || energy.isEmpty()) return out
        val count = minOf(energy.size, timesMs.size)
        for (i in 0 until count) {
            val bin = ((timesMs[i].coerceIn(0, durationMs - 1)) * n / durationMs).toInt().coerceIn(0, n - 1)
            out[bin] = max(out[bin], energy[i])
        }
        // Forward-fill empty bins so the curve is continuous.
        var last = 0f
        for (b in 0 until n) {
            if (out[b] > 0f) last = out[b] else out[b] = last
        }
        return out
    }

    /** Triangular interest bumps centred on each HiLight mark. */
    fun highlightCurve(marksMs: List<Long>, n: Int, durationMs: Long, halfWidthMs: Long = 1500): FloatArray {
        val out = FloatArray(n)
        if (n == 0 || durationMs <= 0 || marksMs.isEmpty()) return out
        val msPerBin = durationMs.toDouble() / n
        val halfBins = max(1, (halfWidthMs / msPerBin).toInt())
        for (mark in marksMs) {
            val center = (mark * n / durationMs).toInt().coerceIn(0, n - 1)
            for (b in (center - halfBins)..(center + halfBins)) {
                if (b in 0 until n) {
                    val falloff = 1f - kotlin.math.abs(b - center).toFloat() / (halfBins + 1)
                    out[b] = max(out[b], falloff)
                }
            }
        }
        return out
    }

    /** base reinforced by [add]*weight, clamped to 0..1. */
    fun boost(base: FloatArray, add: FloatArray, weight: Float): FloatArray =
        FloatArray(base.size) { i ->
            (base[i] + weight * (add.getOrElse(i) { 0f })).coerceIn(0f, 1f)
        }
}
