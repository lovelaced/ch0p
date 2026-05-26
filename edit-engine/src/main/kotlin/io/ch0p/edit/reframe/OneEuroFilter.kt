package io.ch0p.edit.reframe

import kotlin.math.abs

/**
 * 1€ filter (Casiez, Roussel, Vogel) — a speed-adaptive low-pass: low cutoff when the
 * signal is still (kills jitter), higher cutoff when it moves fast (kills lag). Used to
 * smooth the auto-reframe crop trajectory so the frame glides rather than snaps or drifts.
 * Pure Kotlin, JVM-testable.
 */
class OneEuroFilter(
    private val minCutoff: Double = 1.0,
    private val beta: Double = 0.0,
    private val dCutoff: Double = 1.0,
) {
    private var initialized = false
    private var xPrev = 0.0
    private var dxPrev = 0.0
    private var tPrev = 0.0

    fun reset() { initialized = false }

    /** @param t timestamp in seconds (monotonic). */
    fun filter(x: Double, t: Double): Double {
        if (!initialized) {
            initialized = true; xPrev = x; dxPrev = 0.0; tPrev = t
            return x
        }
        val dt = (t - tPrev).takeIf { it > 1e-6 } ?: 1e-3
        val dx = (x - xPrev) / dt
        val edx = lowPass(dx, dxPrev, alpha(dCutoff, dt))
        val cutoff = minCutoff + beta * abs(edx)
        val ex = lowPass(x, xPrev, alpha(cutoff, dt))
        xPrev = ex; dxPrev = edx; tPrev = t
        return ex
    }

    private fun lowPass(x: Double, prev: Double, a: Double) = a * x + (1 - a) * prev

    private fun alpha(cutoff: Double, dt: Double): Double {
        val tau = 1.0 / (2 * Math.PI * cutoff)
        return 1.0 / (1.0 + tau / dt)
    }
}
