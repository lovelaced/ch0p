package io.ch0p.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NimaScoringTest {

    @Test fun `uniform distribution scores mid`() {
        val uniform = FloatArray(10) { 0.1f }
        assertEquals(0.55f, NimaScoring.meanScore(uniform), 1e-4f)  // mean 5.5 -> /10
    }

    @Test fun `peak at top bucket scores high, bottom scores low`() {
        val high = FloatArray(10).also { it[9] = 1f }
        val low = FloatArray(10).also { it[0] = 1f }
        assertEquals(1.0f, NimaScoring.meanScore(high), 1e-4f)
        assertEquals(0.1f, NimaScoring.meanScore(low), 1e-4f)
        assertTrue(NimaScoring.meanScore(high) > NimaScoring.meanScore(low))
    }

    @Test fun `unnormalized distribution is renormalized`() {
        // probabilities summing to 2.0 should still yield the same mean as 1.0
        val d = FloatArray(10) { 0.2f }
        assertEquals(0.55f, NimaScoring.meanScore(d), 1e-4f)
    }

    @Test fun `empty is zero`() {
        assertEquals(0f, NimaScoring.meanScore(FloatArray(0)), 1e-6f)
    }
}
