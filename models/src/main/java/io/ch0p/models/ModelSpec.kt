package io.ch0p.models

/** Premium capabilities that an optional downloaded model unlocks. */
enum class Feature(val displayName: String, val description: String) {
    AUTO_CAPTIONS("Auto captions", "Word-timed karaoke subtitles + speech-aware cuts (Whisper)"),
    ROBUST_VAD("Robust speech detection", "Music/noise-robust voice activity (Silero)"),
    LAUGHTER_DETECTION("Laughter & reactions", "Detect laughter, applause, music, cheering (YAMNet)"),
    AESTHETIC_SCORING("Cinematic scoring", "Learned shot-quality / aesthetic score (NIMA)"),
    FACE_TRACKING("Face & expression", "Faces = interest, active-speaker, expression intensity"),
    SMART_REFRAME("Smart reframe", "Subject-tracking vertical reframe (AutoFlip-style)"),
    POSE_ACTION("Action recognition", "Pose / semantic action understanding (MoViNet / Pose)"),
    SCENE_ML("Precise scene cuts", "ML shot-boundary incl. dissolves (TransNetV2)"),
    SPEECH_EMOTION("Emotion / arousal", "Speech arousal for the drama signal"),
    SEMANTIC_SELECTION("Smart selection", "On-device LLM picks hooks/quotes, writes titles"),
}

enum class ModelRuntime { TFLITE, ONNX, MEDIAPIPE_TASK, MEDIAPIPE_LLM, GGML, LLM_GGUF, SYSTEM }

/**
 * A downloadable (or system-provided) model.
 *
 * NOTE: [sha256] must be pinned by downloading each artifact once and hashing it before
 * shipping; left null here so [ModelStore] verifies only when a hash is present. [url]s
 * point at the canonical sources cited in research — re-verify on integration.
 */
data class ModelSpec(
    val id: String,
    val displayName: String,
    val feature: Feature,
    val runtime: ModelRuntime,
    val url: String?,            // null for SYSTEM-provided (e.g. Gemini Nano via AICore)
    val approxSizeBytes: Long,
    val license: String,
    val minRamMb: Int,           // device RAM floor to run comfortably
    val prefersNpu: Boolean = false,
    val recommended: Boolean = false,  // the default pick for its feature
    val sha256: String? = null,  // pin before shipping
) {
    val isSystemProvided: Boolean get() = runtime == ModelRuntime.SYSTEM
}

/** The full premium model catalog. Capability-based: a device pulls what it can run. */
object ModelCatalog {

    private const val MB = 1024L * 1024L

