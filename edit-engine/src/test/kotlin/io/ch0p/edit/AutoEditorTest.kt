package io.ch0p.edit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoEditorTest {

    // --- fixture helpers ----------------------------------------------------

    private val hz = 4.0  // 4 samples/sec → 250ms resolution

    private fun samples(durationMs: Long) = (durationMs / 1000.0 * hz).toInt()

    private fun flat(durationMs: Long, v: Float) = FloatArray(samples(durationMs)) { v }

    /** Set [v] over [startMs,endMs) in a signal array sampled at [hz]. */
    private fun FloatArray.hot(startMs: Long, endMs: Long, v: Float): FloatArray {
        val a = (startMs / 1000.0 * hz).toInt().coerceIn(0, size - 1)
        val b = (endMs / 1000.0 * hz).toInt().coerceIn(0, size)
        for (i in a until b) this[i] = v
        return this
    }

    private fun shots(durationMs: Long, lenMs: Long): List<Shot> =
        (0 until durationMs step lenMs).map { Shot(it, minOf(it + lenMs, durationMs)) }

    /** 60s clip, 6 shots of 10s, low baseline everywhere. */
    private fun baseAnalysis(
        durationMs: Long = 60_000,
        shotLenMs: Long = 10_000,
        words: List<Word> = emptyList(),
        build: (MutableMap<String, FloatArray>) -> Unit = {},
    ): Analysis {
        val ch = mutableMapOf(
            "action" to flat(durationMs, 0.1f),
            "speech" to flat(durationMs, 0.1f),
            "laughter" to flat(durationMs, 0.05f),
            "loudness" to flat(durationMs, 0.3f),
            "drama" to flat(durationMs, 0.1f),
            "aesthetic" to flat(durationMs, 0.3f),
            "interest" to flat(durationMs, 0.1f),
        )
        build(ch)
        return Analysis(
            durationMs = durationMs, frameRate = 30.0, shots = shots(durationMs, shotLenMs),
            sampleRateHz = hz,
            action = ch["action"]!!, speech = ch["speech"]!!, laughter = ch["laughter"]!!,
            loudness = ch["loudness"]!!, drama = ch["drama"]!!, aesthetic = ch["aesthetic"]!!,
            interest = ch["interest"]!!, words = words,
        )
    }

    // --- tests --------------------------------------------------------------

    @Test fun `produces a non-empty edl within the duration budget`() {
        val a = baseAnalysis { it["interest"]!!.hot(20_000, 28_000, 0.95f) }
        val edl = AutoEditor.edit(a, Presets.TIKTOK)
        assertTrue("edl should not be empty", edl.units.isNotEmpty())
        assertTrue(
            "duration ${edl.totalDurationMs} must be <= ${Presets.TIKTOK.targetDurationMs.last}",
            edl.totalDurationMs <= Presets.TIKTOK.targetDurationMs.last,
        )
    }

    @Test fun `selects the high-signal region`() {
        // A clear interest+action spike at 30-38s should be represented in the cut.
        val a = baseAnalysis {
            it["interest"]!!.hot(30_000, 38_000, 0.95f)
            it["action"]!!.hot(30_000, 38_000, 0.9f)
        }
        val edl = AutoEditor.edit(a, Presets.PROMO)
        val coversSpike = edl.units.any { it.srcInMs < 38_000 && it.srcOutMs > 30_000 }
        assertTrue("EDL should include the 30-38s spike", coversSpike)
    }

    @Test fun `chronological preset keeps source order`() {
        val a = baseAnalysis(durationMs = 90_000) {
            it["aesthetic"]!!.hot(0, 90_000, 0.6f)
            it["interest"]!!.hot(10_000, 14_000, 0.9f)
            it["action"]!!.hot(60_000, 66_000, 0.9f)
        }
        val edl = AutoEditor.edit(a, Presets.VLOG)  // CHRONOLOGICAL
        val ins = edl.units.map { it.srcInMs }
        assertEquals("entries must be in ascending source order", ins.sorted(), ins)
    }

    @Test fun `hook-first puts a high-score unit first`() {
        val a = baseAnalysis {
            // Strong late hook the chronological order would otherwise bury.
            it["speech"]!!.hot(50_000, 58_000, 0.95f)
            it["interest"]!!.hot(50_000, 58_000, 0.95f)
        }
        val edl = AutoEditor.edit(a, Presets.TIKTOK)  // HOOK_FIRST
        val first = edl.units.first()
        val laterExists = edl.units.any { it.srcInMs < first.srcInMs }
        // The first entry is not simply the earliest source unit.
        assertTrue("hook should reorder a strong later moment to the front", laterExists)
    }

    @Test fun `never cuts mid-word`() {
        // One 12s shot (exceeds TikTok maxUnit -> gets split), words every 500ms with 50ms gaps.
        val words = (0L until 12_000L step 500L).map { Word("w", it, it + 450) }
        val a = Analysis(
            durationMs = 12_000, frameRate = 30.0, shots = listOf(Shot(0, 12_000)),
            sampleRateHz = hz,
            action = flat(12_000, 0.2f), speech = flat(12_000, 0.5f),
            laughter = flat(12_000, 0.1f), loudness = flat(12_000, 0.3f),
            drama = flat(12_000, 0.2f), aesthetic = flat(12_000, 0.4f),
            interest = flat(12_000, 0.3f), words = words,
        )
        val units = Segmentation.segment(a, Presets.TIKTOK)
        // internal boundaries = each unit's out-point except the final shot end
        val boundaries = units.map { it.srcOutMs }.filter { it != 12_000L }
        for (b in boundaries) {
            val insideWord = words.any { b > it.startMs && b < it.endMs }
            assertTrue("cut at $b falls inside a word", !insideWord)
        }
    }

    @Test fun `different presets produce different edits`() {
        val a = baseAnalysis {
            it["speech"]!!.hot(10_000, 18_000, 0.95f)     // talking region
            it["aesthetic"]!!.hot(40_000, 50_000, 0.95f)  // pretty region
            it["action"]!!.hot(40_000, 50_000, 0.2f)
        }
        val tiktok = AutoEditor.edit(a, Presets.TIKTOK).units.map { it.srcInMs to it.srcOutMs }.toSet()
        val cine = AutoEditor.edit(a, Presets.CINEMATIC).units.map { it.srcInMs to it.srcOutMs }.toSet()
        assertNotEquals("speech-led and aesthetic-led presets should select differently", tiktok, cine)
    }

    @Test fun `short source yields a short reel`() {
        val a = baseAnalysis(durationMs = 8_000, shotLenMs = 4_000) {
            it["interest"]!!.hot(0, 8_000, 0.4f)
        }
        val edl = AutoEditor.edit(a, Presets.TIKTOK)
        assertTrue("cannot exceed source length", edl.totalDurationMs <= 8_000)
    }

    @Test fun `editVariants yields distinct non-overlapping shorts`() {
        val a = baseAnalysis(durationMs = 120_000, shotLenMs = 10_000) {
            it["interest"]!!.hot(5_000, 14_000, 0.9f)
            it["action"]!!.hot(40_000, 50_000, 0.9f)
            it["aesthetic"]!!.hot(80_000, 95_000, 0.9f)
        }
        val variants = AutoEditor.editVariants(a, Presets.TIKTOK, count = 3)
        assertTrue("should yield at least 2 variants from 2min", variants.size >= 2)
        // No source range is reused across variants.
        val ranges = variants.flatMap { v -> v.units.map { it.srcInMs to it.srcOutMs } }
        assertEquals("variants must not share source ranges", ranges.size, ranges.toSet().size)
    }

    @Test fun `all six presets run without error`() {
        val a = baseAnalysis {
            it["action"]!!.hot(5_000, 12_000, 0.8f)
            it["drama"]!!.hot(30_000, 36_000, 0.8f)
            it["interest"]!!.hot(45_000, 52_000, 0.8f)
        }
        for (p in Presets.all) {
            val edl = AutoEditor.edit(a, p)
            assertEquals(p.id, edl.presetId)
            assertTrue("${p.id} should pick something", edl.units.isNotEmpty())
        }
    }
}
