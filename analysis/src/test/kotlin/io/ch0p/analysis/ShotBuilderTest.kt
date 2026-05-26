package io.ch0p.analysis

import io.ch0p.edit.Shot
import org.junit.Assert.assertEquals
import org.junit.Test

class ShotBuilderTest {

    @Test fun `cuts become contiguous shots spanning the duration`() {
        val shots = ShotBuilder.fromCuts(doubleArrayOf(5.0, 8.0), 10_000)
        assertEquals(
            listOf(Shot(0, 5_000), Shot(5_000, 8_000), Shot(8_000, 10_000)),
            shots,
        )
    }

    @Test fun `no cuts yields a single shot`() {
        assertEquals(listOf(Shot(0, 12_000)), ShotBuilder.fromCuts(doubleArrayOf(), 12_000))
    }

    @Test fun `out-of-range and duplicate cuts are ignored`() {
        val shots = ShotBuilder.fromCuts(doubleArrayOf(-1.0, 0.0, 5.0, 5.0, 99.0), 10_000)
        assertEquals(listOf(Shot(0, 5_000), Shot(5_000, 10_000)), shots)
    }

    @Test fun `zero duration yields nothing`() {
        assertEquals(emptyList<Shot>(), ShotBuilder.fromCuts(doubleArrayOf(1.0), 0))
    }
}
