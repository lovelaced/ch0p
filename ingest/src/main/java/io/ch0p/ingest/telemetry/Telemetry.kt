package io.ch0p.ingest.telemetry

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer

/** Embedded camera telemetry distilled into editing signals. */
data class Telemetry(
    val highlightMarksMs: List<Long>,   // operator-pressed HiLight tags
    val accelEnergy: FloatArray,        // per-sample physical-action energy, 0..1
    val sampleTimesMs: LongArray,       // timestamp of each accelEnergy entry
    val source: String,
) {
    val hasData: Boolean get() = highlightMarksMs.isNotEmpty() || accelEnergy.isNotEmpty()
}

/** Seekable byte source so the same moov scan works for a File path or a SAF content URI. */
private interface RandomSource {
    fun size(): Long
    fun readAt(pos: Long, dst: ByteArray, len: Int)
    fun close()
}

/**
 * Extracts telemetry from a camera MP4: HiLight tags from `moov/udta/HMMT`, and an
 * accelerometer-derived action curve from the GPMF `gpmd` track. Works on *speechless*
 * footage where transcript-based tools fail. Must read the ORIGINAL (a transcoded proxy
 * drops the gpmd track). Device path; the parsing it delegates to is pure + unit-tested.
 */
object TelemetryExtractor {

    private const val MAX_MOOV_BYTES = 64L * 1024 * 1024

    fun extract(path: String): Telemetry? {
        val src = object : RandomSource {
            val raf = RandomAccessFile(path, "r")
            override fun size() = raf.length()
            override fun readAt(pos: Long, dst: ByteArray, len: Int) { raf.seek(pos); raf.readFully(dst, 0, len) }
            override fun close() = raf.close()
        }
        val gpmd = { MediaExtractor().apply { setDataSource(path) } }
        return build(src, gpmd)
    }

    fun extract(context: Context, uri: Uri): Telemetry? {
        val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
        val channel = FileInputStream(pfd.fileDescriptor).channel
        val src = object : RandomSource {
            override fun size() = channel.size()
            override fun readAt(pos: Long, dst: ByteArray, len: Int) {
                val bb = ByteBuffer.wrap(dst, 0, len)
                var p = pos
                while (bb.hasRemaining()) {
                    val r = channel.read(bb, p)
                    if (r < 0) break
                    p += r
                }
            }
            override fun close() { runCatching { channel.close() }; runCatching { pfd.close() } }
        }
        val gpmd = { MediaExtractor().apply { setDataSource(context, uri, null) } }
        return build(src, gpmd)
    }

    private fun build(src: RandomSource, gpmd: () -> MediaExtractor): Telemetry? {
        val highlights = runCatching { readHiLights(src) }.getOrDefault(emptyList())
        val (energy, times) = runCatching { readGpmdAccel(gpmd()) }.getOrDefault(FloatArray(0) to LongArray(0))
        runCatching { src.close() }
        val telem = Telemetry(highlights, energy, times, source = "GPMF")
        return if (telem.hasData) telem else null
    }

    private fun readHiLights(src: RandomSource): List<Long> {
        val len = src.size()
        var pos = 0L
        val header = ByteArray(16)
        while (pos + 8 <= len) {
            src.readAt(pos, header, 8)
            var size = Mp4Boxes.u32(header, 0)
            val type = String(header, 4, 4, Charsets.US_ASCII)
            var headerSize = 8L
            if (size == 1L) {
                src.readAt(pos + 8, header, 8); // largesize in bytes 8..16 of our buffer
                size = Mp4Boxes.u64(header, 8); headerSize = 16L
            } else if (size == 0L) {
                size = len - pos
            }
            if (size <= 0 || pos + size > len) break
            if (type == "moov" && size <= MAX_MOOV_BYTES) {
                val moov = ByteArray((size - headerSize).toInt())
                src.readAt(pos + headerSize, moov, moov.size)
                return HiLightTags.fromMoov(moov)
            }
            pos += size
        }
        return emptyList()
    }

    private fun readGpmdAccel(extractor: MediaExtractor): Pair<FloatArray, LongArray> {
        try {
            val track = (0 until extractor.trackCount).firstOrNull { i ->
                val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: ""
                mime.contains("gpm", ignoreCase = true) || mime.contains("gopro", ignoreCase = true)
            } ?: return FloatArray(0) to LongArray(0)

            extractor.selectTrack(track)
            val energies = ArrayList<Float>()
            val times = ArrayList<Long>()
            val buf = ByteBuffer.allocate(1 shl 20)
            while (true) {
                buf.clear()
                val size = extractor.readSampleData(buf, 0)
                if (size < 0) break
                val blob = ByteArray(size)
                buf.get(blob, 0, size)
                val mags = Gpmf.acclMagnitudes(blob)
                if (mags.isNotEmpty()) {
                    energies.add(mags.average().toFloat())
                    times.add(extractor.sampleTime / 1000)
                }
                if (!extractor.advance()) break
            }
            return normalize(energies.toFloatArray()) to times.toLongArray()
        } finally {
            runCatching { extractor.release() }
        }
    }

    private fun normalize(a: FloatArray): FloatArray {
        if (a.isEmpty()) return a
        val max = a.max()
        return if (max <= 0f) a else FloatArray(a.size) { a[it] / max }
    }
}
