package io.ch0p.analysis

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer

/**
 * Silero VAD v5 via ONNX Runtime — music/noise-robust speech probability, the quality
 * upgrade over the energy VAD. Processes 16 kHz audio in 512-sample chunks with a 64-sample
 * context window and carries the [2,1,128] recurrent state between chunks.
 *
 * Device-only; constructed only when the model is installed. The caller falls back to the
 * energy [Vad] if this throws (so a subtle ONNX issue never breaks the pipeline).
 */
class SileroVad(modelPath: String) : AutoCloseable {

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession = env.createSession(File(modelPath).readBytes())

    /** @return speech probability per 512-sample (~32 ms) chunk. */
    fun speechCurve(pcm16k: ShortArray): FloatArray {
        if (pcm16k.isEmpty()) return FloatArray(0)
        val probs = ArrayList<Float>(pcm16k.size / CHUNK + 1)
        var state = FloatArray(STATE_LEN)
        var context = FloatArray(CONTEXT)
        val srTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(longArrayOf(16_000L)), longArrayOf())

        try {
            var i = 0
            while (i < pcm16k.size) {
                val chunk = FloatArray(CHUNK) { j ->
                    val idx = i + j
                    if (idx < pcm16k.size) pcm16k[idx] / 32768f else 0f
                }
                val input = FloatArray(CONTEXT + CHUNK)
                System.arraycopy(context, 0, input, 0, CONTEXT)
                System.arraycopy(chunk, 0, input, CONTEXT, CHUNK)

                val inputT = OnnxTensor.createTensor(env, FloatBuffer.wrap(input), longArrayOf(1, (CONTEXT + CHUNK).toLong()))
                val stateT = OnnxTensor.createTensor(env, FloatBuffer.wrap(state), longArrayOf(2, 1, 128))
                val result = session.run(mapOf("input" to inputT, "state" to stateT, "sr" to srTensor))

                @Suppress("UNCHECKED_CAST")
                val prob = (result.get(0).value as Array<FloatArray>)[0][0]
                probs.add(prob)
                @Suppress("UNCHECKED_CAST")
                state = flatten(result.get(1).value as Array<Array<FloatArray>>)
                context = chunk.copyOfRange(CHUNK - CONTEXT, CHUNK)

                result.close(); inputT.close(); stateT.close()
                i += CHUNK
            }
        } finally {
            srTensor.close()
        }
        return probs.toFloatArray()
    }

    override fun close() {
        runCatching { session.close() }
    }

    private fun flatten(s: Array<Array<FloatArray>>): FloatArray {
        val out = FloatArray(STATE_LEN)
        var k = 0
        for (a in s) for (b in a) for (v in b) out[k++] = v
        return out
    }

    private companion object {
        const val CHUNK = 512
        const val CONTEXT = 64
        const val STATE_LEN = 2 * 1 * 128
    }
}
