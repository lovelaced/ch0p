package io.ch0p.ingest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxySpecTest {

    @Test fun `4K landscape downscales to 720p class with even dims`() {
        val s = ProxySpec.forSource(3840, 2160)
        assertEquals(1280, s.width)
        assertEquals(720, s.height)
        assertEven(s)
    }

    @Test fun `4K portrait keeps long edge capped`() {
        val s = ProxySpec.forSource(2160, 3840)
        assertEquals(1280, maxOf(s.width, s.height))
        assertEquals(720, minOf(s.width, s.height))
        assertEven(s)
    }

    @Test fun `does not upscale small sources`() {
        val s = ProxySpec.forSource(640, 360)
        assertEquals(640, s.width)
        assertEquals(360, s.height)
    }

    @Test fun `odd source dimensions are rounded to even`() {
        val s = ProxySpec.forSource(1921, 1081)
        assertEven(s)
    }

    @Test fun `bitrate stays within sane bounds`() {
        val low = ProxySpec.forSource(320, 240, fps = 24.0)
        val high = ProxySpec.forSource(7680, 4320, fps = 60.0)
        assertTrue(low.bitrate >= 1_500_000)
        assertTrue(high.bitrate <= 8_000_000)
    }

    @Test fun `invalid input falls back to a usable default`() {
        val s = ProxySpec.forSource(0, 0)
        assertTrue(s.width > 0 && s.height > 0 && s.bitrate > 0)
    }

    private fun assertEven(s: ProxySpec) {
        assertEquals(0, s.width % 2)
        assertEquals(0, s.height % 2)
    }
}
