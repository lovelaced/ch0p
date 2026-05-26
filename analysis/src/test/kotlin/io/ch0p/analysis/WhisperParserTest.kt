package io.ch0p.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WhisperParserTest {

    @Test fun `parses delimited words`() {
        val raw = "hello\t0\t400\nthere\t450\t800\n"
        val words = WhisperParser.parse(raw)
        assertEquals(2, words.size)
        assertEquals("hello", words[0].text)
        assertEquals(0L, words[0].startMs)
        assertEquals(800L, words[1].endMs)
    }

    @Test fun `skips malformed and out-of-order lines`() {
        val raw = "ok\t10\t20\nbad-line\nneg\t100\t50\n\tmissing\t1\t2\n"
        val words = WhisperParser.parse(raw)
        assertEquals(1, words.size)
        assertEquals("ok", words[0].text)
    }

    @Test fun `blank input yields nothing`() {
        assertTrue(WhisperParser.parse("").isEmpty())
        assertTrue(WhisperParser.parse("   ").isEmpty())
    }
}
