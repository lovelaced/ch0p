package io.ch0p.ingest

import android.media.MediaCodecList
import android.media.MediaFormat

/**
 * Checks whether the device can hardware-decode a given source and flags risky inputs.
 * Camera footage is frequently 10-bit HEVC / 4:2:2 / high-bitrate, which phone decoders
 * handle inconsistently; we surface that so the pipeline can transcode a safe working copy.
 */
object CodecSupport {

    private val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)

    fun canDecode(mime: String, width: Int, height: Int): Boolean {
        if (width <= 0 || height <= 0) return false
        val format = MediaFormat.createVideoFormat(mime, width, height)
        return runCatching { codecList.findDecoderForFormat(format) != null }.getOrDefault(false)
    }

    /** Whether the device has a hardware/software encoder for [mime] (e.g. VP9 for WebM out). */
    fun canEncode(mime: String): Boolean = runCatching {
        codecList.codecInfos.any { it.isEncoder && it.supportedTypes.any { t -> t.equals(mime, ignoreCase = true) } }
    }.getOrDefault(false)

    fun assessRisk(meta: VideoMetadata): IngestRisk {
        val reasons = mutableListOf<String>()
        var level = IngestRisk.Level.OK

        if (meta.codec == VideoCodec.OTHER || meta.mimeType == null) {
            return IngestRisk(IngestRisk.Level.UNSUPPORTED, listOf("Unrecognized video codec"))
        }

        val decodable = canDecode(meta.mimeType, meta.width, meta.height)
        if (!decodable) {
            reasons += "No hardware decoder for ${meta.mimeType} at ${meta.width}×${meta.height}"
            level = IngestRisk.Level.UNSUPPORTED
        }

        if (meta.bitDepth >= 10) {
            reasons += "10-bit ${meta.codec.name}: HW decode support varies by device"
            if (level == IngestRisk.Level.OK) level = IngestRisk.Level.CAUTION
        }

        if (maxOf(meta.width, meta.height) > 4096) {
            reasons += "Above-4K resolution may exceed decoder limits"
            if (level == IngestRisk.Level.OK) level = IngestRisk.Level.CAUTION
        }

        return IngestRisk(level, reasons)
    }
}
