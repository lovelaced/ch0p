package io.ch0p.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.ch0p.models.DeviceCapability
import io.ch0p.models.Feature
import io.ch0p.models.ModelCatalog
import io.ch0p.models.ModelSpec
import io.ch0p.models.ModelStore
import io.ch0p.ui.theme.AppTheme
import io.ch0p.ui.theme.Radius
import io.ch0p.ui.theme.Space
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Capability-based model manager: shows what this device can run and lets the user pull
 * premium models on demand. No paywall — gated only by device RAM/architecture.
 */
@Composable
fun ModelsScreen(onBack: () -> Unit) {
    val c = AppTheme.colors
    val t = AppTheme.type
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cap = remember { DeviceCapability.probe(context) }
    val store = remember { ModelStore(context) }

    var installed by remember { mutableStateOf(store.installed().map { it.id }.toSet()) }
    var downloadingId by remember { mutableStateOf<String?>(null) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var pendingImport by remember { mutableStateOf<ModelSpec?>(null) }

    // Import a model file from storage (for license-gated / unhosted models like NIMA or Gemma).
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val spec = pendingImport
        pendingImport = null
        if (uri == null || spec == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        store.fileFor(spec).outputStream().use { input.copyTo(it) }
                    }
                }
            }
            installed = store.installed().map { it.id }.toSet()
        }
    }

    fun importModel(spec: ModelSpec) {
        pendingImport = spec
        importLauncher.launch(arrayOf("*/*"))
    }

    fun startDownload(spec: ModelSpec) {
        downloadingId = spec.id
        downloadProgress = 0f
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    store.download(spec, progress = { downloadProgress = it })
                }
            }
            installed = store.installed().map { it.id }.toSet()
            downloadingId = null
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(c.bg).padding(horizontal = Space.md),
        verticalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        item {
            Column(Modifier.padding(top = Space.xxl, bottom = Space.md)) {
                Text("← BACK", style = t.micro, color = c.accentActive,
                    modifier = Modifier.clickable(onClick = onBack))
                Text("Models", style = t.displayL, color = c.textHi,
                    modifier = Modifier.padding(top = Space.sm))
                Text(
                    deviceSummary(cap),
                    style = t.body, color = c.textMid, modifier = Modifier.padding(top = Space.xs),
                )
                val active = ModelCatalog.featuresFor(installed)
                Text(
                    if (active.isEmpty()) "No premium models installed — download below"
                    else "ACTIVE: " + active.joinToString(" · ") { it.displayName },
                    style = t.label,
                    color = if (active.isEmpty()) c.textLow else c.success,
                    modifier = Modifier.padding(top = Space.xs),
                )
            }
        }

        for (feature in Feature.entries) {
            item {
                Column(Modifier.padding(top = Space.sm)) {
                    Text(feature.displayName.uppercase(Locale.US), style = t.micro, color = c.accentMagic)
                    Text(feature.description, style = t.label, color = c.textLow,
                        modifier = Modifier.padding(top = 2.dp))
                }
            }
            for (spec in ModelCatalog.forFeature(feature)) {
                item {
                    ModelRow(
                        spec = spec,
                        canRun = cap.canRun(spec),
                        installed = spec.id in installed,
                        downloading = downloadingId == spec.id,
                        progress = downloadProgress,
                        onDownload = { startDownload(spec) },
                        onImport = { importModel(spec) },
                        onDelete = { store.delete(spec); installed = store.installed().map { it.id }.toSet() },
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelRow(
    spec: ModelSpec,
    canRun: Boolean,
    installed: Boolean,
    downloading: Boolean,
    progress: Float,
    onDownload: () -> Unit,
    onImport: () -> Unit,
    onDelete: () -> Unit,
) {
    val c = AppTheme.colors
    val t = AppTheme.type
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(c.surface1)
            .padding(Space.md),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                Text(spec.displayName, style = t.titleM, color = if (canRun) c.textHi else c.textLow)
                if (spec.recommended) Text(
                    "RECOMMENDED", style = t.micro, color = c.accentMagic,
                    modifier = Modifier.clip(RoundedCornerShape(Radius.sm))
                        .background(c.surface2).padding(horizontal = Space.xs, vertical = 2.dp),
                )
            }
            Text(
                "${formatMb(spec.approxSizeBytes)} · ${spec.license}",
                style = t.timecode, color = c.textLow,
            )
        }
        Column(Modifier.width(120.dp), horizontalAlignment = Alignment.End) {
            when {
                spec.isSystemProvided ->
                    Text(if (canRun) "SYSTEM" else "UNSUPPORTED", style = t.micro, color = c.textMid)
                downloading -> LinearProgressIndicator(
                    progress = { if (progress >= 0f) progress else 0f },
                    color = c.accentActive, trackColor = c.surface2,
                    modifier = Modifier.fillMaxWidth(),
                )
                installed -> Text("INSTALLED · DELETE", style = t.micro, color = c.success,
                    modifier = Modifier.clickable(onClick = onDelete))
                !canRun -> Text("NEEDS ${spec.minRamMb / 1000}GB", style = t.micro, color = c.textLow)
                spec.url == null -> Text("IMPORT", style = t.micro, color = c.accentActive,
                    modifier = Modifier.clickable(onClick = onImport))
                else -> Text("DOWNLOAD", style = t.micro, color = c.accentActive,
                    modifier = Modifier.clickable(onClick = onDownload))
            }
        }
    }
}

private fun deviceSummary(cap: DeviceCapability): String {
    val ram = "%.0f GB RAM".format(cap.totalRamMb / 1000.0)
    val tier = if (cap.isFlagshipClass) "flagship-class" else "standard"
    val arch = if (cap.isArm64) "arm64" else cap.abis.firstOrNull().orEmpty()
    return "$ram · $tier · $arch${cap.socModel?.let { " · $it" } ?: ""}"
}

private fun formatMb(bytes: Long): String =
    if (bytes <= 0) "system" else "%.0f MB".format(bytes / (1024.0 * 1024.0))
