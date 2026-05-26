package io.ch0p.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// --- Studio palette ---------------------------------------------------------
// Dark is primary. Elevation is expressed through surface tint, not shadow.
// Two functional accents only: Amber = active/now, Iris = AI/magic.

val Ink = Color(0xFF0A0B0D)        // app background (near-black)
val Surface1 = Color(0xFF131519)   // cards, sheets
val Surface2 = Color(0xFF1C1F25)   // raised controls, timeline track bed
val Surface3 = Color(0xFF262A32)   // pressed / hover
val Hairline = Color(0x1FFFFFFF)   // 12% white — borders, dividers
val HairlineHi = Color(0x33FFFFFF) // 20% white — focused borders

val TextHi = Color(0xFFF4F5F7)     // porcelain — primary text
val TextMid = Color(0xFFA0A6B0)    // secondary, labels
val TextLow = Color(0xFF6A707C)    // captions, disabled

val Amber = Color(0xFFFFB23E)      // ACTIVE / processing / record / primary CTA
val AmberDim = Color(0xFF6B4A14)   // amber track fill at rest
val Iris = Color(0xFF8B7CFF)       // AI / MAGIC accent
val IrisGlow = Color(0xFFB9AEFF)   // magic highlight / shimmer crest

val Success = Color(0xFF4ED996)    // export complete
val Danger = Color(0xFFFF5C5C)     // destructive / clip delete

// Light theme (secondary, for daylight outdoor shooting)
val LBg = Color(0xFFF7F8F8)
val LSurface = Color(0xFFFFFFFF)
val LHairline = Color(0x14000000)
val LTextHi = Color(0xFF14161A)

@Immutable
data class AppColors(
    val bg: Color,
    val surface1: Color,
    val surface2: Color,
    val surface3: Color,
    val hairline: Color,
    val hairlineHi: Color,
    val textHi: Color,
    val textMid: Color,
    val textLow: Color,
    val accentActive: Color,   // Amber
    val accentActiveDim: Color,
    val accentMagic: Color,    // Iris
    val accentMagicGlow: Color,
    val success: Color,
    val danger: Color,
    val isDark: Boolean,
)

val DarkColors = AppColors(
    bg = Ink, surface1 = Surface1, surface2 = Surface2, surface3 = Surface3,
    hairline = Hairline, hairlineHi = HairlineHi,
    textHi = TextHi, textMid = TextMid, textLow = TextLow,
    accentActive = Amber, accentActiveDim = AmberDim,
    accentMagic = Iris, accentMagicGlow = IrisGlow,
    success = Success, danger = Danger, isDark = true,
)

val LightColors = AppColors(
    bg = LBg, surface1 = LSurface, surface2 = Color(0xFFEFF1F2), surface3 = Color(0xFFE3E6E8),
    hairline = LHairline, hairlineHi = Color(0x29000000),
    textHi = LTextHi, textMid = Color(0xFF55606C), textLow = Color(0xFF8A929C),
    accentActive = Color(0xFFE08A00), accentActiveDim = Color(0xFFF0D7A8),
    accentMagic = Color(0xFF6A5AE0), accentMagicGlow = Color(0xFF8B7CFF),
    success = Color(0xFF1FA56B), danger = Color(0xFFD23C3C), isDark = false,
)

val LocalAppColors = staticCompositionLocalOf { DarkColors }
