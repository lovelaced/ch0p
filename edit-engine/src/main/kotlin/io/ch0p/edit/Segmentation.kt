package io.ch0p.edit

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Turns scene shots into atomic editing units.
 *
 * Shots within [Preset.maxUnitMs] become one unit. Longer shots are split near the
 * preset's target shot length, with each internal cut snapped to the lowest-loudness
 * trough in a window and then nudged off any mid-word position. Scene boundaries are
 * already natural cut points and are kept as-is.
 */
object Segmentation {

    fun segment(a: Analysis, preset: Preset): List<EditUnit> {
        val units = ArrayList<EditUnit>()
        a.shots.forEachIndexed { idx, shot ->
            if (shot.durationMs <= preset.maxUnitMs) {
                units += EditUnit(shot.startMs, shot.endMs, idx)
            } else {
                units += splitLongShot(a, shot, idx, preset)
            }
        }
        // Drop fragments shorter than the preset's minimum.
        return units.filter { it.durationMs >= preset.minUnitMs }
    }

    private fun splitLongShot(a: Analysis, shot: Shot, idx: Int, preset: Preset): List<EditUnit> {
        val dur = shot.durationMs
        // Enough pieces that the average piece is near avgShotLen and no piece exceeds maxUnit.
        var nPieces = max(1, (dur.toDouble() / preset.avgShotLenMs).roundToInt())
        while (dur / nPieces > preset.maxUnitMs) nPieces++
        if (nPieces == 1) return listOf(EditUnit(shot.startMs, shot.endMs, idx))

        val window = min(preset.avgShotLenMs / 2, 800L)
        val cuts = ArrayList<Long>()
        for (i in 1 until nPieces) {
            val target = shot.startMs + dur * i / nPieces
            var c = snapToTrough(a, target, window, shot.startMs, shot.endMs)
            c = avoidMidWord(a.words, c)
            cuts += c.coerceIn(shot.startMs, shot.endMs)
        }

        // Build boundaries, enforce monotonicity + minimum spacing.
        val bounds = ArrayList<Long>().apply {
            add(shot.startMs); addAll(cuts.sorted()); add(shot.endMs)
        }
        val cleaned = ArrayList<Long>().apply { add(bounds.first()) }
        for (k in 1 until bounds.size) {
            if (bounds[k] - cleaned.last() >= preset.minUnitMs) cleaned += bounds[k]
        }
        if (cleaned.last() != shot.endMs) cleaned[cleaned.lastIndex] = shot.endMs

        return (0 until cleaned.size - 1).map { EditUnit(cleaned[it], cleaned[it + 1], idx) }
    }

    /** Search a window around [targetMs] for the minimum-loudness moment. */
    private fun snapToTrough(a: Analysis, targetMs: Long, windowMs: Long, lo: Long, hi: Long): Long {
        if (a.loudness.isEmpty()) return targetMs
        val stepMs = max(1L, (1000.0 / a.sampleRateHz).toLong())
        val from = max(lo, targetMs - windowMs)
        val to = min(hi, targetMs + windowMs)
        var bestMs = targetMs
        var bestVal = Float.MAX_VALUE
        var t = from
        while (t <= to) {
            val v = a.mean(a.loudness, t, t + stepMs)
            if (v < bestVal) { bestVal = v; bestMs = t }
            t += stepMs
        }
        return bestMs
    }

    /** If [ms] lands inside a spoken word, push it to that word's nearest edge. */
    private fun avoidMidWord(words: List<Word>, ms: Long): Long {
        val w = words.firstOrNull { ms in it.startMs..it.endMs } ?: return ms
        return if (ms - w.startMs < w.endMs - ms) w.startMs else w.endMs
    }
}
