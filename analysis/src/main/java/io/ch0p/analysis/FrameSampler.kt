package io.ch0p.analysis

import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.media.Image
import android.media.ImageReader
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.nio.ByteBuffer

/**
 * Decodes a video's frames in a single forward pass and emits roughly one frame per [stepMs],
 * scaled to [dstW]×[dstH] RGBA.
 *
 * Unlike `MediaMetadataRetriever.getScaledFrameAtTime(…, OPTION_CLOSEST_SYNC)` — which snaps
 * every request to the nearest *keyframe*, so densely-sampled frames are duplicates and the
 * motion/scene-cut signals computed from them are garbage — this yields TRUE consecutive frames
 * and decodes the stream once instead of re-seeking per sample. Device-only (MediaCodec).
 *
 * Returns the number of frames emitted; callers fall back to the retriever path on 0.
 */
object FrameSampler {

    private const val TIMEOUT_US = 10_000L

    fun sample(path: String, dstW: Int, dstH: Int, stepMs: Long, onFrame: (Bitmap, Long) -> Unit): Int {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        var reader: ImageReader? = null
        var emitted = 0
        try {
            extractor.setDataSource(path)
            var track = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                if ((f.getString(MediaFormat.KEY_MIME) ?: "").startsWith("video/")) { track = i; format = f; break }
            }
            if (track < 0 || format == null) return 0
            extractor.selectTrack(track)
            val mime = format.getString(MediaFormat.KEY_MIME)!!

            reader = ImageReader.newInstance(dstW, dstH, PixelFormat.RGBA_8888, 3)
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, reader.surface, null, 0)
            codec.start()

            val info = MediaCodec.BufferInfo()
            var sawInputEos = false
            var sawOutputEos = false
            var nextSampleMs = 0L

            while (!sawOutputEos) {
                if (!sawInputEos) {
                    val inIdx = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inIdx >= 0) {
                        val buf = codec.getInputBuffer(inIdx)!!
                        val sz = extractor.readSampleData(buf, 0)
                        if (sz < 0) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            sawInputEos = true
                        } else {
                            codec.queueInputBuffer(inIdx, 0, sz, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIdx = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                if (outIdx >= 0) {
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) sawOutputEos = true
                    val ptsMs = info.presentationTimeUs / 1000
                    val render = info.size > 0 && ptsMs >= nextSampleMs
                    codec.releaseOutputBuffer(outIdx, render)   // render=true → pushes to the reader surface
                    if (render) {
                        acquire(reader)?.let { image ->
                            try {
                                onFrame(imageToBitmap(image, dstW, dstH), ptsMs)
                                emitted++
                            } finally { image.close() }
                        }
                        nextSampleMs = maxOf(nextSampleMs + stepMs, ptsMs + 1)
                    }
                }
            }
        } catch (_: Throwable) {
            // any decoder/extractor failure → caller falls back to the retriever path
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { reader?.close() }
            runCatching { extractor.release() }
        }
        return emitted
    }

    /** Rendering to the surface is async; poll the reader briefly for the produced image. */
    private fun acquire(reader: ImageReader): Image? {
        repeat(60) {
            reader.acquireNextImage()?.let { return it }
            Thread.sleep(1)
        }
        return null
    }

    private fun imageToBitmap(image: Image, w: Int, h: Int): Bitmap {
        val plane = image.planes[0]
        val rowStride = plane.rowStride
        val src = plane.buffer
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        if (rowStride == w * 4) {
            bmp.copyPixelsFromBuffer(src)
        } else {
            // Strip per-row padding the decoder may add (rowStride > width*4).
            val tight = ByteBuffer.allocateDirect(w * h * 4)
            val row = ByteArray(w * 4)
            for (y in 0 until h) {
                src.position(y * rowStride)
                src.get(row, 0, w * 4)
                tight.put(row)
            }
            tight.rewind()
            bmp.copyPixelsFromBuffer(tight)
        }
        return bmp
    }
}
