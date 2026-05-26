package io.ch0p.edit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptTest {

    @Test fun `filler words are flagged with their spans`() {
        val words = listOf(
            Word("Hello", 0, 400), Word("um", 450, 700), Word("world", 750, 1200),
        )
        assertEquals(listOf(450L..700L), Transcript.fillerIntervals(words))
    }

    @Test fun `sentence ends from punctuation and pauses`() {
        val words = listOf(
            Word("Hello.", 0, 400),       // punctuation -> boundary at 400
            Word("And", 1200, 1400),      // 800ms gap before -> boundary at prior word (400) already; also this word
            Word("then", 1450, 1700),
        )
        val ends = Transcript.sentenceEndTimes(words, gapMs = 400)
        assertTrue("sentence end at the period", ends.contains(400L))
    }

    @Test fun `silence gaps are detected`() {
        val words = listOf(Word("a", 0, 500), Word("b", 2000, 2500))
        assertEquals(listOf(500L..2000L), Transcript.silenceGaps(words, minGapMs = 700))
    }

    @Test fun `no transcript yields nothing`() {
        assertTrue(Transcript.fillerIntervals(emptyList()).isEmpty())
        assertTrue(Transcript.sentenceEndTimes(emptyList()).isEmpty())
        assertTrue(Transcript.silenceGaps(emptyList()).isEmpty())
    }
}
