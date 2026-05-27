package io.ch0p.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.CanvasOverlay
import io.ch0p.edit.Word
import io.ch0p.edit.captions.CaptionChunk

/**
 * Burns karaoke-style captions onto frames: the active chunk's words are drawn in the safe
 * band with a heavy stroke+shadow, the currently-spoken word highlighted in the accent color.
 * Text wraps to fit inside the frame's safe area (never runs off the viewport) regardless of
 * output aspect ratio, and the multi-line block is clamped to the lower-center safe band.
 * Chunk/word times are in source time; [clipStartMs] maps the frame's clip-relative timestamp
 * back to source time. Chunking is the JVM-tested part ([io.ch0p.edit.captions.CaptionChunker]).
 */
@UnstableApi
class CaptionOverlay(
    private val chunks: List<CaptionChunk>,
    private val clipStartMs: Long,
) : CanvasOverlay(/* useInputFrameSize = */ true) {

    private val base = Paint().apply {
        isAntiAlias = true; color = Color.WHITE; textAlign = Paint.Align.LEFT
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        setShadowLayer(8f, 0f, 4f, 0x99000000.toInt())
    }
    private val stroke = Paint().apply {
        isAntiAlias = true; color = Color.BLACK; style = Paint.Style.STROKE
        textAlign = Paint.Align.LEFT; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val accent = 0xFFFFB23E.toInt()  // Studio "active" amber

    override fun onDraw(canvas: Canvas, presentationTimeUs: Long) {
        val srcMs = clipStartMs + presentationTimeUs / 1000
        val chunk = chunks.firstOrNull { srcMs in it.startMs..it.endMs } ?: return

        val w = canvas.width
        val h = canvas.height
        // Size text against the short edge so landscape (2.39:1) and portrait (9:16) read alike.
        val textSize = (minOf(w, h) * 0.062f).coerceIn(22f, h * 0.09f)
        base.textSize = textSize; stroke.textSize = textSize
        stroke.strokeWidth = textSize * 0.12f

        val maxWidth = w * 0.90f                 // 5% safe margin each side
        val spaceW = base.measureText(" ")
        val lines = wrap(chunk.words, maxWidth, spaceW)

        val lineHeight = textSize * 1.22f
        val bottomBaseline = h * 0.86f           // above platform UI furniture
        val topBaseline = bottomBaseline - (lines.size - 1) * lineHeight

        lines.forEachIndexed { li, line ->
            val widths = line.map { base.measureText(it.text) }
            val total = widths.sum() + spaceW * (line.size - 1).coerceAtLeast(0)
            var x = (w - total) / 2f
            val y = topBaseline + li * lineHeight
            line.forEachIndexed { i, word ->
                val isActive = srcMs in word.startMs..word.endMs
                canvas.drawText(word.text, x, y, stroke)
                base.color = if (isActive) accent else Color.WHITE
                canvas.drawText(word.text, x, y, base)
                x += widths[i] + spaceW
            }
        }
    }

    /** Greedy word wrap so each line fits [maxWidth]; an over-long single word gets its own line. */
    private fun wrap(words: List<Word>, maxWidth: Float, spaceW: Float): List<List<Word>> {
        val lines = ArrayList<MutableList<Word>>()
        var line = ArrayList<Word>()
        var lineW = 0f
        for (word in words) {
            val ww = base.measureText(word.text)
            val add = if (line.isEmpty()) ww else lineW + spaceW + ww
            if (line.isNotEmpty() && add > maxWidth) {
                lines.add(line); line = ArrayList(); lineW = 0f
            }
            line.add(word)
            lineW = if (lineW == 0f) ww else lineW + spaceW + ww
        }
        if (line.isNotEmpty()) lines.add(line)
        return lines
    }
}
