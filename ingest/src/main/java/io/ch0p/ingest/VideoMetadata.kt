package io.ch0p.ingest

/** Probed properties of a source video, before any work copy or proxy is made. */
data class VideoMetadata(
    val displayName: String?,
    val sizeBytes: Long,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val rotationDegrees: Int,
    val frameRate: Double,
    val bitrate: Int,
    val mimeType: String?,        // container/video mime, e.g. video/hevc
    val codec: VideoCodec,
    val bitDepth: Int,            // 8 or 10 (best-effort)
    val hasAudio: Boolean,
    val captureTimeUtcMs: Long?,  // from container metadata if present
) {
    /** Width/height after applying rotation — the displayed orientation. */
    val orientedWidth: Int get() = if (rotationDegrees % 180 == 0) width else height
    val orientedHeight: Int get() = if (rotationDegrees % 180 == 0) height else width

    val isPortrait: Boolean get() = orientedHeight > orientedWidth
}

enum class VideoCodec { H264, HEVC, AV1, VP9, OTHER }

/**
 * How risky this input is to decode on-device. Cameras commonly emit 10-bit HEVC and
 * 4:2:2 which phone HW decoders handle inconsistently — surfaced so the UI can warn and
 * the pipeline can fall back to a transcoded working copy.
 */
data class IngestRisk(
    val level: Level,
    val reasons: List<String>,
) {
    enum class Level { OK, CAUTION, UNSUPPORTED }
    val isUsable: Boolean get() = level != Level.UNSUPPORTED
}
