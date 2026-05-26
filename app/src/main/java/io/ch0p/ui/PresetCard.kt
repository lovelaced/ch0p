package io.ch0p.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.ch0p.edit.Preset
import io.ch0p.ui.theme.AppTheme
import io.ch0p.ui.theme.HairlineWidth
import io.ch0p.ui.theme.Radius
import io.ch0p.ui.theme.Space
import java.util.Locale

/** Film-box-style preset card. Reused by the picker and the import flow. */
@Composable
fun PresetCard(preset: Preset, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val c = AppTheme.colors
    val t = AppTheme.type
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.lg))
            .background(c.surface1)
            .border(BorderStroke(HairlineWidth, c.hairline), RoundedCornerShape(Radius.lg))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(Space.md),
        verticalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        Text(preset.displayName.uppercase(Locale.US), style = t.micro, color = c.textHi)
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            Stat(preset.aspectRatio)
            Stat("${preset.targetDurationMs.first / 1000}–${preset.targetDurationMs.last / 1000}s")
            Stat(preset.ordering.name.lowercase(Locale.US).replace('_', ' '))
        }
        Text(
            "avg shot ${"%.1f".format(preset.avgShotLenMs / 1000.0)}s",
            style = t.timecode, color = c.textLow,
        )
    }
}

@Composable
private fun Stat(label: String) {
    val c = AppTheme.colors
    val t = AppTheme.type
    Text(
        text = label,
        style = t.label,
        color = c.textMid,
        modifier = Modifier
            .clip(RoundedCornerShape(Radius.sm))
            .background(c.surface2)
            .padding(horizontal = Space.sm, vertical = 4.dp),
    )
}
