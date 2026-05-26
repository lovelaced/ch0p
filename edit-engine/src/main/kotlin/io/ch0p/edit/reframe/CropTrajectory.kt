package io.ch0p.edit.reframe

import kotlin.math.abs

/** A subject target per sampled frame, in normalized [0,1] frame coordinates. */
data class SubjectTarget(val timeSec: Double, val cx: Double, val cy: Double, val size: Double)

/** A smoothed crop window keyframe (normalized center + size), consumed at render time. */
data class CropKeyframe(val timeSec: Double, val cx: Double, val cy: Double, val size: Double)

/**
 * Builds a smoothed crop trajectory from per-frame subject targets (AutoFlip-style):
 * 1€-filter the center + size, clamp pan speed, and hard-snap (reset the filter) at scene
 * cuts so the frame never glides across a hard cut. Pure Kotlin, JVM-testable.
 */
object CropTrajectory {

    enum class Follow(val minCutoff: Double, val beta: Double, val maxPanPerSec: Double) {
        SLOW(0.6, 0.003, 0.25),
        DEFAULT(1.0, 0.007, 0.5),
        FAST(1.6, 0.02, 1.0),
    }

    fun build(
        targets: List<SubjectTarget>,
        cutsSec: List<Double>,
        follow: Follow = Follow.DEFAULT,
    ): List<CropKeyframe> {
        if (targets.isEmpty()) return emptyList()
        val cuts = cutsSec.sorted()
        val fx = OneEuroFilter(follow.minCutoff, follow.beta)
        val fy = OneEuroFilter(follow.minCutoff, follow.beta)
        val fs = OneEuroFilter(follow.minCutoff, follow.beta)

        val out = ArrayList<CropKeyframe>(targets.size)
        var cutIdx = 0
        var prev: CropKeyframe? = null
        for (target in targets.sortedBy { it.timeSec }) {
            // Reset filters when we've crossed a scene cut since the previous sample.
            while (cutIdx < cuts.size && cuts[cutIdx] <= target.timeSec) {
                if (prev != null && cuts[cutIdx] > prev.timeSec) { fx.reset(); fy.reset(); fs.reset() }
                cutIdx++
            }
            var cx = fx.filter(target.cx, target.timeSec)
            var cy = fy.filter(target.cy, target.timeSec)
            val size = fs.filter(target.size, target.timeSec).coerceIn(0.1, 1.0)

            // Clamp pan speed relative to the previous keyframe.
            if (prev != null) {
                val dt = (target.timeSec - prev.timeSec).coerceAtLeast(1e-3)
                val maxStep = follow.maxPanPerSec * dt
                cx = clampStep(prev.cx, cx, maxStep)
                cy = clampStep(prev.cy, cy, maxStep)
            }
            val kf = CropKeyframe(target.timeSec, cx.coerceIn(0.0, 1.0), cy.coerceIn(0.0, 1.0), size)
            out.add(kf); prev = kf
        }
        return out
    }

    private fun clampStep(from: Double, to: Double, maxStep: Double): Double {
        val d = to - from
        return if (abs(d) <= maxStep) to else from + maxStep * (if (d > 0) 1 else -1)
    }
}
