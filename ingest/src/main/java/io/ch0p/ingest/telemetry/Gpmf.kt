package io.ch0p.ingest.telemetry

import kotlin.math.sqrt

/**
 * Parser for GPMF (GoPro Metadata Format) — the KLV-structured telemetry GoPro/DJI/Insta360
 * cameras embed in a `gpmd` track. Pure Kotlin, big-endian, host/JVM-testable.
 *
 * KLV layout per node:
 *   key:   4 ASCII chars (FourCC)
 *   type:  1 byte (e.g. 's'=int16, 'l'=int32, 'f'=float, 'L'=uint32, 0=nested container)
 *   size:  1 byte — bytes per sample element ("structure size")
 *   count: 2 bytes BE — number of samples
 *   data:  size*count bytes, then padded to a 4-byte boundary
 *
 * Containers (type 0, e.g. DEVC/STRM) hold a nested KLV stream as their payload.
 */
object Gpmf {

    data class Node(
        val key: String,
        val type: Char,
        val structSize: Int,
        val repeat: Int,
        val bytes: ByteArray,        // backing buffer
        val dataOffset: Int,         // start of this node's payload
        val children: List<Node>,
    ) {
        val payloadLen: Int get() = structSize * repeat

        /** Big-endian int16 samples. */
        fun int16s(): ShortArray {
            val n = payloadLen / 2
            return ShortArray(n) { i ->
                val o = dataOffset + i * 2
                (((bytes[o].toInt() and 0xFF) shl 8) or (bytes[o + 1].toInt() and 0xFF)).toShort()
            }
        }

        /** Big-endian int32 samples. */
        fun int32s(): IntArray {
            val n = payloadLen / 4
            return IntArray(n) { i ->
                val o = dataOffset + i * 4
                ((bytes[o].toInt() and 0xFF) shl 24) or ((bytes[o + 1].toInt() and 0xFF) shl 16) or
                    ((bytes[o + 2].toInt() and 0xFF) shl 8) or (bytes[o + 3].toInt() and 0xFF)
            }
        }

        fun find(key: String): Node? =
            if (this.key == key) this else children.firstNotNullOfOrNull { it.find(key) }

        fun findAll(key: String, into: MutableList<Node> = mutableListOf()): List<Node> {
            if (this.key == key) into.add(this)
            children.forEach { it.findAll(key, into) }
            return into
        }
    }

    /** Parse a KLV stream from [start, end). */
    fun parse(bytes: ByteArray, start: Int = 0, end: Int = bytes.size): List<Node> {
        val nodes = ArrayList<Node>()
        var pos = start
        while (pos + 8 <= end) {
            val key = String(bytes, pos, 4, Charsets.US_ASCII)
            val type = bytes[pos + 4].toInt().toChar()
            val structSize = bytes[pos + 5].toInt() and 0xFF
            val repeat = ((bytes[pos + 6].toInt() and 0xFF) shl 8) or (bytes[pos + 7].toInt() and 0xFF)
            val dataOffset = pos + 8
            val payloadLen = structSize * repeat
            if (dataOffset + payloadLen > end) break  // malformed/truncated

            val children = if (type.code == 0 && payloadLen > 0) {
                parse(bytes, dataOffset, dataOffset + payloadLen)
            } else emptyList()

            nodes.add(Node(key, type, structSize, repeat, bytes, dataOffset, children))
            // Advance past payload, 4-byte aligned.
            val padded = (payloadLen + 3) and 3.inv()
            pos = dataOffset + padded
        }
        return nodes
    }

    /**
     * Per-sample accelerometer magnitude (m/s² after SCAL), across all DEVC/STRM blocks in
     * one GPMF blob. This is the core "physical action energy" signal.
     */
    fun acclMagnitudes(blob: ByteArray): FloatArray {
        val out = ArrayList<Float>()
        for (top in parse(blob)) {
            for (strm in top.findAll("STRM")) {
                val accl = strm.find("ACCL") ?: continue
                val scal = strm.find("SCAL")?.let { firstScalar(it) } ?: 1f
                val v = accl.int16s()
                val axes = if (accl.structSize > 0) accl.structSize / 2 else 3
                var i = 0
                while (i + axes <= v.size) {
                    var sumSq = 0f
                    for (a in 0 until axes) {
                        val x = v[i + a] / scal
                        sumSq += x * x
                    }
                    out.add(sqrt(sumSq))
                    i += axes
                }
            }
        }
        return out.toFloatArray()
    }

    private fun firstScalar(scal: Node): Float = when (scal.type) {
        's', 'S' -> scal.int16s().firstOrNull()?.toFloat() ?: 1f
        'l', 'L' -> scal.int32s().firstOrNull()?.toFloat() ?: 1f
        else -> 1f
    }.let { if (it == 0f) 1f else it }
}
