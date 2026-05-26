package io.ch0p.analysis

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import io.ch0p.edit.Analysis
import io.ch0p.edit.Normalizer
import io.ch0p.ingest.telemetry.Telemetry
import io.ch0p.ingest.telemetry.TelemetryFusion
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Turns a proxy file into the engine's [Analysis]: decode-sample frames into the native
 * scene/motion/aesthetic detector, decode audio for loudness, then fuse everything onto a
 * single uniform time grid (the video sample rate).
 *
 * Populated now: scene boundaries, action (motion), aesthetic (sharpness+colorfulness),
 * interest (motion+aesthetic), loudness, drama (loudness swell).
 * TODO(phase 2c): speech (Silero VAD) and laughter (YAMNet) — currently silent channels.
 *
 * Blocking; run off the main thread. Device-only (MediaMetadataRetriever / MediaCodec).
 */
object AnalysisPipeline {

    private const val SAMPLE_FPS = 4.0
    private const val ANALYZE_WIDTH = 160  // downscale frames for cheap native analysis

    /** Live telemetry for the Analyzing screen — counts populate as stages complete. */
    data class AnalysisProgress(
        val fraction: Float,
        val stage: String,
        val scenes: Int = 0,
        val faces: Int = 0,
        val laughs: Int = 0,
    )

    fun interface Progress {
        fun onProgress(p: AnalysisProgress)
    }

    fun analyze(
        context: Context,
        proxyPath: String,
        durationMs: Long,
        frameRate: Double,
        telemetry: Telemetry? = null,
        audioEventsModelPath: String? = null,
        whisperModelPath: String? = null,
        faceModelPath: String? = null,
        sileroModelPath: String? = null,
        nimaModelPath: String? = null,
        progress: Progress = Progress { },
    ): Analysis {
        val retriever = MediaMetadataRetriever()
        val faceAnalyzer = faceModelPath?.takeIf { File(it).exists() }
            ?.let { runCatching { FaceAnalyzer(context, it) }.getOrNull() }
        val faceScores = ArrayList<Float>()
        val faceTargets = ArrayList<io.ch0p.edit.reframe.SubjectTarget>()
        val nimaScorer = nimaModelPath?.takeIf { File(it).exists() }
            ?.let { runCatching { NimaScorer(it) }.getOrNull() }
        val nimaScores = ArrayList<Float>()
        val (motion, sharp, color, cuts) = try {
            retriever.setDataSource(proxyPath)
            val srcW = meta(retriever, MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH) ?: 16
            val srcH = meta(retriever, MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT) ?: 9
            val dstW = ANALYZE_WIDTH
            val dstH = max(2, (ANALYZE_WIDTH.toLong() * srcH / srcW).toInt()).let { it - (it % 2) }

            NativeAnalyzer().use { na ->
                val stepMs = (1000.0 / SAMPLE_FPS).toLong()
                var tMs = 0L
                while (tMs < durationMs) {
                    val bmp = retriever.getScaledFrameAtTime(
                        tMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, dstW, dstH,
                    )
                    if (bmp != null) {
                        if (faceAnalyzer != null) {
                            val ff = runCatching { faceAnalyzer.detect(bmp) }.getOrDefault(FaceScoring.EMPTY)
                            faceScores.add(ff.score)
                            if (ff.hasFace) faceTargets.add(
                                io.ch0p.edit.reframe.SubjectTarget(tMs / 1000.0, ff.cx.toDouble(), ff.cy.toDouble(), ff.size.toDouble()),
                            )
                        }
                        if (nimaScorer != null) {
                            nimaScores.add(runCatching { nimaScorer.score(bmp) }.getOrDefault(0f))
                        }
                        na.pushFrame(bitmapToRgb(bmp), bmp.width, bmp.height, tMs / 1000.0)
                        bmp.recycle()
                    }
                    if (durationMs > 0) progress.onProgress(
                        AnalysisProgress((tMs.toFloat() / durationMs) * 0.8f, "Analyzing video", faces = faceTargets.size),
                    )
                    tMs += stepMs
                }
                NativeResult(na.motionCurve(), na.sharpnessCurve(), na.colorfulnessCurve(), na.cutTimesSec())
            }
        } finally {
            runCatching { faceAnalyzer?.close() }
            runCatching { nimaScorer?.close() }
            runCatching { retriever.release() }
        }

        val n = motion.size
        val sceneCount = cuts.size + 1
        val faceCount = faceTargets.size
        progress.onProgress(AnalysisProgress(0.85f, "Analyzing audio", scenes = sceneCount, faces = faceCount))
        val pcm = AudioDecoder.decodeMono16k(proxyPath)
        val loudHi = Loudness.normalizedCurve(pcm, 16_000, windowMs = (1000.0 / SAMPLE_FPS).toInt())
        // Silero VAD if installed (music/noise-robust); else energy VAD. Falls back on any failure.
        val speechHi = if (sileroModelPath != null && File(sileroModelPath).exists()) {
            runCatching { SileroVad(sileroModelPath).use { it.speechCurve(pcm) } }
                .getOrNull()?.takeIf { it.isNotEmpty() } ?: Vad.speechCurve(pcm, 16_000)
        } else Vad.speechCurve(pcm, 16_000)

        // YAMNet laughter/applause/music — only if the model is installed.
        val laughterHi: FloatArray = if (audioEventsModelPath != null && File(audioEventsModelPath).exists()) {
            runCatching {
                AudioEvents(context, audioEventsModelPath).use { it.analyze(pcm).laughter }
            }.getOrDefault(FloatArray(0))
        } else FloatArray(0)

        // Whisper ASR (word-timed transcript) — only if a GGML model is installed.
        val laughCount = laughterHi.count { it > 0.4f }
        val transcript: List<io.ch0p.edit.Word> = if (whisperModelPath != null && File(whisperModelPath).exists()) {
            progress.onProgress(AnalysisProgress(0.9f, "Transcribing", sceneCount, faceCount, laughCount))
            runCatching { Whisper(whisperModelPath).use { it.transcribe(pcm) } }.getOrDefault(emptyList())
        } else emptyList()

        progress.onProgress(AnalysisProgress(0.95f, "Assembling", sceneCount, faceCount, laughCount))
        var aesthetic = fuseNormalized(sharp, color, n)
        // NIMA learned aesthetic blends with the classical metrics when installed.
        if (nimaScores.isNotEmpty()) {
            val nima = clampLen(nimaScores.toFloatArray(), n)
            aesthetic = FloatArray(n) { 0.5f * aesthetic[it] + 0.5f * nima[it] }
        }
        var action = clampLen(motion, n)
        val loudness = resampleTo(loudHi, n)
        val speech = resampleTo(speechHi, n)
        val drama = swell(loudness)
        var interest = combine(action, aesthetic) { a, b -> 0.5f * a + 0.5f * b }.let { normalize(it) }

        // Faces are a strong interest cue — blend the per-frame face score in when available.
        if (faceScores.isNotEmpty()) {
            val face = clampLen(faceScores.toFloatArray(), n)
            interest = normalize(FloatArray(n) { 0.5f * interest[it] + 0.5f * face[it] })
        }

        // Camera telemetry (GPMF): physical motion boosts action; operator HiLight tags
        // boost interest — making the editor favour moments a human marked, even with no speech.
        if (telemetry != null && telemetry.hasData && n > 0) {
            val telemAction = TelemetryFusion.toGrid(telemetry.accelEnergy, telemetry.sampleTimesMs, n, durationMs)
            action = TelemetryFusion.boost(action, telemAction, weight = 0.6f)
            val hilite = TelemetryFusion.highlightCurve(telemetry.highlightMarksMs, n, durationMs)
            interest = TelemetryFusion.boost(interest, hilite, weight = 0.8f)
        }

        progress.onProgress(AnalysisProgress(1f, "Done", sceneCount, faceCount, laughCount))
        return Analysis(
            durationMs = durationMs,
            frameRate = frameRate,
            shots = ShotBuilder.fromCuts(cuts, durationMs),
            sampleRateHz = SAMPLE_FPS,
            action = action,
            speech = speech,            // energy/ZCR VAD; Silero upgrade later
            laughter = resampleTo(laughterHi, n),  // YAMNet when installed, else silent
            loudness = loudness,
            drama = drama,
            aesthetic = aesthetic,
            interest = interest,
            words = transcript,
            cropTrajectory = if (faceTargets.size >= 2)
                io.ch0p.edit.reframe.CropTrajectory.build(faceTargets, cuts.toList()) else emptyList(),
        )
    }

