package io.ch0p.ingest

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import java.io.File
import java.util.UUID

/**
 * Produces a downscaled H.264 proxy (default 720p long-edge) used for ALL analysis and
 * preview. Only the final render touches the full-res original — this single decision
 * dominates the on-device performance/thermal budget.
 *
 * Transformer is callback-based and must be started/cancelled on a thread with a Looper
 * (the main thread). The app layer can wrap [start]/[queryProgress] in coroutines.
 */
@UnstableApi
class ProxyGenerator(private val context: Context) {

    interface Callback {
        fun onComplete(proxyFile: File)
        fun onError(message: String, cause: Throwable?)
    }

    private var transformer: Transformer? = null

    private fun proxiesDir(): File =
        File(context.filesDir, "proxies").apply { mkdirs() }

    /** Begins transcoding; returns the destination file (written when [Callback.onComplete] fires). */
    fun start(sourceUri: Uri, spec: ProxySpec, callback: Callback): File {
        val outFile = File(proxiesDir(), "${UUID.randomUUID()}_proxy.mp4")

        val presentation = Presentation.createForWidthAndHeight(
            spec.width, spec.height, Presentation.LAYOUT_SCALE_TO_FIT,
        )
        val edited = EditedMediaItem.Builder(MediaItem.fromUri(sourceUri))
            .setEffects(Effects(/* audioProcessors = */ emptyList(), listOf(presentation)))
            .build()

        val encoderFactory = DefaultEncoderFactory.Builder(context)
            .setRequestedVideoEncoderSettings(
                VideoEncoderSettings.Builder().setBitrate(spec.bitrate).build(),
            )
            .build()

        val t = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setEncoderFactory(encoderFactory)
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
                    callback.onError(exception.message ?: "Proxy transform failed", exception)
                }
            })
            .build()

        transformer = t
        t.start(edited, outFile.absolutePath)
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
