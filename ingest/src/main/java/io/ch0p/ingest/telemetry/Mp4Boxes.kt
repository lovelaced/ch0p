package io.ch0p.ingest.telemetry

/**
 * Minimal ISO-BMFF (MP4/MOV) box reader over an in-memory buffer. Used to locate small
 * metadata boxes like `moov/udta/HMMT` (GoPro HiLight tags). Pure Kotlin, JVM-testable.
 * 32-bit and 64-bit box sizes supported; recursion is path-driven so no container table.
 */
object Mp4Boxes {

    data class Box(val type: String, val headerSize: Int, val start: Int, val end: Int) {
        val payloadStart: Int get() = start + headerSize
        val payloadEnd: Int get() = end
    }

    /** Boxes directly within [start, end). */
    fun children(bytes: ByteArray, start: Int, end: Int): List<Box> {
        val boxes = ArrayList<Box>()
        var pos = start
        while (pos + 8 <= end) {
            val size32 = u32(bytes, pos)
            val type = String(bytes, pos + 4, 4, Charsets.US_ASCII)
            var headerSize = 8
            var boxEnd: Int
            when (size32) {
                1L -> { // 64-bit largesize
                    val large = u64(bytes, pos + 8)
                    headerSize = 16
                    boxEnd = (pos + large).toInt()
                }
                0L -> boxEnd = end       // extends to container end
                else -> boxEnd = pos + size32.toInt()
            }
            if (boxEnd <= pos || boxEnd > end) break
            boxes.add(Box(type, headerSize, pos, boxEnd))
            pos = boxEnd
        }
        return boxes
    }

    /** Resolve a slash path like "moov/udta/HMMT" to its box, or null. */
    fun findByPath(bytes: ByteArray, path: String, start: Int = 0, end: Int = bytes.size): Box? {
        val parts = path.split("/")
        var rangeStart = start
        var rangeEnd = end
        var found: Box? = null
        for (part in parts) {
            found = children(bytes, rangeStart, rangeEnd).firstOrNull { it.type == part } ?: return null
            rangeStart = found.payloadStart
            rangeEnd = found.payloadEnd
        }
        return found
    }

    fun u32(b: ByteArray, o: Int): Long =
        ((b[o].toLong() and 0xFF) shl 24) or ((b[o + 1].toLong() and 0xFF) shl 16) or
            ((b[o + 2].toLong() and 0xFF) shl 8) or (b[o + 3].toLong() and 0xFF)

    fun u64(b: ByteArray, o: Int): Long {
        var v = 0L
        for (i in 0 until 8) v = (v shl 8) or (b[o + i].toLong() and 0xFF)
        return v
    }
}
