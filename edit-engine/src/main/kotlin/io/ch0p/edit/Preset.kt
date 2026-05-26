package io.ch0p.edit

enum class Ordering { HOOK_FIRST, CHRONOLOGICAL, BUILD_TO_CLIMAX }

/** Per-signal selection weights. Normalized to sum to 1 at construction. */
class SignalWeights(raw: Map<Signal, Float>) {
    val map: Map<Signal, Float>

    init {
        val total = raw.values.sum()
        require(total > 0f) { "weights must sum to > 0" }
        map = Signal.entries.associateWith { (raw[it] ?: 0f) / total }
    }

    operator fun get(s: Signal): Float = map[s] ?: 0f

    companion object {
        fun of(
            sceneLength: Float, action: Float, speech: Float, laughter: Float,
            drama: Float, aesthetic: Float, interest: Float,
        ) = SignalWeights(
            mapOf(
                Signal.SCENE_LENGTH to sceneLength, Signal.ACTION to action,
                Signal.SPEECH to speech, Signal.LAUGHTER to laughter,
                Signal.DRAMA to drama, Signal.AESTHETIC to aesthetic,
                Signal.INTEREST to interest,
            )
        )
    }
}

/**
 * A preset is pure configuration. Adding a preset = adding a [Preset] object,
 * never a new code path. Weight vectors and pacing are research-tuned starting points.
 */
data class Preset(
    val id: String,
    val displayName: String,
    val targetDurationMs: LongRange,
    val aspectRatio: String,
    val avgShotLenMs: Long,        // L* in the length-preference curve
    val shotLenSigmaMs: Long,      // σ of that curve
    val minUnitMs: Long,
    val maxUnitMs: Long,
    val ordering: Ordering,
    val weights: SignalWeights,
    val diversityLambda: Float,    // λ_div in the selection objective
    val coverageGamma: Float,      // γ_cov
    val qualityFloor: Float,       // θ — stop padding below this marginal value
    val beatSync: Boolean,
    val defaultTransition: TransitionType,
)

object Presets {
    val TIKTOK = Preset(
        id = "shortform", displayName = "Short-form / TikTok",
        targetDurationMs = 15_000L..45_000L, aspectRatio = "9:16",
        avgShotLenMs = 1_200, shotLenSigmaMs = 900, minUnitMs = 500, maxUnitMs = 3_000,
        ordering = Ordering.HOOK_FIRST,
        weights = SignalWeights.of(0.00f, 0.20f, 0.30f, 0.15f, 0.10f, 0.05f, 0.20f),
        diversityLambda = 0.6f, coverageGamma = 0.15f, qualityFloor = 0.18f,
        beatSync = true, defaultTransition = TransitionType.HARD_CUT,
    )

    val CINEMATIC = Preset(
        id = "cine", displayName = "Cinematic",
        targetDurationMs = 30_000L..60_000L, aspectRatio = "2.39:1",
        avgShotLenMs = 4_500, shotLenSigmaMs = 2_500, minUnitMs = 1_500, maxUnitMs = 8_000,
        ordering = Ordering.BUILD_TO_CLIMAX,
        weights = SignalWeights.of(0.25f, 0.05f, 0.05f, 0.00f, 0.20f, 0.35f, 0.10f),
        diversityLambda = 0.4f, coverageGamma = 0.25f, qualityFloor = 0.22f,
        beatSync = false, defaultTransition = TransitionType.DISSOLVE,
    )

    val PROMO = Preset(
        id = "promo", displayName = "Promotional",
        targetDurationMs = 15_000L..30_000L, aspectRatio = "9:16",
        avgShotLenMs = 1_800, shotLenSigmaMs = 1_200, minUnitMs = 1_000, maxUnitMs = 5_000,
        ordering = Ordering.HOOK_FIRST,
        weights = SignalWeights.of(0.05f, 0.20f, 0.10f, 0.05f, 0.10f, 0.20f, 0.30f),
        diversityLambda = 0.5f, coverageGamma = 0.20f, qualityFloor = 0.20f,
        beatSync = true, defaultTransition = TransitionType.HARD_CUT,
    )

    val VLOG = Preset(
        id = "vlog", displayName = "Vlog / Travel",
        targetDurationMs = 45_000L..90_000L, aspectRatio = "16:9",
        avgShotLenMs = 2_800, shotLenSigmaMs = 1_600, minUnitMs = 1_000, maxUnitMs = 6_000,
        ordering = Ordering.CHRONOLOGICAL,
        weights = SignalWeights.of(0.15f, 0.15f, 0.15f, 0.10f, 0.05f, 0.25f, 0.15f),
        diversityLambda = 0.5f, coverageGamma = 0.30f, qualityFloor = 0.18f,
        beatSync = true, defaultTransition = TransitionType.HARD_CUT,
    )

    val ACTION = Preset(
        id = "action", displayName = "Sports / Action",
        targetDurationMs = 20_000L..40_000L, aspectRatio = "16:9",
        avgShotLenMs = 1_000, shotLenSigmaMs = 800, minUnitMs = 400, maxUnitMs = 3_000,
        ordering = Ordering.BUILD_TO_CLIMAX,
        weights = SignalWeights.of(0.00f, 0.40f, 0.00f, 0.00f, 0.15f, 0.10f, 0.35f),
        diversityLambda = 0.6f, coverageGamma = 0.20f, qualityFloor = 0.20f,
        beatSync = true, defaultTransition = TransitionType.SPEED_RAMP,
    )

    val TALKING_HEAD = Preset(
        id = "talkinghead", displayName = "Talking-head / Podcast",
        targetDurationMs = 30_000L..60_000L, aspectRatio = "9:16",
        avgShotLenMs = 3_500, shotLenSigmaMs = 1_500, minUnitMs = 1_000, maxUnitMs = 7_000,
        ordering = Ordering.HOOK_FIRST,
        weights = SignalWeights.of(0.10f, 0.00f, 0.45f, 0.10f, 0.15f, 0.05f, 0.15f),
        diversityLambda = 0.45f, coverageGamma = 0.15f, qualityFloor = 0.16f,
        beatSync = false, defaultTransition = TransitionType.HARD_CUT,
    )

    /** v1 ship order. */
    val all: List<Preset> = listOf(TIKTOK, CINEMATIC, PROMO, VLOG, ACTION, TALKING_HEAD)

    fun byId(id: String): Preset = all.first { it.id == id }
}
