package io.ch0p.analysis

import io.ch0p.edit.Transcript
import io.ch0p.edit.Word

/** A transcript span offered to the LLM as a clip candidate. */
data class Candidate(val index: Int, val startMs: Long, val endMs: Long, val text: String)

/** The LLM's pick of a candidate, with its rationale. */
data class HookSelection(val index: Int, val reason: String)

/**
 * Prompt construction + response parsing for the on-device LLM semantic layer. Pure and
 * JVM-testable — the inference itself runs in [LlmEngine]. Kept deliberately strict so a
 * small on-device model's output is easy to parse.
 */
object SemanticPrompts {

    fun titlePrompt(transcript: String): String = buildString {
        append("You are a short-form video editor. Read the transcript and write ONE punchy title ")
        append("(max 8 words, no quotes, no emoji).\n\nTranscript:\n")
        append(transcript.take(4000))
        append("\n\nTitle:")
    }

    fun parseTitle(response: String): String =
        response.lineSequence()
            .map { it.trim().removePrefix("Title:").trim().trim('"', '\'', '*', '#', ' ') }
            .firstOrNull { it.isNotBlank() }
            ?.take(80)
            .orEmpty()

    fun hookPrompt(candidates: List<Candidate>): String = buildString {
        append("Pick the 1-3 most engaging moments (hook, punchline, quote, reaction) for a short clip.\n")
        append("Reply ONLY with lines formatted: <index> | <short reason>\n\nCandidates:\n")
        candidates.forEach { c -> append("[").append(c.index).append("] ").append(c.text.take(160)).append('\n') }
        append("\nSelections:")
    }

    /** Parse "<index> | <reason>" lines, ignoring anything malformed. */
    fun parseHookSelections(response: String): List<HookSelection> =
        response.lineSequence().mapNotNull { line ->
            val m = Regex("""^[\[(\s]*(\d+)[\s|:.)\]\-]+(.+)$""").find(line.trim()) ?: return@mapNotNull null
            val idx = m.groupValues[1].toIntOrNull() ?: return@mapNotNull null
            HookSelection(idx, m.groupValues[2].trim())
        }.distinctBy { it.index }.toList()

    /** Group words into sentence-level candidates (split at transcript sentence ends). Pure. */
    fun sentenceCandidates(words: List<Word>): List<Candidate> {
        if (words.isEmpty()) return emptyList()
        val ends = Transcript.sentenceEndTimes(words).toHashSet()
        val out = ArrayList<Candidate>()
        var cur = ArrayList<Word>()
        var idx = 0
        for (w in words) {
            cur.add(w)
            if (w.endMs in ends) { out.add(toCandidate(idx++, cur)); cur = ArrayList() }
        }
        if (cur.isNotEmpty()) out.add(toCandidate(idx, cur))
        return out
    }

    private fun toCandidate(index: Int, words: List<Word>) =
        Candidate(index, words.first().startMs, words.last().endMs, words.joinToString(" ") { it.text })
}
