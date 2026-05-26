package io.ch0p.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class LoudnessTest {

    private val sr = 16_000

    @Test fun `silence yields zero energy`() {
        val pcm = ShortArray(sr)  // 1s of zeros
        val c = Loudness.rmsCurve(pcm, sr)
        assertTrue(c.isNotEmpty())
        assertTrue(c.all { it == 0f })
    }

    @Test fun `a tone yields positive energy`() {
        val pcm = ShortArray(sr) { i ->
            (sin(2 * PI * 440 * i / sr) * 16000).toInt().toShort()
        }
        val c = Loudness.rmsCurve(pcm, sr)
        assertTrue("tone should register energy", c.average() > 0.1)
    }

    @Test fun `normalized curve peaks at one`() {
        // Quiet first half, loud second half.
        val pcm = ShortArray(sr) { i ->
            val amp = if (i < sr / 2) 1000.0 else 20000.0
            (sin(2 * PI * 440 * i / sr) * amp).toInt().toShort()
        }
        val c = Loudness.normalizedCurve(pcm, sr)
        assertEquals(1f, c.max(), 1e-4f)
        assertTrue("first half quieter than second", c.first() < c.last())
    }

    @Test fun `empty input is handled`() {
        assertEquals(0, Loudness.rmsCurve(ShortArray(0), sr).size)
        assertEquals(0, Loudness.rmsCurve(ShortArray(10), 0).size)
    }
}
