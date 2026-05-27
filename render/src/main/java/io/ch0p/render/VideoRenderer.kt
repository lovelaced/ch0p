package io.ch0p.render

import android.content.Context
import android.net.Uri
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.effect.TextureOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.google.common.collect.ImmutableList
import io.ch0p.edit.EditDecisionList
import io.ch0p.edit.captions.CaptionChunk
import io.ch0p.edit.reframe.CropKeyframe
import java.io.File
import java.util.UUID

/**
 * Renders an [EditDecisionList] to an MP4: each EDL entry becomes a clipped
 * [EditedMediaItem] taken from the full-res original, concatenated into one sequence and
 * center-cropped to the preset's aspect ratio. Hardware-encoded via Media3 Transformer.
 *
 * v1 uses hard cuts (Media3 has no built-in cross-clip transitions) and a static
 * aspect-crop; animated smart-reframe (MatrixTransformation trajectory) is a later
 * enhancement. Start/cancel on the main thread; the app wraps progress polling.
 */
@UnstableApi
class VideoRenderer(private val context: Context) {

    interface Callback {
        fun onComplete(output: File)
        fun onError(message: String, cause: Throwable?)
    }

    private var transformer: Transformer? = null

    private fun exportsDir(): File = File(context.filesDir, "exports").apply { mkdirs() }

    /**
     * @param aspect width/height (e.g. 9f/16f).
     * @param cropTrajectory optional smart-reframe path; when present each clip pans/zooms
     *   to follow the subject before the aspect-crop. Empty = static center-crop.
     */
    /** Container/codec for the export. WebM (VP9+Opus) needs a device VP9 encoder. */
    enum class OutputFormat(val ext: String) { MP4("mp4"), WEBM("webm") }

    fun start(
        sourceUri: Uri,
        edl: EditDecisionList,
        aspect: Float,
        cropTrajectory: List<CropKeyframe> = emptyList(),
        captionChunks: List<CaptionChunk> = emptyList(),
        outputFormat: OutputFormat = OutputFormat.MP4,
        musicUri: Uri? = null,
        duckEnvelope: FloatArray = FloatArray(0),
        duckEnvHz: Float = 20f,
        normalizeGain: Float = 1f,
        callback: Callback,
    ): File {
        require(edl.units.isNotEmpty()) { "empty EDL" }
        val outFile = File(exportsDir(), "${UUID.randomUUID()}_${edl.presetId}.${outputFormat.ext}")

        val presentation = Presentation.createForAspectRatio(
            aspect, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP,
        )

        val items: List<EditedMediaItem> = edl.units.map { entry ->
            val mediaItem = MediaItem.Builder()
                .setUri(sourceUri)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(entry.srcInMs)
                        .setEndPositionMs(entry.srcOutMs)
                        .build(),
                )
                .build()
            val videoEffects = buildList<Effect> {
                if (cropTrajectory.isNotEmpty()) add(ReframeEffect(cropTrajectory, entry.srcInMs))
                add(presentation)
                if (captionChunks.isNotEmpty()) {
                    add(OverlayEffect(ImmutableList.of<TextureOverlay>(CaptionOverlay(captionChunks, entry.srcInMs))))
                }
            }
            val audioFx = if (normalizeGain != 1f) {
                listOf<AudioProcessor>(NormalizeAudioProcessor(normalizeGain))
            } else {
                emptyList()
            }
            EditedMediaItem.Builder(mediaItem)
                .setEffects(Effects(audioFx, videoEffects))
                .build()
        }

        @Suppress("DEPRECATION")
        val videoSequence = EditedMediaItemSequence.Builder(items).build()

        // Optional user-picked music: a second, looping audio sequence ducked under speech.
        val composition = if (musicUri != null) {
            val music = EditedMediaItem.Builder(MediaItem.fromUri(musicUri))
                .setRemoveVideo(true)
                .setEffects(Effects(listOf<AudioProcessor>(DuckingAudioProcessor(duckEnvelope, duckEnvHz)), emptyList()))
                .build()
            @Suppress("DEPRECATION")
            val musicSequence = EditedMediaItemSequence.Builder(music).setIsLooping(true).build()
            Composition.Builder(videoSequence, musicSequence).build()
        } else {
            Composition.Builder(videoSequence).build()
        }

        val builder = Transformer.Builder(context)
        when (outputFormat) {
            OutputFormat.MP4 -> builder.setVideoMimeType(MimeTypes.VIDEO_H264)
            OutputFormat.WEBM -> builder
                .setVideoMimeType(MimeTypes.VIDEO_VP9)
                .setAudioMimeType(MimeTypes.AUDIO_OPUS)
                .setMuxerFactory(WebmMuxer.Factory())
        }
        val t = builder
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, result: ExportResult) {
                    transformer = null
                    callback.onComplete(outFile)
                }

                override fun onError(
                    composition: Composition,
                    result: ExportResult,
                    exception: ExportException,
                ) {
                    transformer = null
                    callback.onError(exception.message ?: "Render failed", exception)
                }
            })
            .build()

        transformer = t
        t.start(composition, outFile.absolutePath)
        return outFile
    }

    /** Current progress 0..1, or -1 if not yet available. */
    fun queryProgress(): Float {
        val holder = ProgressHolder()
        val state = transformer?.getProgress(holder) ?: return 0f
        return if (state == Transformer.PROGRESS_STATE_AVAILABLE) holder.progress / 100f else -1f
    }

    fun cancel() {
        transformer?.cancel()
        transformer = null
    }
}
