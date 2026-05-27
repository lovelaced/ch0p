package io.ch0p.analysis

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * CLIP-IQA aesthetic/quality scorer (the modern replacement for NIMA). Runs a CLIP ViT-B/32
 * image encoder (ONNX, downloaded) to a 512-d embedding, then scores it against antonym prompt
 * pairs ("Good photo."/"Bad photo." …) whose text embeddings were generated offline and bundled
 * as an asset — so no tokenizer or text model is needed on-device, just the image encoder + dot
 * products. Returns technical-quality, aesthetic, and interest scores in 0..1.
 *
 * Device-only (ONNX Runtime). Constructed only when the model is installed; the caller falls
 * back to NIMA/classical aesthetics if this throws.
 */
class ClipScorer(context: Context, modelPath: String) : AutoCloseable {

    data class Scores(val quality: Float, val aesthetic: Float, val interest: Float)

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession = env.createSession(File(modelPath).readBytes())

    // Prompt-pair text embeddings (pos,neg interleaved), generated offline; see tools/gen.
    private val dim: Int
    private val vectors: Array<FloatArray>  // size = nPairs*2

    init {
        val bytes = context.assets.open(PROMPTS_ASSET).readBytes()
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val n = bb.int
        dim = bb.int
        vectors = Array(n) { FloatArray(dim) { bb.float } }
    }

    val isReady: Boolean get() = vectors.isNotEmpty()

    fun score(bitmap: Bitmap): Scores {
        val emb = embed(bitmap)
        fun pairProb(pair: Int): Float {
            val lp = dot(emb, vectors[2 * pair]) * LOGIT_SCALE
            val ln = dot(emb, vectors[2 * pair + 1]) * LOGIT_SCALE
            return 1f / (1f + exp(ln - lp))  // 2-way softmax → P(positive)
        }
        val quality = (QUALITY_PAIRS).map { pairProb(it) }.average().toFloat()
        val aesthetic = (AESTHETIC_PAIRS).map { pairProb(it) }.average().toFloat()
        val interest = (INTEREST_PAIRS).map { pairProb(it) }.average().toFloat()
        return Scores(quality, aesthetic, interest)
    }

    private fun embed(bitmap: Bitmap): FloatArray {
        val scaled = Bitmap.createScaledBitmap(bitmap, SIZE, SIZE, true)
        val px = IntArray(SIZE * SIZE)
        scaled.getPixels(px, 0, SIZE, 0, 0, SIZE, SIZE)
        if (scaled != bitmap) scaled.recycle()
        // CLIP preprocessing: /255, normalize per channel, layout NCHW.
        val buf = FloatArray(3 * SIZE * SIZE)
        val plane = SIZE * SIZE
        for (i in 0 until plane) {
            val p = px[i]
            val r = ((p shr 16) and 0xFF) / 255f
            val g = ((p shr 8) and 0xFF) / 255f
            val b = (p and 0xFF) / 255f
            buf[i] = (r - MEAN[0]) / STD[0]
            buf[plane + i] = (g - MEAN[1]) / STD[1]
            buf[2 * plane + i] = (b - MEAN[2]) / STD[2]
        }
        val t = OnnxTensor.createTensor(env, FloatBuffer.wrap(buf), longArrayOf(1, 3, SIZE.toLong(), SIZE.toLong()))
        try {
            val result = session.run(mapOf("pixel_values" to t))
            @Suppress("UNCHECKED_CAST")
            val out = (result.get(0).value as Array<FloatArray>)[0]
            result.close()
            val norm = sqrt(out.sumOf { (it * it).toDouble() }).toFloat().coerceAtLeast(1e-6f)
            return FloatArray(out.size) { out[it] / norm }
        } finally {
            t.close()
        }
    }

    private fun dot(a: FloatArray, b: FloatArray): Float {
        var s = 0f
        for (i in a.indices) s += a[i] * b[i]
        return s
    }

    override fun close() {
        runCatching { session.close() }
    }

    companion object {
        private const val PROMPTS_ASSET = "clip_iqa_prompts.bin"
        private const val SIZE = 224
        private const val LOGIT_SCALE = 100f
        private val MEAN = floatArrayOf(0.48145466f, 0.4578275f, 0.40821073f)
        private val STD = floatArrayOf(0.26862954f, 0.26130258f, 0.27577711f)
        // Pair indices within the bundled asset (PAIRS order from the generator).
        private val QUALITY_PAIRS = listOf(0, 1, 2, 3)
        private val AESTHETIC_PAIRS = listOf(4, 5, 6)
        private val INTEREST_PAIRS = listOf(7, 8, 9)
    }
}
