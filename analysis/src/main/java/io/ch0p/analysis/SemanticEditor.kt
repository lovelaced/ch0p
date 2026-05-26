package io.ch0p.analysis

import io.ch0p.edit.Word

/**
 * The "understanding" layer: uses the on-device LLM over the Whisper transcript to write a
 * title and surface the most engaging hook/quote moments. Optional + flagship-gated; runs
 * only when a Gemma model is installed. Orchestration only — prompts/parsing live in the
 * pure [SemanticPrompts] (JVM-tested).
 */
class SemanticEditor(private val llm: LlmEngine) {

    data class Hook(val startMs: Long, val endMs: Long, val reason: String)
    data class Result(val title: String, val hooks: List<Hook>)

    fun analyze(words: List<Word>, maxCandidates: Int = 12): Result {
        if (words.isEmpty()) return Result("", emptyList())

        val title = SemanticPrompts.parseTitle(
            llm.generate(SemanticPrompts.titlePrompt(words.joinToString(" ") { it.text })),
        )

        val candidates = SemanticPrompts.sentenceCandidates(words).take(maxCandidates)
        val hooks = if (candidates.isEmpty()) emptyList() else {
            val byIndex = candidates.associateBy { it.index }
            SemanticPrompts.parseHookSelections(llm.generate(SemanticPrompts.hookPrompt(candidates)))
                .mapNotNull { sel -> byIndex[sel.index]?.let { Hook(it.startMs, it.endMs, sel.reason) } }
        }
        return Result(title, hooks)
    }
}
