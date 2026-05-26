package io.ch0p.edit.captions

import io.ch0p.edit.Word

/** A timed caption phrase (1–4 words) for karaoke-style display. */
data class CaptionChunk(val text: String, val startMs: Long, val endMs: Long, val words: List<Word>)

/**
 * Groups Whisper word timestamps into short, well-timed caption chunks — the TikTok/Reels
 * convention of 1–4 words on screen at a time. Pure Kotlin, JVM-testable. The render layer
 * consumes these to drive per-word highlighting; they also serialize to SRT/VTT/ASS.
 */
object CaptionChunker {

    fun chunk(
        words: List<Word>,
        maxWords: Int = 4,
        maxChars: Int = 24,
        maxDurationMs: Long = 1_800,
        pauseMs: Long = 350,
    ): List<CaptionChunk> {
        if (words.isEmpty()) return emptyList()
        val chunks = ArrayList<CaptionChunk>()
        var cur = ArrayList<Word>()

        fun flush() {
            if (cur.isEmpty()) return
            chunks.add(CaptionChunk(cur.joinToString(" ") { it.text }, cur.first().startMs, cur.last().endMs, cur.toList()))
            cur = ArrayList()
        }

        for (w in words) {
            if (cur.isNotEmpty()) {
                val gap = w.startMs - cur.last().endMs
                val chars = cur.sumOf { it.text.length + 1 } + w.text.length
                val durIfAdded = w.endMs - cur.first().startMs
                val prevEndsSentence = cur.last().text.trimEnd().lastOrNull() in setOf('.', '!', '?')
                if (gap > pauseMs || cur.size >= maxWords || chars > maxChars ||
                    durIfAdded > maxDurationMs || prevEndsSentence
                ) {
                    flush()
                }
            }
            cur.add(w)
        }
        flush()

        return mergeOrphans(chunks, pauseMs)
    }

    /** Fold a lone trailing one-word chunk back into the previous chunk — but not across a pause. */
    private fun mergeOrphans(chunks: List<CaptionChunk>, pauseMs: Long): List<CaptionChunk> {
        if (chunks.size < 2) return chunks
        val last = chunks.last()
        val prev = chunks[chunks.size - 2]
        if (last.words.size == 1 && (last.startMs - prev.endMs) <= pauseMs) {
            val merged = CaptionChunk(
                "${prev.text} ${last.text}", prev.startMs, last.endMs, prev.words + last.words,
            )
            return chunks.dropLast(2) + merged
        }
        return chunks
    }
}
