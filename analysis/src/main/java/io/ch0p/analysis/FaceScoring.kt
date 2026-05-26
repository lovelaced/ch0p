package io.ch0p.analysis

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Pure face-presence scoring from bounding boxes — faces are the single biggest predictor
 * of "interesting" in consumer footage, and the largest face's center/size seeds the
 * smart-reframe trajectory. JVM-testable (no MediaPipe types here).
 */
object FaceScoring {

    data class FaceFrame(
        val score: Float,   // 0..1 interest contribution
        val cx: Float,      // normalized center of the dominant face
        val cy: Float,
        val size: Float,    // dominant face size as a fraction of the frame's larger edge
        val hasFace: Boolean,
    )

    val EMPTY = FaceFrame(0f, 0.5f, 0.5f, 0f, false)

    /** @param boxes list of [left, top, right, bottom] in pixels. */
    fun fromBoxes(boxes: List<FloatArray>, frameW: Int, frameH: Int): FaceFrame {
        if (boxes.isEmpty() || frameW <= 0 || frameH <= 0) return EMPTY
        val largest = boxes.maxByOrNull { (it[2] - it[0]) * (it[3] - it[1]) } ?: return EMPTY
        val w = (largest[2] - largest[0]).coerceAtLeast(0f)
        val h = (largest[3] - largest[1]).coerceAtLeast(0f)
        val areaRatio = (w * h) / (frameW.toFloat() * frameH)
        val cx = ((largest[0] + largest[2]) / 2f) / frameW
        val cy = ((largest[1] + largest[3]) / 2f) / frameH
        val centered = (1f - (abs(cx - 0.5f) + abs(cy - 0.5f))).coerceIn(0f, 1f)
        val sizeTerm = sqrt(areaRatio).coerceIn(0f, 1f)  // faces are small in-frame; sqrt lifts it
        val score = (0.6f * sizeTerm + 0.4f * centered).coerceIn(0f, 1f)
        return FaceFrame(score, cx, cy, max(w / frameW, h / frameH), true)
    }
}
