package io.ch0p.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import io.ch0p.R

// Downloadable Google Fonts: Inter for UI, JetBrains Mono for timecodes/numbers.
private val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

private fun family(name: String, vararg weights: FontWeight) =
    FontFamily(weights.map { Font(googleFont = GoogleFont(name), fontProvider = provider, weight = it) })

private val Sans = family("Inter", FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold)
private val Mono = family("JetBrains Mono", FontWeight.Normal, FontWeight.Medium)

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
