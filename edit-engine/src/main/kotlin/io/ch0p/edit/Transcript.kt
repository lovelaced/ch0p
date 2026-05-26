package io.ch0p.edit

/**
 * Transcript-derived editing aids from Whisper word timestamps. Pure Kotlin, JVM-testable.
 *
 *  - [fillerIntervals]: spans of filler words to micro-cut (the cut-video heuristic).
 *  - [sentenceEndTimes]: natural cut points (punctuation or a pause) — never mid-thought.
 *  - [silenceGaps]: dead-air spans between words, candidates for tightening.
 */
object Transcript {

    val FILLERS = setOf("um", "uh", "umm", "uhh", "er", "ah", "hmm", "mhm", "like", "uhm")

    fun fillerIntervals(words: List<Word>): List<LongRange> =
        words.filter { normalize(it.text) in FILLERS }.map { it.startMs..it.endMs }

    /** Word-end times where the sentence ends (punctuation) or a pause follows (> gapMs). */
    fun sentenceEndTimes(words: List<Word>, gapMs: Long = 400): List<Long> {
        val ends = ArrayList<Long>()
        for (i in words.indices) {
            val w = words[i]
            val endsSentence = w.text.trimEnd().lastOrNull() in setOf('.', '!', '?')
            val pauseAfter = i + 1 < words.size && (words[i + 1].startMs - w.endMs) > gapMs
            if (endsSentence || pauseAfter) ends.add(w.endMs)
        }
        return ends.distinct().sorted()
    }

    /** Gaps between consecutive words longer than [minGapMs] (removable dead air). */
    fun silenceGaps(words: List<Word>, minGapMs: Long = 700): List<LongRange> {
        val gaps = ArrayList<LongRange>()
        for (i in 0 until words.size - 1) {
            val gap = words[i + 1].startMs - words[i].endMs
            if (gap > minGapMs) gaps.add(words[i].endMs..words[i + 1].startMs)
        }
        return gaps
    }

    private fun normalize(text: String): String =
        text.lowercase().trim().trim('.', ',', '!', '?', ';', ':', '"', '\'')
}
