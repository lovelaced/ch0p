package io.ch0p.edit

/**
 * The editing brain. Pure and deterministic: given an [Analysis] and a [Preset],
 * produce an [EditDecisionList] the render pipeline can execute.
 *
 *   segment → score → select → arrange
 */
object AutoEditor {

    fun edit(analysis: Analysis, preset: Preset): EditDecisionList {
        val units = Segmentation.segment(analysis, preset)
        val scored = Scoring.score(analysis, units, preset)
        val selected = Selection.select(scored, preset, analysis.durationMs)
        return Arrange.arrange(selected, preset)
    }

    /**
     * Produce several distinct shorts from one source (like Opus/Vizard's multi-clip
     * output). Each variant draws from a candidate pool with the previous variants'
     * units removed, so the cuts cover different moments. The first variant is the
     * strongest; returns fewer than [count] when material runs out.
     */
    fun editVariants(analysis: Analysis, preset: Preset, count: Int = 3): List<EditDecisionList> {
        val units = Segmentation.segment(analysis, preset)
        val pool = Scoring.score(analysis, units, preset).toMutableList()
        val results = ArrayList<EditDecisionList>()
        repeat(count) {
            if (pool.isEmpty()) return results
            val selected = Selection.select(pool, preset, analysis.durationMs)
            if (selected.isEmpty()) return results
            results.add(Arrange.arrange(selected, preset))
            pool.removeAll(selected.toSet())
        }
        return results
    }
}
