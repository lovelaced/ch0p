package io.ch0p.analysis

import android.graphics.Bitmap
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Learned-aesthetic scorer (NIMA-style, TFLite). Adapts to the loaded model's actual input
 * (reads HxW and float-vs-uint8 from the interpreter) and output (K buckets → mean opinion
 * score via [NimaScoring]). This robustness matters because there's no single canonical NIMA
 * tflite — users import their own converted model. Construct only when a model is installed;
 * classical aesthetic remains the always-on baseline, so this is an optional refinement.
 */
class NimaScorer(modelPath: String) : AutoCloseable {

    private val interpreter = Interpreter(loadModel(File(modelPath)))

    private val inTensor = interpreter.getInputTensor(0)
    private val inShape = inTensor.shape()          // [1, H, W, C]
    private val h = inShape.getOrElse(1) { 224 }
    private val w = inShape.getOrElse(2) { 224 }
    private val channels = inShape.getOrElse(3) { 3 }
    private val quantizedInput = inTensor.dataType() == DataType.UINT8
    private val outBuckets = interpreter.getOutputTensor(0).shape().last()

    fun score(bitmap: Bitmap): Float {
        val scaled = Bitmap.createScaledBitmap(bitmap, w, h, true)
        val px = IntArray(w * h)
        scaled.getPixels(px, 0, w, 0, 0, w, h)
        if (scaled !== bitmap) scaled.recycle()

        val output = arrayOf(FloatArray(outBuckets))
        if (quantizedInput) {
            val buf = ByteBuffer.allocateDirect(w * h * channels).order(ByteOrder.nativeOrder())
            for (p in px) { buf.put((p shr 16).toByte()); buf.put((p shr 8).toByte()); buf.put(p.toByte()) }
            buf.rewind()
            interpreter.run(buf, output)
        } else {
            val input = Array(1) { Array(h) { Array(w) { FloatArray(channels) } } }
            for (y in 0 until h) for (x in 0 until w) {
                val p = px[y * w + x]
                input[0][y][x][0] = ((p shr 16) and 0xFF) / 255f
                input[0][y][x][1] = ((p shr 8) and 0xFF) / 255f
                input[0][y][x][2] = (p and 0xFF) / 255f
            }
            interpreter.run(input, output)
        }
        return NimaScoring.meanScore(output[0])
    }

    override fun close() = interpreter.close()

    private companion object {
        fun loadModel(file: File): MappedByteBuffer =
            RandomAccessFile(file, "r").use { it.channel.map(FileChannel.MapMode.READ_ONLY, 0, it.length()) }
    }
}
