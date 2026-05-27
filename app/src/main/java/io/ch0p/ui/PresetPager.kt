package io.ch0p.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import io.ch0p.edit.Preset
import io.ch0p.edit.Presets
import io.ch0p.render.AspectRatio
import io.ch0p.ui.theme.AppTheme
import io.ch0p.ui.theme.HairlineWidth
import io.ch0p.ui.theme.Radius
import io.ch0p.ui.theme.Space
import java.util.Locale
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

/**
 * Film-box-style preset carousel. Each card previews your footage in that format's aspect
 * ratio (9:16, 2.39:1, …) with a pacing strip + caption badge, so the differences are
 * visible at a glance. Focused card lifts; an iris "Auto-Edit" CTA commits.
 */
@Composable
fun PresetPager(
    initialPage: Int = 0,
    previewFrame: ImageBitmap? = null,
    onSelect: (Preset) -> Unit,
) {
    val c = AppTheme.colors
    val t = AppTheme.type
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { Presets.all.size })
    val current by remember { derivedStateOf { Presets.all[pagerState.currentPage] } }

    Column {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 44.dp),
            pageSpacing = Space.sm,
        ) { page ->
            val offset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue
            val scale = 0.92f + (1f - offset.coerceIn(0f, 1f)) * 0.08f
            PresetBox(
                preset = Presets.all[page],
                accent = presetAccent(Presets.all[page].id, c.accentActive, c.accentMagic),
                previewFrame = previewFrame,
                modifier = Modifier.graphicsLayer {
                    scaleX = scale; scaleY = scale; alpha = 0.6f + 0.4f * (1f - offset.coerceIn(0f, 1f))
                },
            )
        }

        Row(Modifier.fillMaxWidth().padding(top = Space.md), horizontalArrangement = Arrangement.Center) {
            Presets.all.indices.forEach { i ->
                val on = i == pagerState.currentPage
                Box(
                    Modifier.padding(horizontal = 3.dp).height(6.dp)
                        .width(if (on) 18.dp else 6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (on) c.accentActive else c.surface3),
                )
            }
        }

        Box(
            Modifier.fillMaxWidth().padding(top = Space.lg)
                .clip(RoundedCornerShape(Radius.md))
                .background(c.surface1)
                .border(HairlineWidth, brushMagic(c.accentMagic), RoundedCornerShape(Radius.md))
                .clickable { onSelect(current) }
                .padding(vertical = Space.md),
            contentAlignment = Alignment.Center,
        ) {
            Text("✦  AUTO-EDIT  ${current.displayName.uppercase(Locale.US)}", style = t.titleM, color = c.accentMagic)
        }
    }
}

@Composable
private fun PresetBox(preset: Preset, accent: Color, previewFrame: ImageBitmap?, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    val t = AppTheme.type
    val ratio = AspectRatio.parseOrDefault(preset.aspectRatio)

    Column(
        modifier
            .fillMaxWidth()
            .height(420.dp)
            .clip(RoundedCornerShape(Radius.lg))
            .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.22f), c.surface1, c.bg)))
            .border(HairlineWidth, c.hairlineHi, RoundedCornerShape(Radius.lg))
            .padding(Space.lg),
    ) {
        Text("CH0P · FILM STOCK", style = t.micro, color = accent)

        // Footage previewed in this format's aspect ratio.
        Box(
            Modifier.fillMaxWidth().height(210.dp).padding(vertical = Space.sm),
            contentAlignment = Alignment.Center,
        ) {
            val frameMod = (if (ratio < 1f) Modifier.fillMaxHeight() else Modifier.fillMaxWidth())
                .aspectRatio(ratio)
            Box(
                frameMod.clipToBounds().clip(RoundedCornerShape(Radius.sm))
                    .background(Color.Black)
                    .border(HairlineWidth, c.hairlineHi, RoundedCornerShape(Radius.sm)),
                contentAlignment = Alignment.BottomCenter,
            ) {
                if (previewFrame != null) {
                    Image(previewFrame, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
                PacingStrip(preset, accent, Modifier.fillMaxWidth().padding(Space.xs))
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            Text(preset.displayName.uppercase(Locale.US), style = t.titleL, color = c.textHi)
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                Chip(preset.aspectRatio)
                Chip("${preset.targetDurationMs.first / 1000}–${preset.targetDurationMs.last / 1000}s")
                if (preset.captions) Chip("CC")
            }
            Text("avg shot ${"%.1f".format(preset.avgShotLenMs / 1000.0)}s", style = t.timecode, color = c.textMid)
        }
    }
}

/** Tick density conveys cut pace: many ticks = fast cuts (TikTok/action), few = cinematic. */
@Composable
private fun PacingStrip(preset: Preset, accent: Color, modifier: Modifier = Modifier) {
    val ticks = ((preset.targetDurationMs.last / 1000.0) / (preset.avgShotLenMs / 1000.0))
        .roundToInt().coerceIn(4, 20)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(ticks) {
            Box(Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(accent.copy(alpha = 0.85f)))
        }
    }
}

@Composable
private fun Chip(label: String) {
    val c = AppTheme.colors
    val t = AppTheme.type
    Text(
        label, style = t.label, color = c.textHi,
        modifier = Modifier
            .clip(RoundedCornerShape(Radius.sm))
            .background(Color.Black.copy(alpha = 0.35f))
            .padding(horizontal = Space.sm, vertical = 4.dp),
    )
}

private fun presetAccent(id: String, amber: Color, iris: Color): Color = when (id) {
    "cine" -> iris
    "talkinghead" -> iris
    else -> amber
}

private fun brushMagic(c: Color) = Brush.horizontalGradient(listOf(c.copy(alpha = 0.7f), c))