    val all: List<ModelSpec> = listOf(
        ModelSpec(
            "whisper-base-q5", "Whisper base (captions)", Feature.AUTO_CAPTIONS, ModelRuntime.GGML,
            "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base-q5_1.bin",
            57 * MB, "MIT", minRamMb = 3000,
        ),
        ModelSpec(
            "whisper-small-q5", "Whisper small (recommended)", Feature.AUTO_CAPTIONS, ModelRuntime.GGML,
            "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small-q5_1.bin",
            182 * MB, "MIT", minRamMb = 6000, prefersNpu = true, recommended = true,
        ),
        ModelSpec(
            "silero-vad", "Silero VAD", Feature.ROBUST_VAD, ModelRuntime.ONNX,
            "https://github.com/snakers4/silero-vad/raw/master/src/silero_vad/data/silero_vad.onnx",
            2 * MB, "MIT", minRamMb = 2000,
        ),
        ModelSpec(
            "yamnet", "YAMNet audio events", Feature.LAUGHTER_DETECTION, ModelRuntime.MEDIAPIPE_TASK,
            "https://storage.googleapis.com/mediapipe-models/audio_classifier/yamnet/float32/latest/yamnet.tflite",
            4 * MB, "Apache-2.0", minRamMb = 2000,
        ),
        ModelSpec(
            "nima-mobilenet", "NIMA aesthetic", Feature.AESTHETIC_SCORING, ModelRuntime.TFLITE,
            null,  // convert from titu1994/neural-image-assessment (MIT) -> int8 tflite; pin url+hash
            5 * MB, "MIT", minRamMb = 3000,
        ),
        ModelSpec(
            "blazeface-short", "Face detector", Feature.FACE_TRACKING, ModelRuntime.MEDIAPIPE_TASK,
            "https://storage.googleapis.com/mediapipe-models/face_detector/blaze_face_short_range/float16/latest/blaze_face_short_range.tflite",
            1 * MB, "Apache-2.0", minRamMb = 2000,
        ),
        ModelSpec(
            "face-landmarker", "Face landmarks + blendshapes", Feature.FACE_TRACKING, ModelRuntime.MEDIAPIPE_TASK,
            "https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/latest/face_landmarker.task",
            4 * MB, "Apache-2.0", minRamMb = 3000,
        ),
        ModelSpec(
            "efficientdet-lite0", "Object detector", Feature.SMART_REFRAME, ModelRuntime.MEDIAPIPE_TASK,
            "https://storage.googleapis.com/mediapipe-models/object_detector/efficientdet_lite0/float16/latest/efficientdet_lite0.tflite",
            7 * MB, "Apache-2.0", minRamMb = 3000,
        ),
        ModelSpec(
            "pose-landmarker-lite", "Pose landmarks", Feature.POSE_ACTION, ModelRuntime.MEDIAPIPE_TASK,
            "https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/latest/pose_landmarker_lite.task",
            3 * MB, "Apache-2.0", minRamMb = 3000,
        ),
        ModelSpec(
            "movinet-a0-stream", "MoViNet action", Feature.POSE_ACTION, ModelRuntime.TFLITE,
            null,  // from TF Hub movinet a0 stream int8; pin url+hash
            5 * MB, "Apache-2.0", minRamMb = 4000, prefersNpu = true,
        ),
        ModelSpec(
            "transnetv2", "TransNetV2 shot cuts", Feature.SCENE_ML, ModelRuntime.TFLITE,
            null,  // convert soCzech/TransNetV2 weights -> tflite; pin url+hash
            8 * MB, "MIT", minRamMb = 4000, prefersNpu = true,
        ),
        ModelSpec(
            "wav2small", "Speech arousal", Feature.SPEECH_EMOTION, ModelRuntime.ONNX,
            null,  // distill audeering wav2vec2 -> Wav2Small onnx; pin url+hash
            1 * MB, "research", minRamMb = 2000,
        ),
        ModelSpec(
            "gemini-nano", "Gemini Nano (on-device LLM)", Feature.SEMANTIC_SELECTION, ModelRuntime.SYSTEM,
            null,  // system-provided via AICore on supported Pixel/Samsung; not a download
            0, "OEM", minRamMb = 6000, prefersNpu = true,
        ),
        ModelSpec(
            "gemma3-1b", "Gemma 3 1B (on-device LLM)", Feature.SEMANTIC_SELECTION, ModelRuntime.MEDIAPIPE_LLM,
            null,  // license-gated (Kaggle/HuggingFace) — user accepts terms + supplies the .task
            555 * MB, "Gemma", minRamMb = 6000, prefersNpu = true,
        ),
    )

    fun byId(id: String): ModelSpec? = all.firstOrNull { it.id == id }

    fun forFeature(feature: Feature): List<ModelSpec> = all.filter { it.feature == feature }

    /** The default/preferred model for a feature (flagged [ModelSpec.recommended], else the first). */
    fun recommendedFor(feature: Feature): ModelSpec? =
        forFeature(feature).firstOrNull { it.recommended } ?: forFeature(feature).firstOrNull()

    /** Features unlocked by the given installed model ids. Pure — JVM-testable. */
    fun featuresFor(installedIds: Set<String>): List<Feature> =
        Feature.entries.filter { f -> forFeature(f).any { it.id in installedIds } }
}
