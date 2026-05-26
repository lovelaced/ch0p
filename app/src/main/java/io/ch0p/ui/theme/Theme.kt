package io.ch0p.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

// Convenience accessors so screens read tokens declaratively:
//   AppTheme.colors.accentActive, AppTheme.type.timecode, etc.
object AppTheme {
    val colors: AppColors
        @Composable get() = LocalAppColors.current
    val type: AppTypography
        @Composable get() = LocalAppTypography.current
    val haptics: Haptics
        @Composable get() = LocalHaptics.current
}

@Composable
fun StudioTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (dark) DarkColors else LightColors
    val view = LocalView.current
    val haptics = remember(view) { Haptics(view) }

    // Material 3 is used as plumbing (ripple, sheets, windowing). Our own tokens drive the look.
    val m3 = if (dark) {
        darkColorScheme(
            background = colors.bg,
            surface = colors.surface1,
            primary = colors.accentActive,
            secondary = colors.accentMagic,
            error = colors.danger,
            onBackground = colors.textHi,
            onSurface = colors.textHi,
        )
    } else {
        lightColorScheme(
            background = colors.bg,
            surface = colors.surface1,
            primary = colors.accentActive,
            secondary = colors.accentMagic,
            error = colors.danger,
            onBackground = colors.textHi,
            onSurface = colors.textHi,
        )
    }

    CompositionLocalProvider(
        LocalAppColors provides colors,
        LocalAppTypography provides TypographyDefault,
        LocalHaptics provides haptics,
    ) {
        MaterialTheme(colorScheme = m3, content = content)
    }
}
