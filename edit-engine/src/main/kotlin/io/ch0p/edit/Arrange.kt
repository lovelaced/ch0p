package io.ch0p.edit

/**
 * Orders the selected set into a final EDL.
 *  - CHRONOLOGICAL: source order (story logic preserved).
 *  - HOOK_FIRST: strongest moment first, then chronological (stop-the-scroll).
 *  - BUILD_TO_CLIMAX: energy rises toward the end.
 */
object Arrange {

    fun arrange(selected: List<ScoredUnit>, preset: Preset): EditDecisionList {
        val ordered: List<ScoredUnit> = when (preset.ordering) {
            Ordering.CHRONOLOGICAL -> selected.sortedBy { it.unit.srcInMs }
            Ordering.HOOK_FIRST -> hookFirst(selected)
            Ordering.BUILD_TO_CLIMAX -> selected.sortedBy { energy(it) }
        }
        val entries = ordered.mapIndexed { i, su ->
            EdlEntry(
                order = i,
                srcInMs = su.unit.srcInMs,
                srcOutMs = su.unit.srcOutMs,
                transition = if (i == 0) TransitionType.HARD_CUT else preset.defaultTransition,
            )
        }
        return EditDecisionList(preset.id, entries)
    }

    private fun hookFirst(selected: List<ScoredUnit>): List<ScoredUnit> {
        if (selected.isEmpty()) return selected
        val hook = selected.maxBy { it.score }
        val rest = selected.filter { it !== hook }.sortedBy { it.unit.srcInMs }
        return listOf(hook) + rest
    }

    /** Composite "energy" for climax ordering. */
    private fun energy(su: ScoredUnit): Float {
        val f = su.features
        return ((f[Signal.ACTION] ?: 0f) + (f[Signal.DRAMA] ?: 0f) + (f[Signal.INTEREST] ?: 0f)) / 3f
    }
}
