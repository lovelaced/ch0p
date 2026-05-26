package io.ch0p.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FaceScoringTest {

    @Test fun `no boxes is empty`() {
        val f = FaceScoring.fromBoxes(emptyList(), 160, 90)
        assertFalse(f.hasFace)
        assertEquals(0f, f.score, 1e-6f)
    }

    @Test fun `centered large face scores higher than small corner face`() {
        val frameW = 160; val frameH = 90
        val big = FaceScoring.fromBoxes(listOf(floatArrayOf(50f, 20f, 110f, 80f)), frameW, frameH)   // centered, large
        val small = FaceScoring.fromBoxes(listOf(floatArrayOf(0f, 0f, 16f, 12f)), frameW, frameH)     // tiny corner
        assertTrue(big.hasFace)
        assertTrue("big.score ${big.score} > small.score ${small.score}", big.score > small.score)
        // Center is near the middle of the frame.
        assertEquals(0.5f, big.cx, 0.05f)
    }

    @Test fun `dominant face is the largest box`() {
        val f = FaceScoring.fromBoxes(
            listOf(floatArrayOf(0f, 0f, 10f, 10f), floatArrayOf(60f, 30f, 120f, 80f)),
            160, 90,
        )
        // center should reflect the large box (~x 0.56), not the tiny one
        assertTrue(f.cx > 0.4f)
        assertTrue(f.size > 0f)
    }
}
