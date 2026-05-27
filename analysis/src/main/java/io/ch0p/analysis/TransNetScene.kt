package io.ch0p.analysis

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.graphics.Bitmap
import java.io.File
import java.nio.FloatBuffer

/**
 * TransNetV2 shot-boundary detection (ONNX). Unlike the classical HSV frame-differencing, this
 * catches gradual transitions (dissolves/fades/wipes) that real footage is full of. It decodes
 * the proxy densely at 48×27 RGB (the model's native input) and runs 100-frame windows, returning
 * cut times in seconds.
 *
 * Verified contract: input [1,100,27,48,3] float RGB in 0..255 (NO normalization), output a
 * per-frame transition probability; rising edges above [THRESHOLD] are cuts.
 *
 * Device-only (ONNX Runtime + MediaCodec). Constructed only when installed; caller falls back to
 * the classical native cuts if this throws or finds nothing.
 */
class TransNetScene(modelPath: String) : AutoCloseable {

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession = env.createSession(File(modelPath).readBytes())
    private val inputName: String = session.inputNames.first()

    /** @return cut times in seconds (start of each new shot after the first). */
    fun detect(proxyPath: String, frameRate: Double): List<Double> {
        val fps = if (frameRate in 1.0..240.0) frameRate else 30.0
        val stepMs = (1000.0 / fps).toLong().coerceAtLeast(1)
        val cuts = ArrayList<Double>()
        val window = ArrayList<FloatArray>(WIN)
        var windowStart = 0  // global frame index of window[0]

        fun flush() {
            if (window.isEmpty()) return
            val n = window.size
            val buf = FloatArray(WIN * FRAME)
            for (i in 0 until WIN) {
                val src = window[minOf(i, n - 1)]  // pad tail by repeating last frame
                System.arraycopy(src, 0, buf, i * FRAME, FRAME)
            }
            val t = OnnxTensor.createTensor(env, FloatBuffer.wrap(buf), longArrayOf(1, WIN.toLong(), H.toLong(), W.toLong(), 3))
            try {
                val result = session.run(mapOf(inputName to t))
                val probs = flatten(result.get(0).value, WIN)
                result.close()
                for (i in 0 until n) {
                    val rising = probs[i] > THRESHOLD && (i == 0 || probs[i - 1] <= THRESHOLD)
                    if (rising && (windowStart + i) > 0) cuts.add((windowStart + i) / fps)
                }
            } finally {
                t.close()
            }
            windowStart += n
            window.clear()
        }

        FrameSampler.sample(proxyPath, W, H, stepMs) { bmp, _ ->
            window.add(frameFloats(bmp))
            bmp.recycle()
            if (window.size == WIN) flush()
        }
        flush()
        return cuts
    }

    /** 48×27 bitmap → [H,W,3] float 0..255 (RGB), matching the verified model input. */
    private fun frameFloats(bmp: Bitmap): FloatArray {
        val b = if (bmp.width == W && bmp.height == H) bmp else Bitmap.createScaledBitmap(bmp, W, H, true)
        val px = IntArray(W * H)
        b.getPixels(px, 0, W, 0, 0, W, H)
        if (b != bmp) b.recycle()
        val out = FloatArray(FRAME)
        for (i in 0 until W * H) {
            val p = px[i]
            out[i * 3] = ((p shr 16) and 0xFF).toFloat()
            out[i * 3 + 1] = ((p shr 8) and 0xFF).toFloat()
            out[i * 3 + 2] = (p and 0xFF).toFloat()
        }
        return out
    }

    @Suppress("UNCHECKED_CAST")
    private fun flatten(value: Any?, count: Int): FloatArray {
        // Output is [1,100,1]; pull the 100 probabilities out regardless of nesting.
        val flat = FloatArray(count)
        val outer = value as Array<*>            // [1][100][1]
        val mid = outer[0] as Array<*>           // [100][1]
        for (i in 0 until count) {
            val cell = mid[i]
            flat[i] = if (cell is FloatArray) cell[0] else (cell as Number).toFloat()
        }
        return flat
    }

    override fun close() {
        runCatching { session.close() }
    }

    companion object {
        private const val WIN = 100
        private const val W = 48
        private const val H = 27
        private const val FRAME = H * W * 3
        private const val THRESHOLD = 0.5f
    }
}
