package io.ch0p.ingest.telemetry

/**
 * Extracts GoPro HiLight tags — the moments the operator manually marked while filming —
 * from the `moov/udta/HMMT` box. These are the highest-value highlight signal: an explicit
 * human "this bit matters" with zero inference.
 *
 * HMMT payload layout: [uint32 count][uint32 timestampMs] * count (big-endian).
 * Pure Kotlin, JVM-testable.
 */
object HiLightTags {

    /** Parse from a full `moov` buffer. */
    fun fromMoov(moov: ByteArray): List<Long> {
        val box = Mp4Boxes.findByPath(moov, "udta/HMMT") ?: return emptyList()
        return parsePayload(moov, box.payloadStart, box.payloadEnd)
    }

    /** Parse the HMMT payload region [start, end). */
    fun parsePayload(bytes: ByteArray, start: Int, end: Int): List<Long> {
        if (end - start < 4) return emptyList()
        val declared = Mp4Boxes.u32(bytes, start).toInt()
        val available = (end - start - 4) / 4
        val count = declared.coerceIn(0, available)
        val marks = ArrayList<Long>(count)
        for (i in 0 until count) {
            val ms = Mp4Boxes.u32(bytes, start + 4 + i * 4)
            if (ms > 0) marks.add(ms)
        }
        return marks.sorted()
    }
}
