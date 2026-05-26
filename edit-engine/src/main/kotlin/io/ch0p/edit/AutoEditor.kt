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
}
