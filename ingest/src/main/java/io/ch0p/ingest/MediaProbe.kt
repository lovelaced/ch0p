package io.ch0p.ingest

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Reads source-video properties from a content URI (SAF or MediaStore). */
object MediaProbe {

    fun probe(context: Context, uri: Uri): VideoMetadata {
        val (name, size) = queryNameAndSize(context, uri)
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            fun meta(key: Int): String? = retriever.extractMetadata(key)

            val durationMs = meta(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val width = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val height = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rotation = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            val bitrate = meta(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull() ?: 0
            val hasAudio = meta(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes"
            val captureFps = meta(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toDoubleOrNull()
            val captureTime = parseDate(meta(MediaMetadataRetriever.METADATA_KEY_DATE))

            val track = probeVideoTrack(context, uri)

            return VideoMetadata(
                displayName = name,
                sizeBytes = size,
                durationMs = durationMs,
                width = width,
                height = height,
                rotationDegrees = ((rotation % 360) + 360) % 360,
                frameRate = track.frameRate ?: captureFps ?: 30.0,
                bitrate = bitrate,
                mimeType = track.mime,
                codec = codecOf(track.mime),
                bitDepth = track.bitDepth,
                hasAudio = hasAudio,
                captureTimeUtcMs = captureTime,
            )
        } finally {
            runCatching { retriever.release() }
        }
    }

    private data class TrackInfo(val mime: String?, val frameRate: Double?, val bitDepth: Int)

    private fun probeVideoTrack(context: Context, uri: Uri): TrackInfo {
        val extractor = MediaExtractor()
        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                extractor.setDataSource(pfd.fileDescriptor)
            } ?: return TrackInfo(null, null, 8)

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (!mime.startsWith("video/")) continue
                val fps = when {
                    format.containsKey(MediaFormat.KEY_FRAME_RATE) ->
                        runCatching { format.getInteger(MediaFormat.KEY_FRAME_RATE).toDouble() }
                            .getOrElse { runCatching { format.getFloat(MediaFormat.KEY_FRAME_RATE).toDouble() }.getOrNull() }
                    else -> null
                }
                return TrackInfo(mime, fps, bitDepthOf(format))
            }
        } catch (_: Exception) {
            // fall through to default
        } finally {
            runCatching { extractor.release() }
        }
        return TrackInfo(null, null, 8)
    }

    /** Best-effort 10-bit detection via codec profile. */
    private fun bitDepthOf(format: MediaFormat): Int {
        if (!format.containsKey(MediaFormat.KEY_PROFILE)) return 8
        val profile = runCatching { format.getInteger(MediaFormat.KEY_PROFILE) }.getOrNull() ?: return 8
        // HEVCProfileMain10 = 2, HEVCProfileMain10HDR10 = 0x1000, AV1Profile* main10 variants are high bits.
        return if (profile == 2 || profile >= 0x1000) 10 else 8
    }

    private fun codecOf(mime: String?): VideoCodec = when (mime) {
        "video/avc" -> VideoCodec.H264
        "video/hevc" -> VideoCodec.HEVC
        "video/av01" -> VideoCodec.AV1
        "video/x-vnd.on2.vp9" -> VideoCodec.VP9
        "video/x-vnd.on2.vp8" -> VideoCodec.VP8
        else -> VideoCodec.OTHER
    }

    private fun queryNameAndSize(context: Context, uri: Uri): Pair<String?, Long> {
        var name: String? = null
        var size = 0L
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = c.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0) name = c.getString(nameIdx)
                    if (sizeIdx >= 0 && !c.isNull(sizeIdx)) size = c.getLong(sizeIdx)
                }
            }
        }
        return name to size
    }

    private fun parseDate(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        val patterns = listOf("yyyyMMdd'T'HHmmss.SSS'Z'", "yyyyMMdd'T'HHmmss'Z'", "yyyy-MM-dd HH:mm:ss")
        for (p in patterns) {
            runCatching {
                val fmt = SimpleDateFormat(p, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
                return fmt.parse(raw)?.time
            }
        }
        return null
    }
}
