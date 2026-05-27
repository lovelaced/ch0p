package io.ch0p.models

import android.app.ActivityManager
import android.content.Context
import android.os.Build

/**
 * What the current device can run. Capability-based (not a paywall): a model is offered
 * if the device has the RAM and architecture to run it comfortably.
 *
 * [canRun] is pure given the fields, so gating logic is unit-testable.
 */
data class DeviceCapability(
    val totalRamMb: Int,
    val socModel: String?,
    val abis: List<String>,
) {
    val isArm64: Boolean get() = abis.any { it == "arm64-v8a" }

    /** Heuristic: flagship-class devices carry plenty of RAM. */
    val isFlagshipClass: Boolean get() = totalRamMb >= 8000

    fun canRun(spec: ModelSpec): Boolean {
        if (!isArm64) return false
        // System-provided models (e.g. Gemini Nano) also need an OEM runtime check at call time.
        return totalRamMb >= spec.minRamMb
    }

    fun runnable(): List<ModelSpec> = ModelCatalog.all.filter { canRun(it) }

    /** Features with at least one runnable model on this device. */
    fun availableFeatures(): List<Feature> =
        Feature.entries.filter { f -> ModelCatalog.forFeature(f).any { canRun(it) } }

    companion object {
        fun probe(context: Context): DeviceCapability {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val mem = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
            val totalRamMb = (mem.totalMem / (1024 * 1024)).toInt()
            val soc = if (Build.VERSION.SDK_INT >= 31) Build.SOC_MODEL else null
            return DeviceCapability(
                totalRamMb = totalRamMb,
                socModel = soc,
                abis = Build.SUPPORTED_ABIS.toList(),
            )
        }
    }
}