    private data class NativeResult(
        val motion: FloatArray, val sharp: FloatArray, val color: FloatArray, val cuts: DoubleArray,
    )

    private fun meta(r: MediaMetadataRetriever, key: Int): Int? =
        r.extractMetadata(key)?.toIntOrNull()?.takeIf { it > 0 }

    private fun bitmapToRgb(bmp: Bitmap): ByteArray {
        val w = bmp.width; val h = bmp.height
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        val rgb = ByteArray(w * h * 3)
        for (i in px.indices) {
            val p = px[i]
            rgb[i * 3] = ((p shr 16) and 0xFF).toByte()
            rgb[i * 3 + 1] = ((p shr 8) and 0xFF).toByte()
            rgb[i * 3 + 2] = (p and 0xFF).toByte()
        }
        return rgb
    }

    /** Robust-normalize two raw channels and average them into a 0..1 channel of length n. */
    private fun fuseNormalized(a: FloatArray, b: FloatArray, n: Int): FloatArray {
        if (n == 0) return FloatArray(0)
        val na = Normalizer.fit(a.take(n))
        val nb = Normalizer.fit(b.take(n))
        return FloatArray(n) { i ->
            0.5f * na.apply(a.getOrElse(i) { 0f }) + 0.5f * nb.apply(b.getOrElse(i) { 0f })
        }
    }

    private fun clampLen(a: FloatArray, n: Int): FloatArray =
        if (a.size == n) a else FloatArray(n) { a.getOrElse(it) { 0f } }

    /** Resample an arbitrary-length curve to exactly n samples (nearest). */
    private fun resampleTo(src: FloatArray, n: Int): FloatArray {
        if (n == 0) return FloatArray(0)
        if (src.isEmpty()) return FloatArray(n)
        return FloatArray(n) { i -> src[(i.toLong() * src.size / n).toInt().coerceIn(0, src.size - 1)] }
    }

    /** Positive rate-of-change of loudness, normalized — a cheap "dramatic swell" proxy. */
    private fun swell(loudness: FloatArray): FloatArray {
        if (loudness.isEmpty()) return loudness
        val raw = FloatArray(loudness.size)
        for (i in 1 until loudness.size) raw[i] = max(0f, loudness[i] - loudness[i - 1])
        return normalize(raw)
    }

    private inline fun combine(a: FloatArray, b: FloatArray, op: (Float, Float) -> Float): FloatArray =
        FloatArray(a.size) { op(a[it], b.getOrElse(it) { 0f }) }

    private fun normalize(a: FloatArray): FloatArray {
        if (a.isEmpty()) return a
        val nrm = Normalizer.fit(a.toList())
        return FloatArray(a.size) { nrm.apply(a[it]) }
    }
}
