package io.ch0p.edit.captions

import io.ch0p.edit.Word
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleWriterTest {

    private val chunks = listOf(
        CaptionChunk("hello there", 1_000, 2_500, listOf(Word("hello", 1_000, 1_700), Word("there", 1_800, 2_500))),
        CaptionChunk("friend", 3_661_000, 3_662_000, listOf(Word("friend", 3_661_000, 3_662_000))),
    )

    @Test fun `srt has index and comma millis`() {
        val srt = SubtitleWriter.toSrt(chunks)
        assertTrue(srt.startsWith("1\n"))
        assertTrue(srt.contains("00:00:01,000 --> 00:00:02,500"))
        assertTrue(srt.contains("hello there"))
        assertTrue("hour rollover", srt.contains("01:01:01,000 --> 01:01:02,000"))
    }

    @Test fun `vtt has header and dot millis`() {
        val vtt = SubtitleWriter.toVtt(chunks)
        assertTrue(vtt.startsWith("WEBVTT"))
        assertTrue(vtt.contains("00:00:01.000 --> 00:00:02.500"))
    }

    @Test fun `empty chunks produce minimal output`() {
        assertTrue(SubtitleWriter.toSrt(emptyList()).isEmpty())
        assertTrue(SubtitleWriter.toVtt(emptyList()).startsWith("WEBVTT"))
    }
}
