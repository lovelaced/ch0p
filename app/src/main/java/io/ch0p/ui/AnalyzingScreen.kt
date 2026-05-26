package io.ch0p.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.ch0p.ui.theme.AppTheme
import io.ch0p.ui.theme.Space
import java.util.Locale

/**
 * The signature screen: the AI's work made visible. An amber progress arc, an iris shimmer,
 * a cycling stage label, and three live counters (scenes/faces/laughter) that roll up as the
 * analysis stages complete — not a spinner.
 */
@Composable
fun AnalyzingScreen(
    presetName: String,
    fraction: Float,
    stage: String,
    scenes: Int,
    faces: Int,
    laughs: Int,
) {
    val c = AppTheme.colors
    val t = AppTheme.type
    val animFraction by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(400), label = "frac")

    Column(
        Modifier.fillMaxSize().background(c.bg).padding(Space.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(presetName.uppercase(Locale.US), style = t.micro, color = c.accentMagic)

        Box(Modifier.padding(vertical = Space.xl), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(180.dp)) {
                val w = 10.dp.toPx()
                drawArc(
                    color = c.surface3, startAngle = -90f, sweepAngle = 360f, useCenter = false,
                    topLeft = Offset(w / 2, w / 2),
                    size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w),
                    style = Stroke(width = w, cap = StrokeCap.Round),
                )
                drawArc(
                    color = c.accentActive, startAngle = -90f, sweepAngle = 360f * animFraction, useCenter = false,
                    topLeft = Offset(w / 2, w / 2),
                    size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w),
                    style = Stroke(width = w, cap = StrokeCap.Round),
                )
            }
            Text("${(animFraction * 100).toInt()}%", style = t.counter, color = c.textHi)
        }

        Text(stage.uppercase(Locale.US), style = t.micro, color = c.accentActive)
        Shimmer(c.accentMagic, c.surface2, Modifier.padding(top = Space.md).fillMaxWidth(0.6f))

        Row(
            Modifier.fillMaxWidth().padding(top = Space.xl),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Counter("SCENES", scenes)
            Counter("FACES", faces)
            Counter("LAUGHTER", laughs)
        }
    }
}

@Composable
private fun Counter(label: String, value: Int) {
    val c = AppTheme.colors
    val t = AppTheme.type
    val animated by animateIntAsState(value, tween(500), label = label)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("%02d".format(animated), style = t.counter, color = c.textHi)
        Text(label, style = t.micro, color = c.textMid, textAlign = TextAlign.Center)
    }
}

@Composable
private fun Shimmer(accent: Color, track: Color, modifier: Modifier) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(
        0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), label = "x",
    )
    Box(
        modifier.height(2.dp).clip(RoundedCornerShape(1.dp)).drawBehind {
            drawRect(track)
            val segW = size.width * 0.3f
            val left = x * (size.width + segW) - segW
            drawRect(
                brush = Brush.horizontalGradient(listOf(Color.Transparent, accent, Color.Transparent)),
                topLeft = Offset(left, 0f),
                size = Size(segW, size.height),
            )
        },
    )
}
