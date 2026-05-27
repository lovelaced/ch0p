package io.ch0p.analysis

import io.ch0p.edit.Word

/**
 * Segment-scoped transcription. Instead of running Whisper over the whole (possibly very
 * long) source — which is the "transcribing forever" trap — this decodes the proxy audio
 * once and transcribes only the chosen clip spans, reporting per-clip progress. Cost scales
 * with the short's length (seconds), not the source.
 *
 * Word timestamps are offset back to source time so captions/cuts line up. Device-only.
 */
object Transcriber {

    fun interface Progress {
        fun onProgress(fraction: Float)
    }

    /**
     * @param spansMs source-time ranges (the selected clips) to transcribe.
     * @return words across all spans, in source time.
     */
    fun transcribeSpans(
        proxyPath: String,
        modelPath: String,
        spansMs: List<LongRange>,
        language: String = "auto",
        progress: Progress = Progress {},
    ): List<Word> {
        if (spansMs.isEmpty()) return emptyList()
        val pcm = AudioDecoder.decodeMono16k(proxyPath)
        if (pcm.isEmpty()) return emptyList()

        val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 8)
        val words = ArrayList<Word>()
        Whisper(modelPath).use { whisper ->
            if (!whisper.isReady) return emptyList()
            spansMs.forEachIndexed { i, span ->
                val from = (span.first * SR / 1000).toInt().coerceIn(0, pcm.size)
                val to = (span.last * SR / 1000).toInt().coerceIn(from, pcm.size)
                if (to - from > MIN_SAMPLES) {
                    val slice = pcm.copyOfRange(from, to)
                    runCatching { whisper.transcribe(slice, language, threads) }
                        .getOrDefault(emptyList())
                        .forEach { w -> words.add(Word(w.text, w.startMs + span.first, w.endMs + span.first)) }
                }
                progress.onProgress((i + 1).toFloat() / spansMs.size)
            }
        }
        return words
    }

    private const val SR = 16_000
    private const val MIN_SAMPLES = 1_600  // <0.1s of audio isn't worth a Whisper pass
}
