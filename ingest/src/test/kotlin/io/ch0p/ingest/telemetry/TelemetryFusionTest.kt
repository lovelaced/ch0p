package io.ch0p.ingest.telemetry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetryFusionTest {

    @Test fun `energy maps to the right bins and forward-fills`() {
        // 10s clip, 10 bins (1s each). Energy 1.0 at 0ms, 0.5 at 5000ms.
        val grid = TelemetryFusion.toGrid(
            energy = floatArrayOf(1.0f, 0.5f),
            timesMs = longArrayOf(0, 5000),
            n = 10, durationMs = 10_000,
        )
        assertEquals(10, grid.size)
        assertEquals(1.0f, grid[0], 1e-4f)   // bin 0
        assertEquals(1.0f, grid[4], 1e-4f)   // forward-filled until the 5s mark
        assertEquals(0.5f, grid[5], 1e-4f)   // bin 5 (5000ms)
        assertEquals(0.5f, grid[9], 1e-4f)   // forward-filled to the end
    }

    @Test fun `highlight mark creates a peak at its position`() {
        val curve = TelemetryFusion.highlightCurve(listOf(5000L), n = 10, durationMs = 10_000, halfWidthMs = 1000)
        assertEquals(1.0f, curve[5], 1e-4f)            // peak at the mark
        assertTrue("decays away from mark", curve[5] > curve[3])
        assertEquals(0f, curve[0], 1e-4f)              // far from any mark
    }

    @Test fun `boost reinforces and clamps`() {
        val base = floatArrayOf(0.2f, 0.9f, 0.0f)
        val add = floatArrayOf(1.0f, 1.0f, 0.5f)
        val out = TelemetryFusion.boost(base, add, weight = 0.5f)
        assertEquals(0.7f, out[0], 1e-4f)
        assertEquals(1.0f, out[1], 1e-4f)  // clamped
        assertEquals(0.25f, out[2], 1e-4f)
    }

    @Test fun `empty telemetry is harmless`() {
        assertTrue(TelemetryFusion.toGrid(FloatArray(0), LongArray(0), 5, 5000).all { it == 0f })
        assertTrue(TelemetryFusion.highlightCurve(emptyList(), 5, 5000).all { it == 0f })
    }
}
