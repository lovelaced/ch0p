package io.ch0p.edit.reframe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ReframeTest {

    @Test fun `one euro filter converges to a constant input`() {
        val f = OneEuroFilter(minCutoff = 1.0, beta = 0.01)
        var out = 0.0
        for (i in 0..50) out = f.filter(0.7, i * 0.033)
        assertEquals(0.7, out, 1e-3)
    }

    @Test fun `one euro filter lags a step (smooths jitter)`() {
        val f = OneEuroFilter(minCutoff = 1.0, beta = 0.0)
        f.filter(0.0, 0.0)
        val firstStep = f.filter(1.0, 0.033)
        assertTrue("should not jump straight to the new value", firstStep < 1.0)
        assertTrue(firstStep > 0.0)
    }

    @Test fun `trajectory smooths jittery targets`() {
        val targets = (0..30).map { i ->
            val jitter = if (i % 2 == 0) 0.02 else -0.02
            SubjectTarget(i * 0.1, 0.5 + jitter, 0.5, 0.6)
        }
        val traj = CropTrajectory.build(targets, cutsSec = emptyList())
        assertEquals(targets.size, traj.size)
        // Smoothed center should sit near 0.5 with less variance than the raw jitter.
        val maxDev = traj.drop(5).maxOf { abs(it.cx - 0.5) }
        assertTrue("smoothed deviation $maxDev should be under the 0.02 raw jitter", maxDev < 0.02)
    }

    @Test fun `pan speed is clamped`() {
        // Target jumps from left to right instantly; trajectory must ramp, not teleport.
        val targets = listOf(
            SubjectTarget(0.0, 0.1, 0.5, 0.6),
            SubjectTarget(0.1, 0.9, 0.5, 0.6),
        )
        val traj = CropTrajectory.build(targets, emptyList(), CropTrajectory.Follow.DEFAULT)
        val step = abs(traj[1].cx - traj[0].cx)
        assertTrue("pan step $step must respect the speed clamp", step <= CropTrajectory.Follow.DEFAULT.maxPanPerSec * 0.1 + 1e-6)
    }

    @Test fun `empty targets yield empty trajectory`() {
        assertTrue(CropTrajectory.build(emptyList(), emptyList()).isEmpty())
    }
}
