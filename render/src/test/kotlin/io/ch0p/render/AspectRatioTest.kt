package io.ch0p.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AspectRatioTest {

    @Test fun `common ratios parse`() {
        assertEquals(9f / 16f, AspectRatio.parseOrNull("9:16")!!, 1e-4f)
        assertEquals(16f / 9f, AspectRatio.parseOrNull("16:9")!!, 1e-4f)
        assertEquals(1f, AspectRatio.parseOrNull("1:1")!!, 1e-4f)
        assertEquals(2.39f, AspectRatio.parseOrNull("2.39:1")!!, 1e-4f)
    }

    @Test fun `malformed input is rejected`() {
        assertNull(AspectRatio.parseOrNull("16-9"))
        assertNull(AspectRatio.parseOrNull("16"))
        assertNull(AspectRatio.parseOrNull("a:b"))
        assertNull(AspectRatio.parseOrNull("0:1"))
    }

    @Test fun `default applies on bad input`() {
        assertEquals(9f / 16f, AspectRatio.parseOrDefault("nonsense"), 1e-4f)
    }
}
