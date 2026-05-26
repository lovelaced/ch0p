package io.ch0p.analysis

import io.ch0p.edit.Shot

/**
 * Converts native shot-boundary timestamps into the [Shot] list the edit-engine consumes.
 * Pure Kotlin, JVM-testable. Boundaries are clamped, de-duplicated, and ordered; degenerate
 * (zero-length) shots are dropped.
 */
object ShotBuilder {

    fun fromCuts(cutTimesSec: DoubleArray, durationMs: Long): List<Shot> {
        if (durationMs <= 0) return emptyList()
        val bounds = sortedSetOf(0L, durationMs)
        for (c in cutTimesSec) {
            val ms = (c * 1000.0).toLong()
            if (ms in 1 until durationMs) bounds.add(ms)
        }
        val list = bounds.toList()
        return (0 until list.size - 1)
            .map { Shot(list[it], list[it + 1]) }
            .filter { it.durationMs > 0 }
    }
}
