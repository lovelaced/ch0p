package io.ch0p.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.CanvasOverlay
import io.ch0p.edit.captions.CaptionChunk

/**
 * Burns karaoke-style captions onto frames: the active chunk's words are drawn centered in
 * the safe band with a heavy stroke+shadow, and the currently-spoken word is highlighted in
 * the accent color. Chunk/word times are in source time; the per-clip [clipStartMs] maps the
 * frame's clip-relative timestamp back to source time. Chunking is the JVM-tested part
 * ([io.ch0p.edit.captions.CaptionChunker]); this is the device-side renderer.
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
        val textSize = h * 0.052f
        base.textSize = textSize; stroke.textSize = textSize
        stroke.strokeWidth = textSize * 0.12f
        val y = h * 0.62f  // center-lower safe band (above TikTok UI furniture)

        val spaceW = base.measureText(" ")
        val widths = chunk.words.map { base.measureText(it.text) }
        val total = widths.sum() + spaceW * (chunk.words.size - 1).coerceAtLeast(0)
        var x = (w - total) / 2f

        chunk.words.forEachIndexed { i, word ->
            val isActive = srcMs in word.startMs..word.endMs
            canvas.drawText(word.text, x, y, stroke)              // outline
            base.color = if (isActive) accent else Color.WHITE
            canvas.drawText(word.text, x, y, base)               // fill
            x += widths[i] + spaceW
        }
    }
}
