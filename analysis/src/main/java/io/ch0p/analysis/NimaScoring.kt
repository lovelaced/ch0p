package io.ch0p.analysis

/**
 * NIMA score from the model's K-bucket softmax distribution: the mean opinion score
 * (Σ i·p_i over buckets 1..K), normalized to 0..1 by the bucket count. Works for the usual
 * 10-bucket NIMA and any other bucket count. Pure & JVM-testable.
 */
object NimaScoring {
    fun meanScore(distribution: FloatArray): Float {
        if (distribution.isEmpty()) return 0f
        var weighted = 0f
        var total = 0f
        for (i in distribution.indices) {
            weighted += (i + 1) * distribution[i]
            total += distribution[i]
        }
        val mean = if (total > 0f) weighted / total else 0f  // 1..K
        return (mean / distribution.size).coerceIn(0f, 1f)
    }
}
