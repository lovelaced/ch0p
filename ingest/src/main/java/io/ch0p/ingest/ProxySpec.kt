package io.ch0p.ingest

import kotlin.math.roundToInt

/**
 * Target dimensions + bitrate for the low-res proxy used by analysis and preview.
 *
 * Pure math (no Android types) so it is unit-testable on the JVM. Never upscales,
 * preserves aspect ratio, and forces even dimensions (required by most encoders).
 */
data class ProxySpec(val width: Int, val height: Int, val bitrate: Int) {
    companion object {
        // Long-edge cap. 1280 → 720p-class proxy (1280×720 from 16:9, 720×1280 from 9:16).
        const val DEFAULT_LONG_EDGE = 1280

        fun forSource(
            srcWidth: Int,
            srcHeight: Int,
            targetLongEdge: Int = DEFAULT_LONG_EDGE,
            fps: Double = 30.0,
        ): ProxySpec {
            if (srcWidth <= 0 || srcHeight <= 0) {
                return ProxySpec(targetLongEdge, even((targetLongEdge * 9 / 16)), 4_000_000)
            }
            val longEdge = maxOf(srcWidth, srcHeight)
            val scale = if (longEdge > targetLongEdge) targetLongEdge.toDouble() / longEdge else 1.0
            val w = even((srcWidth * scale).roundToInt())
            val h = even((srcHeight * scale).roundToInt())
            val effFps = if (fps in 1.0..240.0) fps else 30.0
            val bitrate = (w.toLong() * h * effFps * BITS_PER_PIXEL)
                .toLong().coerceIn(MIN_BITRATE, MAX_BITRATE).toInt()
            return ProxySpec(w, h, bitrate)
        }

        private const val BITS_PER_PIXEL = 0.1
        private const val MIN_BITRATE = 1_500_000L
        private const val MAX_BITRATE = 8_000_000L

        private fun even(x: Int): Int = (x and 1.inv()).coerceAtLeast(2)
    }
}
