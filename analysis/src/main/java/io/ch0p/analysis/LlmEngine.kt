package io.ch0p.analysis

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference

/**
 * On-device LLM (Gemma) via the MediaPipe LLM Inference API. Full prompt control — used
 * for semantic clip selection and title generation. Construct only when a `.task` model is
 * installed; device-only, flagship-class RAM. Reuse one instance across prompts.
 */
class LlmEngine(context: Context, modelPath: String, maxTokens: Int = 512) : AutoCloseable {

    private val llm: LlmInference = LlmInference.createFromOptions(
        context,
        // topK/temperature moved to session options in recent MediaPipe; defaults are fine here.
        LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelPath)
            .setMaxTokens(maxTokens)
            .build(),
    )

    fun generate(prompt: String): String = llm.generateResponse(prompt)

    override fun close() = llm.close()
}
