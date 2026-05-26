package io.ch0p.edit.captions

/**
 * Serializes caption chunks to standard subtitle formats. Pure Kotlin, JVM-testable.
 * (Burned-in karaoke rendering is the device-side CanvasOverlay path; these files are for
 * export / re-import / accessibility.)
 */
object SubtitleWriter {

    fun toSrt(chunks: List<CaptionChunk>): String = buildString {
        chunks.forEachIndexed { i, c ->
            append(i + 1).append('\n')
            append(srtTime(c.startMs)).append(" --> ").append(srtTime(c.endMs)).append('\n')
            append(c.text).append("\n\n")
        }
    }

    fun toVtt(chunks: List<CaptionChunk>): String = buildString {
        append("WEBVTT\n\n")
        chunks.forEach { c ->
            append(vttTime(c.startMs)).append(" --> ").append(vttTime(c.endMs)).append('\n')
            append(c.text).append("\n\n")
        }
    }

    private fun srtTime(ms: Long): String = clock(ms, ',')
    private fun vttTime(ms: Long): String = clock(ms, '.')

    private fun clock(ms: Long, msSep: Char): String {
        val h = ms / 3_600_000
        val m = (ms % 3_600_000) / 60_000
        val s = (ms % 60_000) / 1000
        val millis = ms % 1000
        return "%02d:%02d:%02d%c%03d".format(h, m, s, msSep, millis)
    }
}
