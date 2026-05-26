package io.ch0p.analysis

import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.RandomAccessFile
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * NIMA learned-aesthetic scorer (MobileNet-class, TFLite). Input 224×224×3 in [0,1], output
 * a 10-bucket softmax → mean opinion score via [NimaScoring]. Construct only when a model is
 * installed; device-only. Classical aesthetic (sharpness+colorfulness) remains the baseline,
 * so this is an optional refinement, weighted up for the cinematic preset.
 */
class NimaScorer(modelPath: String) : AutoCloseable {

    private val interpreter = Interpreter(loadModel(File(modelPath)))

    fun score(bitmap: Bitmap): Float {
        val scaled = Bitmap.createScaledBitmap(bitmap, SIZE, SIZE, true)
        val input = Array(1) { Array(SIZE) { Array(SIZE) { FloatArray(3) } } }
        val px = IntArray(SIZE * SIZE)
        scaled.getPixels(px, 0, SIZE, 0, 0, SIZE, SIZE)
        for (y in 0 until SIZE) {
            for (x in 0 until SIZE) {
                val p = px[y * SIZE + x]
                input[0][y][x][0] = ((p shr 16) and 0xFF) / 255f
                input[0][y][x][1] = ((p shr 8) and 0xFF) / 255f
                input[0][y][x][2] = (p and 0xFF) / 255f
            }
        }
        if (scaled !== bitmap) scaled.recycle()
        val output = Array(1) { FloatArray(10) }
        interpreter.run(input, output)
        return NimaScoring.meanScore(output[0])
    }

    override fun close() = interpreter.close()

    private companion object {
        const val SIZE = 224
        fun loadModel(file: File): MappedByteBuffer =
            RandomAccessFile(file, "r").use { it.channel.map(FileChannel.MapMode.READ_ONLY, 0, it.length()) }
    }
}
