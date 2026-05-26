package io.ch0p.edit

import kotlin.math.sqrt

/**
 * Selects units under a duration budget by greedily maximizing a submodular objective:
 *
 *   F(A) = Σ S(u)  −  λ_div · Σ_{u,v∈A} sim(u,v)  +  γ_cov · Coverage(A)
 *
 * The diversity term (the one non-obvious must-have) stops the reel from being five
 * near-identical moments; coverage rewards spread across the whole timeline. Candidates
 * are ranked by marginal-gain-per-second (knapsack-aware), and a quality floor lets a
 * short source yield a short reel instead of being padded with filler.
 */
object Selection {

    private const val COVERAGE_BUCKETS = 8

    fun select(scored: List<ScoredUnit>, preset: Preset, totalDurationMs: Long): List<ScoredUnit> {
        if (scored.isEmpty()) return emptyList()
        val minMs = preset.targetDurationMs.first
        val maxMs = preset.targetDurationMs.last
        val targetMs = (minMs + maxMs) / 2

        val selected = ArrayList<ScoredUnit>()
        val remaining = scored.toMutableList()
        var used = 0L

        while (remaining.isNotEmpty()) {
            var best: ScoredUnit? = null
            var bestGps = -Float.MAX_VALUE
            var bestGain = 0f
            for (u in remaining) {
                if (selected.isNotEmpty() && used + u.unit.durationMs > maxMs) continue
                val gain = marginalGain(u, selected, preset, totalDurationMs)
                val gps = gain / (u.unit.durationMs / 1000f).coerceAtLeast(0.001f)
                if (gps > bestGps) { bestGps = gps; best = u; bestGain = gain }
            }
            val pick = best ?: break

            // Once past the minimum length, stop adding low-value filler.
            if (used >= minMs && bestGain < preset.qualityFloor) break

            selected += pick
            used += pick.unit.durationMs
            remaining -= pick

            if (used >= maxMs) break
        }
        return selected
    }

    private fun marginalGain(
        u: ScoredUnit, selected: List<ScoredUnit>, preset: Preset, totalDurationMs: Long,
    ): Float {
        val quality = u.score
        val diversity = preset.diversityLambda * selected.sumOf { sim(u, it).toDouble() }.toFloat()
        val coverage = preset.coverageGamma * coverageDelta(u, selected, totalDurationMs)
        return quality - diversity + coverage
    }

    /** Cheap similarity: cosine of feature vectors, boosted when units share a shot. */
    private fun sim(a: ScoredUnit, b: ScoredUnit): Float {
        val cos = cosine(a.features, b.features)
        val sameShot = if (a.unit.shotIndex == b.unit.shotIndex) 1f else 0f
        return (0.6f * cos + 0.4f * sameShot).coerceIn(0f, 1f)
    }

    private fun cosine(a: Map<Signal, Float>, b: Map<Signal, Float>): Float {
        var dot = 0f; var na = 0f; var nb = 0f
        for (s in Signal.entries) {
            val x = a[s] ?: 0f; val y = b[s] ?: 0f
            dot += x * y; na += x * x; nb += y * y
        }
        if (na == 0f || nb == 0f) return 0f
        return (dot / (sqrt(na) * sqrt(nb))).coerceIn(0f, 1f)
    }

    /** New timeline buckets this unit would cover (fraction of [COVERAGE_BUCKETS]). */
    private fun coverageDelta(u: ScoredUnit, selected: List<ScoredUnit>, totalDurationMs: Long): Float {
        if (totalDurationMs <= 0) return 0f
        val covered = selected.map { bucketOf(it, totalDurationMs) }.toHashSet()
        val b = bucketOf(u, totalDurationMs)
        return if (b in covered) 0f else 1f / COVERAGE_BUCKETS
    }

    private fun bucketOf(u: ScoredUnit, totalDurationMs: Long): Int {
        val center = (u.unit.srcInMs + u.unit.srcOutMs) / 2
        return ((center * COVERAGE_BUCKETS) / totalDurationMs)
            .toInt().coerceIn(0, COVERAGE_BUCKETS - 1)
    }
}
