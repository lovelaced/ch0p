package io.ch0p.edit

/**
 * Analysis output for one source clip — the input to the editing brain.
 *
 * Signal channels are uniformly sampled at [sampleRateHz] and normalized to 0..1.
 * They are produced by the (device-side) analysis pipeline; the engine treats them
 * as opaque per-time curves so it stays pure, deterministic, and JVM-testable.
 */
class Analysis(
    val durationMs: Long,
    val frameRate: Double,
    val shots: List<Shot>,
    val sampleRateHz: Double,
    val action: FloatArray,
    val speech: FloatArray,
    val laughter: FloatArray,
    val loudness: FloatArray,
    val drama: FloatArray,
    val aesthetic: FloatArray,
    val interest: FloatArray,
    val words: List<Word> = emptyList(),
    val beatsMs: LongArray = LongArray(0),
) {
    init {
        require(shots.isNotEmpty()) { "Analysis needs at least one shot" }
        require(sampleRateHz > 0.0) { "sampleRateHz must be positive" }
    }

    /** Index into a signal array for a given timestamp, clamped to bounds. */
    private fun indexAt(ms: Long, size: Int): Int {
        if (size == 0) return 0
        val i = ((ms / 1000.0) * sampleRateHz).toInt()
        return i.coerceIn(0, size - 1)
    }

    /** Mean of a signal channel over [startMs, endMs). */
    fun mean(signal: FloatArray, startMs: Long, endMs: Long): Float {
        if (signal.isEmpty() || endMs <= startMs) return 0f
        val a = indexAt(startMs, signal.size)
        val b = indexAt(endMs - 1, signal.size)
        var sum = 0.0
        var n = 0
        for (i in a..b) { sum += signal[i]; n++ }
        return if (n == 0) 0f else (sum / n).toFloat()
    }

    /** Robust "best moment" reducer: high percentile over a span. */
    fun percentile(signal: FloatArray, startMs: Long, endMs: Long, p: Double = 0.9): Float {
        if (signal.isEmpty() || endMs <= startMs) return 0f
        val a = indexAt(startMs, signal.size)
        val b = indexAt(endMs - 1, signal.size)
        if (b < a) return 0f
        val slice = signal.copyOfRange(a, b + 1).sorted()
        val idx = ((slice.size - 1) * p).toInt().coerceIn(0, slice.size - 1)
        return slice[idx]
    }
}

data class Shot(val startMs: Long, val endMs: Long) {
    val durationMs: Long get() = endMs - startMs
}

data class Word(val text: String, val startMs: Long, val endMs: Long)

/** Which signals exist, in a fixed order, so weights and feature vectors stay aligned. */
enum class Signal { SCENE_LENGTH, ACTION, SPEECH, LAUGHTER, DRAMA, AESTHETIC, INTEREST }

/** One contiguous source span chosen as an editing atom. */
data class EditUnit(
    val srcInMs: Long,
    val srcOutMs: Long,
    val shotIndex: Int,
) {
    val durationMs: Long get() = srcOutMs - srcInMs
}

/** A scored unit carries its per-signal normalized features and composite score. */
data class ScoredUnit(
    val unit: EditUnit,
    val features: Map<Signal, Float>,  // normalized 0..1
    val score: Float,
)

/** Final assembled edit: ordered units with render hints. */
data class EditDecisionList(
    val presetId: String,
    val units: List<EdlEntry>,
) {
    val totalDurationMs: Long get() = units.sumOf { it.durationMs }
}

data class EdlEntry(
    val order: Int,
    val srcInMs: Long,
    val srcOutMs: Long,
    val transition: TransitionType,
) {
    val durationMs: Long get() = srcOutMs - srcInMs
}

enum class TransitionType { HARD_CUT, DISSOLVE, WHIP, SPEED_RAMP }
