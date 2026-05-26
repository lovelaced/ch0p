package io.ch0p.ui.theme

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

// Semantic haptics mapped to HapticFeedbackConstants via the View API (richer than the
// Compose enum). Newer constants are gated by API level; minSdk is 31.
@Immutable
class Haptics(private val view: View?) {
    private fun perform(constant: Int) {
        view?.performHapticFeedback(constant)
    }

    /** Timeline scrub crossing a detent (gridline / detected-event marker). */
    fun scrubTick() = perform(
        if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.SEGMENT_TICK
        else HapticFeedbackConstants.CLOCK_TICK
    )

    /** A clip snaps to the playhead or an adjacent clip edge. */
    fun clipSnap() = perform(HapticFeedbackConstants.CONTEXT_CLICK)

    /** Trim handle hits a clip boundary and can't go further. */
    fun boundaryReject() = perform(HapticFeedbackConstants.REJECT)

    /** Preset selected. */
    fun confirm() = perform(HapticFeedbackConstants.CONFIRM)

    /** Destructive action (delete clip). */
    fun heavy() = perform(HapticFeedbackConstants.LONG_PRESS)
}

val LocalHaptics = staticCompositionLocalOf { Haptics(null) }
