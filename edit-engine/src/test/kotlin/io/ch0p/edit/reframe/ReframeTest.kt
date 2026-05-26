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

    @Test fun `reframe params center the subject`() {
        val centered = Reframe.params(0.5f, 0.5f)
        assertEquals(0f, centered.tx, 1e-4f)
        assertEquals(0f, centered.ty, 1e-4f)
        // subject on the right -> pan left (negative tx), clamped
        val right = Reframe.params(1.0f, 0.5f)
        assertTrue(right.tx < 0f)
        assertTrue(right.tx >= -Reframe.MAX_PAN_X - 1e-4f)
        // subject on the left -> pan right
        assertTrue(Reframe.params(0.0f, 0.5f).tx > 0f)
    }

    @Test fun `sampleAt interpolates between keyframes`() {
        val kfs = listOf(
            CropKeyframe(0.0, 0.2, 0.5, 0.6),
            CropKeyframe(2.0, 0.8, 0.5, 0.6),
        )
        assertEquals(0.5, CropTrajectory.sampleAt(kfs, 1.0)!!.cx, 1e-6)   // midpoint
        assertEquals(0.2, CropTrajectory.sampleAt(kfs, -1.0)!!.cx, 1e-6)  // clamp start
        assertEquals(0.8, CropTrajectory.sampleAt(kfs, 9.0)!!.cx, 1e-6)   // clamp end
        assertEquals(null, CropTrajectory.sampleAt(emptyList(), 1.0))
    }
}
