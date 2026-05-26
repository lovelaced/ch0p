package io.ch0p.ui.theme

import androidx.compose.ui.unit.dp

// 4dp base grid. Surface tint + hairline borders carry hierarchy; shadows are reserved
// for transient floating layers (sheets, export dialog) only.
object Space {
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val xxl = 48.dp
}

object Radius {
    val sm = 6.dp     // buttons, cards
    val md = 10.dp
    val lg = 16.dp    // preset cards, magic CTA
    val sheet = 20.dp
    val pill = 999.dp
}

val HairlineWidth = 1.dp
