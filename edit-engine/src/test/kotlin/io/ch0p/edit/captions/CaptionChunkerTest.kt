package io.ch0p.edit.captions

import io.ch0p.edit.Word
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptionChunkerTest {

    private fun words(vararg pairs: Pair<String, Long>): List<Word> {
        // each word lasts 300ms, 50ms gap, starting at the given ms
        return pairs.map { (t, start) -> Word(t, start, start + 300) }
    }

    @Test fun `caps words per chunk`() {
        val ws = (0 until 10).map { Word("w$it", it * 350L, it * 350L + 300) }
        val chunks = CaptionChunker.chunk(ws, maxWords = 4)
        assertTrue("each chunk <= 4 words", chunks.all { it.words.size <= 4 })
        assertEquals("all words preserved", 10, chunks.sumOf { it.words.size })
    }

    @Test fun `breaks on a long pause`() {
        val ws = listOf(
            Word("hello", 0, 300), Word("there", 320, 600),
            Word("friend", 2000, 2300),  // 1400ms pause -> new chunk
        )
        val chunks = CaptionChunker.chunk(ws)
        assertEquals(2, chunks.size)
        assertEquals("hello there", chunks[0].text)
        assertEquals("friend", chunks[1].text)
    }

    @Test fun `breaks after sentence punctuation`() {
        val ws = listOf(
            Word("Stop.", 0, 300), Word("Now", 350, 600), Word("go", 650, 900),
        )
        val chunks = CaptionChunker.chunk(ws)
        assertEquals("Stop.", chunks.first().text)
    }

    @Test fun `chunk timing spans its words`() {
        val ws = words("a" to 0L, "b" to 350L)
        val c = CaptionChunker.chunk(ws).first()
        assertEquals(0L, c.startMs)
        assertEquals(650L, c.endMs)
    }

    @Test fun `empty input yields no chunks`() {
        assertTrue(CaptionChunker.chunk(emptyList()).isEmpty())
    }
}
