package io.ch0p.analysis

import android.content.Context
import com.google.mediapipe.tasks.audio.audioclassifier.AudioClassifier
import com.google.mediapipe.tasks.audio.core.RunningMode
import com.google.mediapipe.tasks.components.containers.AudioData
import com.google.mediapipe.tasks.core.BaseOptions
import java.io.File
import java.nio.ByteBuffer

/**
 * YAMNet audio-event classification via MediaPipe AudioClassifier. Produces per-window
 * (~0.975s) curves for laughter, applause/crowd reactions, and music — the channels a
 * classical detector can't supply. Gated on the model being installed; constructed only
 * when [ModelStore] has the file.
 *
 * Device-only (MediaPipe native). The label→signal mapping is the editorially useful bit.
 */
class AudioEvents(context: Context, modelPath: String) : AutoCloseable {

    data class Curves(
        val laughter: FloatArray,
        val applause: FloatArray,
        val music: FloatArray,
        val windowSec: Double,
    )

    private val classifier: AudioClassifier

    init {
        val options = AudioClassifier.AudioClassifierOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetBuffer(directBufferOf(File(modelPath))).build())
            .setRunningMode(RunningMode.AUDIO_CLIPS)
            .setMaxResults(521)  // all YAMNet classes, so target labels are never dropped
            .build()
        classifier = AudioClassifier.createFromOptions(context, options)
    }

    /** @param pcm16k mono 16 kHz 16-bit PCM. */
    fun analyze(pcm16k: ShortArray): Curves {
        if (pcm16k.isEmpty()) return Curves(FloatArray(0), FloatArray(0), FloatArray(0), WINDOW_SEC)
        val floats = FloatArray(pcm16k.size) { pcm16k[it] / 32768f }
        val format = AudioData.AudioDataFormat.builder()
            .setNumOfChannels(1).setSampleRate(16_000f).build()
        val audio = AudioData.create(format, floats.size).apply { load(floats) }

        val windows = classifier.classify(audio).classificationResults()
        val n = windows.size
        val laughter = FloatArray(n); val applause = FloatArray(n); val music = FloatArray(n)
        windows.forEachIndexed { i, w ->
            val cats = w.classifications().firstOrNull()?.categories().orEmpty()
            laughter[i] = maxScore(cats, LAUGH)
            applause[i] = maxScore(cats, APPLAUSE)
            music[i] = maxScore(cats, MUSIC)
        }
        return Curves(laughter, applause, music, WINDOW_SEC)
    }

    override fun close() = classifier.close()

    private fun maxScore(
        categories: List<com.google.mediapipe.tasks.components.containers.Category>,
        labels: Set<String>,
    ): Float {
        var best = 0f
        for (c in categories) {
            if (labels.any { c.categoryName().equals(it, ignoreCase = true) }) best = maxOf(best, c.score())
        }
        return best
    }

    companion object {
        private const val WINDOW_SEC = 0.975

        private val LAUGH = setOf("Laughter", "Baby laughter", "Giggle", "Snicker", "Belly laugh", "Chuckle, chortle")
        private val APPLAUSE = setOf("Applause", "Cheering", "Crowd", "Clapping")
        private val MUSIC = setOf("Music")

        private fun directBufferOf(file: File): ByteBuffer {
            val bytes = file.readBytes()
            return ByteBuffer.allocateDirect(bytes.size).apply { put(bytes); rewind() }
        }
    }
}
