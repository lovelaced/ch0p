package io.ch0p.analysis

import io.ch0p.edit.Word
import java.io.File

/**
 * Segment-scoped transcription, done right. Whisper's encoder always runs on fixed 30s
 * windows, so calling it once *per clip* pays a full 30s encoder pass for every tiny clip —
 * crippling for a montage of short cuts. Instead we **concatenate** the selected clips' audio
 * into one buffer (with short silence separators so words don't merge across joins) and run a
 * single pass (Whisper chunks it internally, with no_context so clips don't bleed), then map
 * word timestamps from concat-time back to source time. Cost ≈ total clip seconds, not
 * clips × 30s.
 *
 * Device-only. Reports whisper's real 0..1 progress.
 */
object Transcriber {

    fun interface Progress {
        fun onProgress(fraction: Float)
    }

    /** Words (source-time) plus the language whisper auto-detected ("en", "es", …; "" if none). */
    data class Transcript(val words: List<Word>, val language: String)

    private const val SR = 16_000
    private const val MIN_SAMPLES = 1_600       // skip <0.1s fragments
    private const val GAP_SAMPLES = SR * 3 / 10 // 0.3s silence between clips → clean word boundaries

    private class Span(val concatStartMs: Long, val concatEndMs: Long, val srcStartMs: Long)

    /** @param translate emit English captions regardless of spoken language (task=translate). */
    fun transcribeSpans(
        proxyPath: String,
        modelPath: String,
        spansMs: List<LongRange>,
        language: String = "auto",
        translate: Boolean = false,
        progress: Progress = Progress {},
    ): Transcript {
        if (spansMs.isEmpty()) return Transcript(emptyList(), "")
        val pcm = AudioDecoder.decodeMono16k(proxyPath)
        if (pcm.isEmpty()) return Transcript(emptyList(), "")

        // Concatenate the selected spans into one buffer, tracking concat→source time, with a
        // short silence gap after each clip so Whisper doesn't fuse the last/first words of joins.
        val parts = ArrayList<ShortArray>()
        val map = ArrayList<Span>()
        var concatSamples = 0
        for (span in spansMs) {
            val from = (span.first * SR / 1000).toInt().coerceIn(0, pcm.size)
            val to = (span.last * SR / 1000).toInt().coerceIn(from, pcm.size)
            if (to - from < MIN_SAMPLES) continue
            val slice = pcm.copyOfRange(from, to)
            val startMs = concatSamples * 1000L / SR
            parts.add(slice)
            concatSamples += slice.size
            map.add(Span(startMs, concatSamples * 1000L / SR, span.first))
            parts.add(ShortArray(GAP_SAMPLES))   // silence separator
            concatSamples += GAP_SAMPLES
        }
        if (map.isEmpty()) return Transcript(emptyList(), "")

        val concat = ShortArray(concatSamples)
        var off = 0
        for (p in parts) { System.arraycopy(p, 0, concat, off, p.size); off += p.size }

        return Whisper(modelPath).use { w ->
            if (!w.isReady) return Transcript(emptyList(), "")
            val raw = w.transcribe(concat, language, performanceCoreCount(), translate) { pct ->
                progress.onProgress(pct / 100f)
            }
            // Map each word from concat-time back to source-time via its span (words landing in a
            // silence gap are dropped — they're hallucinations, not real speech).
            val words = raw.mapNotNull { word ->
                val span = map.firstOrNull { word.startMs in it.concatStartMs..it.concatEndMs }
                    ?: return@mapNotNull null
                val delta = word.startMs - span.concatStartMs
                Word(word.text, span.srcStartMs + delta, span.srcStartMs + (word.endMs - span.concatStartMs))
            }
            Transcript(words, w.detectedLanguage())
        }
    }

    /**
     * Whisper on big.LITTLE is gated by the slowest thread, so using all cores (incl. the little
     * cluster) is *slower*. Count the non-littlest cores from cpufreq; fall back to half.
     */
    private fun performanceCoreCount(): Int {
        val cores = Runtime.getRuntime().availableProcessors()
        val n = runCatching {
            val freqs = (0 until cores).mapNotNull { i ->
                File("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq")
                    .takeIf { it.exists() }?.readText()?.trim()?.toIntOrNull()
            }
            val min = freqs.minOrNull() ?: return@runCatching null
            freqs.count { it > min }.takeIf { it >= 2 }
        }.getOrNull() ?: (cores / 2)
        return n.coerceIn(2, 6)
    }
}
