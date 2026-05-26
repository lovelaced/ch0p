package io.ch0p.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.ch0p.edit.EditDecisionList
import io.ch0p.ui.theme.AppTheme
import io.ch0p.ui.theme.HairlineWidth
import io.ch0p.ui.theme.Haptics
import io.ch0p.ui.theme.Radius
import io.ch0p.ui.theme.Space

/**
 * Interactive timeline of the auto-cut: clip blocks sized to duration. Tap to select, then
 * delete or reorder. Edits return a new [EditDecisionList] via [onChange]. (Frame-accurate
 * trim handles are a later refinement.)
 */
@Composable
fun TimelineEditor(edl: EditDecisionList, haptics: Haptics, onChange: (EditDecisionList) -> Unit) {
    val c = AppTheme.colors
    val t = AppTheme.type
    var selected by remember(edl.units.size) { mutableStateOf<Int?>(null) }
    val total = edl.units.sumOf { it.durationMs }.coerceAtLeast(1)

    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        Text("TIMELINE · ${edl.units.size} CLIPS", style = t.micro, color = c.textMid)

        Row(
            Modifier.fillMaxWidth().height(64.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            edl.units.forEachIndexed { i, entry ->
                val isSel = selected == i
                Box(
                    Modifier
                        .weight(entry.durationMs.toFloat() / total)
                        .height(64.dp)
                        .clip(RoundedCornerShape(Radius.sm))
                        .background(if (isSel) c.surface3 else c.surface2)
                        .border(
                            BorderStroke(HairlineWidth, if (isSel) c.accentActive else c.hairline),
                            RoundedCornerShape(Radius.sm),
                        )
                        .clickable { selected = if (isSel) null else i; haptics.clipSnap() }
                        .padding(Space.xs),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("%02d".format(i + 1), style = t.timecode, color = if (isSel) c.accentActive else c.textHi)
                }
            }
        }

        val sel = selected
        if (sel != null && sel in edl.units.indices) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                Action("◀ MOVE", enabled = sel > 0) {
                    onChange(edl.movedClip(sel, sel - 1)); selected = sel - 1; haptics.clipSnap()
                }
                Action("MOVE ▶", enabled = sel < edl.units.size - 1) {
                    onChange(edl.movedClip(sel, sel + 1)); selected = sel + 1; haptics.clipSnap()
                }
                Action("✕ DELETE", enabled = edl.units.size > 1, danger = true) {
                    onChange(edl.withoutClip(sel)); selected = null; haptics.heavy()
                }
            }
            val entry = edl.units[sel]
            Text(
                "clip ${sel + 1} · ${"%.1f".format(entry.durationMs / 1000.0)}s",
                style = t.timecode, color = c.textLow,
            )
        }
    }
}

@Composable
private fun Action(label: String, enabled: Boolean, danger: Boolean = false, onClick: () -> Unit) {
    val c = AppTheme.colors
    val t = AppTheme.type
    val color = when {
        !enabled -> c.textLow
        danger -> c.danger
        else -> c.textHi
    }
    Text(
        label, style = t.micro, color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(Radius.sm))
            .background(c.surface1)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = Space.md, vertical = Space.sm),
    )
}

private fun EditDecisionList.withoutClip(index: Int): EditDecisionList =
    copy(units = units.filterIndexed { i, _ -> i != index }.reindexed())

private fun EditDecisionList.movedClip(from: Int, to: Int): EditDecisionList {
    val list = units.toMutableList()
    list.add(to, list.removeAt(from))
    return copy(units = list.reindexed())
}

private fun List<io.ch0p.edit.EdlEntry>.reindexed() = mapIndexed { i, e -> e.copy(order = i) }
