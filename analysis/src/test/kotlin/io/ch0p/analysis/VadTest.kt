package io.ch0p.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

class VadTest {

    private val sr = 16_000

    @Test fun `silence reads as no speech`() {
        val c = Vad.speechCurve(ShortArray(sr), sr)
        assertTrue(c.isNotEmpty())
        assertTrue("silence should be near zero", c.average() < 0.3)
    }

    @Test fun `loud half scores higher than quiet half`() {
        // Quiet noise first, energetic broadband (speech-like) second.
        val rng = Random(1)
        val pcm = ShortArray(sr) { i ->
            val amp = if (i < sr / 2) 200.0 else 9000.0
            (rng.nextDouble(-1.0, 1.0) * amp).toInt().toShort()
        }
        val c = Vad.speechCurve(pcm, sr)
        val half = c.size / 2
        val quiet = c.take(half).average()
        val loud = c.drop(half).average()
        assertTrue("loud half ($loud) should exceed quiet half ($quiet)", loud > quiet)
    }

    @Test fun `steady low-frequency tone is suppressed relative to broadband`() {
        // A pure low tone (music/hum-like) should not score as high as broadband noise of
        // similar energy, thanks to the ZCR gate.
        val tone = ShortArray(sr) { i -> (sin(2 * PI * 80 * i / sr) * 9000).toInt().toShort() }
        val rng = Random(2)
        val broadband = ShortArray(sr) { (rng.nextDouble(-1.0, 1.0) * 9000).toInt().toShort() }
        val toneScore = Vad.speechCurve(tone, sr).average()
        val broadbandScore = Vad.speechCurve(broadband, sr).average()
        assertTrue("broadband ($broadbandScore) >= tone ($toneScore)", broadbandScore >= toneScore)
    }

    @Test fun `empty input handled`() {
        assertEquals(0, Vad.speechCurve(ShortArray(0), sr).size)
    }
}
