package io.ch0p.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Type scale. Numeric/timecode styles use a monospace family with tabular figures
// so digits don't jitter as values change.
// TODO(fonts): swap FontFamily.Default -> Inter and FontFamily.Monospace -> JetBrains Mono
//   via androidx.compose.ui:ui-text-google-fonts (needs res/values/font_certs.xml).
private val Sans = FontFamily.Default
private val Mono = FontFamily.Monospace

@Immutable
data class AppTypography(
    val displayL: TextStyle,
    val titleL: TextStyle,
    val titleM: TextStyle,
    val body: TextStyle,
    val label: TextStyle,
    val micro: TextStyle,     // ALL-CAPS technical labels (callers uppercase the text)
    val timecode: TextStyle,  // mono, tabular figures
    val counter: TextStyle,   // big mono stat readouts
)

private fun style(family: FontFamily, size: Int, weight: FontWeight, line: Int, tracking: Float) =
    TextStyle(
        fontFamily = family,
        fontSize = size.sp,
        fontWeight = weight,
        lineHeight = line.sp,
        letterSpacing = tracking.sp,
    )

val TypographyDefault = AppTypography(
    displayL = style(Sans, 34, FontWeight.SemiBold, 40, -0.5f),
    titleL = style(Sans, 22, FontWeight.SemiBold, 28, -0.3f),
    titleM = style(Sans, 17, FontWeight.SemiBold, 22, -0.2f),
    body = style(Sans, 15, FontWeight.Normal, 22, -0.1f),
    label = style(Sans, 13, FontWeight.Medium, 16, 0.0f),
    micro = style(Sans, 11, FontWeight.SemiBold, 14, 0.8f),
    timecode = style(Mono, 15, FontWeight.Medium, 18, 0.5f),
    counter = style(Mono, 28, FontWeight.Medium, 32, 0.0f),
)

val LocalAppTypography = staticCompositionLocalOf { TypographyDefault }
