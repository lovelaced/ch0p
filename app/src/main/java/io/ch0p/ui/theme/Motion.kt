package io.ch0p.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.Dp

// Settled and weighty motion: nothing bounces frivolously except clip-snap to the grid.
object Dur {
    const val instant = 90
    const val fast = 160
    const val base = 240
    const val slow = 360
    const val cinematic = 560
}

object Ease {
    val standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)       // default — decisive, soft landing
    val emphasized = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)  // reveals, sheet entry
    val exit = CubicBezierEasing(0.4f, 0f, 1f, 1f)           // dismiss
}

object Spr {
    // Clip-snap to grid — taut, slight overshoot.
    fun snap() = spring<Float>(dampingRatio = 0.7f, stiffness = 900f)
    // Panels, selection rings.
    fun settle() = spring<Float>(dampingRatio = 0.85f, stiffness = 380f)
    // Layout size changes, no overshoot.
    fun gentle() = spring<Dp>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 200f)
}
