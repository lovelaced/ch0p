package io.ch0p.render

import android.media.MediaCodec
import android.media.MediaMuxer
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Metadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.MediaFormatUtil
import androidx.media3.common.util.UnstableApi
import androidx.media3.muxer.BufferInfo
import androidx.media3.muxer.Muxer
import androidx.media3.muxer.MuxerException
import com.google.common.collect.ImmutableList
import java.nio.ByteBuffer

/**
 * WebM output muxer for Media3 Transformer — wraps Android's MediaMuxer in WEBM mode
 * (Media3's own muxers are MP4-only). Mirrors FrameworkMuxer: defers start() to the first
 * sample (all tracks are registered first), converts the Media3 Format/BufferInfo to the
 * framework types. Pair with VP9 video + Opus audio encoders. Device-gated on VP9 encode.
 */
@UnstableApi
class WebmMuxer private constructor(path: String) : Muxer {

    private val mediaMuxer = MediaMuxer(path, MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM)
    private val frameworkInfo = MediaCodec.BufferInfo()
    private var started = false

    override fun addTrack(format: Format): Int {
        val mediaFormat = MediaFormatUtil.createMediaFormatFromFormat(format)
        if (MimeTypes.isVideo(format.sampleMimeType)) {
            mediaMuxer.setOrientationHint(format.rotationDegrees)
        }
        return mediaMuxer.addTrack(mediaFormat)
    }

    override fun writeSampleData(trackId: Int, byteBuffer: ByteBuffer, bufferInfo: BufferInfo) {
        if (!started) { mediaMuxer.start(); started = true }
        var flags = 0
        if (bufferInfo.flags and C.BUFFER_FLAG_KEY_FRAME != 0) flags = flags or MediaCodec.BUFFER_FLAG_KEY_FRAME
        if (bufferInfo.flags and C.BUFFER_FLAG_END_OF_STREAM != 0) flags = flags or MediaCodec.BUFFER_FLAG_END_OF_STREAM
        frameworkInfo.set(byteBuffer.position(), bufferInfo.size, bufferInfo.presentationTimeUs, flags)
        mediaMuxer.writeSampleData(trackId, byteBuffer, frameworkInfo)
    }

    override fun addMetadataEntry(metadataEntry: Metadata.Entry) {
        // WebM container: skip arbitrary metadata entries.
    }

    override fun close() {
        try {
            if (started) mediaMuxer.stop()
        } finally {
            mediaMuxer.release()
        }
    }

    class Factory : Muxer.Factory {
        override fun create(path: String): Muxer =
            try {
                WebmMuxer(path)
            } catch (e: Exception) {
                throw MuxerException("Could not create WebM muxer", e)
            }

        override fun getSupportedSampleMimeTypes(trackType: Int): ImmutableList<String> =
            when (trackType) {
                C.TRACK_TYPE_VIDEO -> ImmutableList.of(MimeTypes.VIDEO_VP9, MimeTypes.VIDEO_VP8)
                C.TRACK_TYPE_AUDIO -> ImmutableList.of(MimeTypes.AUDIO_OPUS, MimeTypes.AUDIO_VORBIS)
                else -> ImmutableList.of()
            }
    }
}
