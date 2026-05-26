package io.ch0p.edit

import kotlin.math.exp
import kotlin.math.max

/**
 * Scores units by fusing normalized signal features with the preset's weights.
 *
 * Relative signals (action, speech, laughter, drama, aesthetic, interest) are
 * robust-normalized across the candidate set (p5/p95 clip) so the score adapts to
 * each video's own dynamic range. Scene length is a preference curve, not normalized.
 * The composite blends a weighted sum with a weighted-max term (ALPHA) because
 * highlights are spiky, not balanced.
 */
object Scoring {

    private const val ALPHA = 0.7f
    private val RELATIVE = listOf(
        Signal.ACTION, Signal.SPEECH, Signal.LAUGHTER,
        Signal.DRAMA, Signal.AESTHETIC, Signal.INTEREST,
    )

    fun score(a: Analysis, units: List<EditUnit>, preset: Preset): List<ScoredUnit> {
        if (units.isEmpty()) return emptyList()

        // 1. Raw per-signal values per unit.
        val raw: List<Map<Signal, Float>> = units.map { rawFeatures(a, it, preset) }

        // 2. Robust-normalize the relative signals across units.
        val normalizers = RELATIVE.associateWith { sig ->
            Normalizer.fit(raw.map { it[sig] ?: 0f })
        }
        val features = units.indices.map { i ->
            buildMap {
                put(Signal.SCENE_LENGTH, raw[i][Signal.SCENE_LENGTH] ?: 0f)  // already a preference 0..1
                for (sig in RELATIVE) put(sig, normalizers.getValue(sig).apply(raw[i][sig] ?: 0f))
            }
        }

        // 3. Composite score.
        return units.indices.map { i ->
            val f = features[i]
            var weighted = 0f
            var wmax = 0f
            for (sig in Signal.entries) {
                val term = preset.weights[sig] * (f[sig] ?: 0f)
                weighted += term
                wmax = max(wmax, term)
            }
            val composite = ALPHA * weighted + (1f - ALPHA) * wmax
            ScoredUnit(units[i], f, composite)
        }
    }

    private fun rawFeatures(a: Analysis, u: EditUnit, preset: Preset): Map<Signal, Float> {
        val shotLen = a.shots[u.shotIndex].durationMs
        return mapOf(
            Signal.SCENE_LENGTH to lengthPreference(shotLen, preset),
            Signal.ACTION to a.percentile(a.action, u.srcInMs, u.srcOutMs),
            Signal.SPEECH to a.mean(a.speech, u.srcInMs, u.srcOutMs),
            Signal.LAUGHTER to a.percentile(a.laughter, u.srcInMs, u.srcOutMs),
            Signal.DRAMA to a.percentile(a.drama, u.srcInMs, u.srcOutMs),
            Signal.AESTHETIC to a.mean(a.aesthetic, u.srcInMs, u.srcOutMs),
            Signal.INTEREST to a.percentile(a.interest, u.srcInMs, u.srcOutMs),
        )
    }

    /** Gaussian preference around the preset's target shot length L*. */
    private fun lengthPreference(lenMs: Long, preset: Preset): Float {
        val d = (lenMs - preset.avgShotLenMs).toDouble()
        val s = preset.shotLenSigmaMs.toDouble()
        return exp(-(d * d) / (2.0 * s * s)).toFloat()
    }
}

/** Robust min-max over the 5th..95th percentile. */
class Normalizer private constructor(private val lo: Float, private val hi: Float) {
    fun apply(x: Float): Float {
        if (hi <= lo) return 0.5f
        return ((x - lo) / (hi - lo)).coerceIn(0f, 1f)
    }

    companion object {
        fun fit(values: List<Float>): Normalizer {
            if (values.isEmpty()) return Normalizer(0f, 1f)
            val sorted = values.sorted()
            return Normalizer(percentile(sorted, 0.05), percentile(sorted, 0.95))
        }

        private fun percentile(sorted: List<Float>, p: Double): Float {
            val idx = ((sorted.size - 1) * p).toInt().coerceIn(0, sorted.size - 1)
            return sorted[idx]
        }
    }
}
