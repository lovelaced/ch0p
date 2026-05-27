package io.ch0p.analysis

import io.ch0p.edit.Word

/**
 * On-device ASR via whisper.cpp (native, in libch0panalysis). Produces word-timed
 * transcripts that drive captions, sentence-boundary cuts, and the LLM semantic layer.
 * Construct only when a GGML model is installed (ModelStore); device-only.
 */
class Whisper(modelPath: String) : AutoCloseable {

    private var ctx: Long = nativeInit(modelPath)

    @Volatile
    private var progressSink: ((Int) -> Unit)? = null

    val isReady: Boolean get() = ctx != 0L

    /**
     * @param pcm16k mono 16 kHz 16-bit PCM. @param language "auto" or e.g. "en".
     * @param onProgress whisper's 0..100 progress during the (single) pass.
     */
    fun transcribe(
        pcm16k: ShortArray,
        language: String = "auto",
        threads: Int = 4,
        onProgress: (Int) -> Unit = {},
    ): List<Word> {
        if (ctx == 0L || pcm16k.isEmpty()) return emptyList()
        val floats = FloatArray(pcm16k.size) { pcm16k[it] / 32768f }
        progressSink = onProgress
        return try {
            WhisperParser.parse(nativeTranscribe(ctx, floats, language, threads))
        } finally {
            progressSink = null
        }
    }

    /** Called from native (whisper progress_callback) on the transcribing thread. */
    @Suppress("unused")
    private fun progressCallback(percent: Int) {
        progressSink?.invoke(percent)
    }

    override fun close() {
        if (ctx != 0L) { nativeFree(ctx); ctx = 0L }
    }

    private external fun nativeInit(modelPath: String): Long
    private external fun nativeTranscribe(handle: Long, pcm: FloatArray, lang: String, threads: Int): String
    private external fun nativeFree(handle: Long)

    companion object {
        init { System.loadLibrary("ch0panalysis") }
    }
}
