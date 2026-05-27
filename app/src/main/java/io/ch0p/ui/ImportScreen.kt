package io.ch0p.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.ch0p.analysis.AnalysisPipeline
import io.ch0p.analysis.LlmEngine
import io.ch0p.analysis.SemanticEditor
import io.ch0p.edit.AutoEditor
import io.ch0p.edit.EditDecisionList
import io.ch0p.edit.Preset
import io.ch0p.edit.captions.CaptionChunk
import io.ch0p.edit.captions.CaptionChunker
import io.ch0p.edit.reframe.CropKeyframe
import io.ch0p.edit.Presets
import io.ch0p.ingest.CodecSupport
import io.ch0p.ingest.IngestRisk
import io.ch0p.ingest.MediaProbe
import io.ch0p.ingest.ProxyGenerator
import io.ch0p.ingest.ProxySpec
import io.ch0p.ingest.VideoMetadata
import io.ch0p.ingest.telemetry.TelemetryExtractor
import io.ch0p.models.Feature
import io.ch0p.models.ModelCatalog
import io.ch0p.models.ModelSpec
import io.ch0p.models.ModelStore
import io.ch0p.render.AspectRatio
import io.ch0p.render.VideoRenderer
import io.ch0p.ui.theme.AppTheme
import io.ch0p.ui.theme.HairlineWidth
import io.ch0p.ui.theme.Radius
import io.ch0p.ui.theme.Space
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

private sealed interface ImportUi {
    data object Idle : ImportUi
    data object Probing : ImportUi
    data class Probed(val uri: Uri, val meta: VideoMetadata, val risk: IngestRisk) : ImportUi
    data class Proxying(val meta: VideoMetadata) : ImportUi
    data class Ready(val meta: VideoMetadata, val proxy: File) : ImportUi
    data class Analyzing(val presetName: String) : ImportUi
    data class Edited(
        val preset: Preset,
        val edl: EditDecisionList,
        val title: String? = null,
        val cropTrajectory: List<CropKeyframe> = emptyList(),
        val captionChunks: List<CaptionChunk> = emptyList(),
    ) : ImportUi
    data class Rendering(val presetName: String) : ImportUi
    data class Rendered(val output: File) : ImportUi
    data class Failed(val message: String) : ImportUi
}

@Composable
fun ImportScreen(initialVideo: Uri? = null, onOpenModels: () -> Unit = {}) {
    val haptics = AppTheme.haptics
    val c = AppTheme.colors
    val t = AppTheme.type
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val proxyGen = remember { ProxyGenerator(context) }
    val renderer = remember { VideoRenderer(context) }
    val store = remember { ModelStore(context) }
    val installedWhisper = remember { ModelCatalog.forFeature(Feature.AUTO_CAPTIONS).filter { store.isInstalled(it) } }

    var ui by remember { mutableStateOf<ImportUi>(ImportUi.Idle) }
    var sourceUri by remember { mutableStateOf<Uri?>(null) }
    // Transcription model for this clip: null = off (e.g. no speech). Defaults to best installed.
    var whisperModelId by remember { mutableStateOf(installedWhisper.lastOrNull()?.id) }
    var proxyProgress by remember { mutableFloatStateOf(0f) }
    var analyzeProgress by remember { mutableFloatStateOf(0f) }
    var analyzeStage by remember { mutableStateOf("") }
    var analyzeScenes by remember { mutableIntStateOf(0) }
    var analyzeFaces by remember { mutableIntStateOf(0) }
    var analyzeLaughs by remember { mutableIntStateOf(0) }
    var renderProgress by remember { mutableFloatStateOf(0f) }

    fun startProxy(uri: Uri, meta: VideoMetadata) {
        ui = ImportUi.Proxying(meta)
        proxyProgress = 0f
        proxyGen.start(
            uri, ProxySpec.forSource(meta.width, meta.height, fps = meta.frameRate),
            object : ProxyGenerator.Callback {
                override fun onComplete(proxyFile: File) { ui = ImportUi.Ready(meta, proxyFile) }
                override fun onError(message: String, cause: Throwable?) { ui = ImportUi.Failed(message) }
            },
        )
    }

    // Share-in / pick → probe → (auto) proxy, no extra taps.
    fun beginImport(uri: Uri) {
        sourceUri = uri
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ui = ImportUi.Probing
        scope.launch {
            val probed = runCatching {
                val meta = withContext(Dispatchers.IO) { MediaProbe.probe(context, uri) }
                meta to CodecSupport.assessRisk(meta)
            }.getOrNull()
            if (probed == null) { ui = ImportUi.Failed("Could not read this file"); return@launch }
            val (meta, risk) = probed
            if (!risk.isUsable) { ui = ImportUi.Failed(risk.reasons.joinToString("\n")); return@launch }
            startProxy(uri, meta)
        }
    }

    fun runRender(
        preset: Preset,
        edl: EditDecisionList,
        cropTrajectory: List<CropKeyframe>,
        captionChunks: List<CaptionChunk>,
    ) {
        val src = sourceUri ?: run { ui = ImportUi.Failed("Lost the source file"); return }
        ui = ImportUi.Rendering(preset.displayName)
        renderProgress = 0f
        renderer.start(
            src, edl, AspectRatio.parseOrDefault(preset.aspectRatio), cropTrajectory, captionChunks,
            object : VideoRenderer.Callback {
                override fun onComplete(output: File) { ui = ImportUi.Rendered(output) }
                override fun onError(message: String, cause: Throwable?) { ui = ImportUi.Failed(message) }
            },
        )
    }

    fun runEdit(meta: VideoMetadata, proxy: File, preset: Preset) {
        ui = ImportUi.Analyzing(preset.displayName)
        analyzeProgress = 0f
        analyzeStage = "Starting"
        scope.launch {
            ui = runCatching {
                withContext(Dispatchers.IO) {
                    // Telemetry comes from the ORIGINAL (the proxy transcode drops the gpmd track).
                    val telemetry = sourceUri?.let {
                        runCatching { TelemetryExtractor.extract(context, it) }.getOrNull()
                    }
                    // Optional models: pass a path only if installed (else the channel stays silent).
                    fun pathIfInstalled(id: String) =
                        ModelCatalog.byId(id)?.takeIf { store.isInstalled(it) }?.let { store.fileFor(it).absolutePath }
                    val yamnet = pathIfInstalled("yamnet")
                    val whisper = whisperModelId?.let { pathIfInstalled(it) }  // null = transcription off
                    val face = pathIfInstalled("blazeface-short")
                    val silero = pathIfInstalled("silero-vad")
                    val nima = pathIfInstalled("nima-mobilenet")
                    val analysis = AnalysisPipeline.analyze(
                        context, proxy.absolutePath, meta.durationMs, meta.frameRate,
                        telemetry = telemetry, audioEventsModelPath = yamnet,
                        whisperModelPath = whisper, faceModelPath = face,
                        sileroModelPath = silero, nimaModelPath = nima,
                    ) { p ->
                        analyzeProgress = p.fraction; analyzeStage = p.stage
                        analyzeScenes = p.scenes; analyzeFaces = p.faces; analyzeLaughs = p.laughs
                    }
                    val edl = AutoEditor.edit(analysis, preset)
                    // Optional on-device LLM title (Gemma), if installed and we have a transcript.
                    val title = pathIfInstalled("gemma3-1b")
                        ?.takeIf { analysis.words.isNotEmpty() }
                        ?.let { p ->
                            analyzeStage = "Writing title"
                            runCatching { LlmEngine(context, p).use { SemanticEditor(it).analyze(analysis.words).title } }
                                .getOrNull()?.takeIf { it.isNotBlank() }
                        }
                    val captions = if (preset.captions && analysis.words.isNotEmpty())
                        CaptionChunker.chunk(analysis.words) else emptyList()
                    ImportUi.Edited(preset, edl, title, analysis.cropTrajectory, captions)
                }
            }.getOrElse { ImportUi.Failed(it.message ?: "Analysis failed") }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { beginImport(it) }
    }

    // A video shared into the app: jump straight into processing.
    LaunchedEffect(initialVideo) {
        if (initialVideo != null && ui is ImportUi.Idle) beginImport(initialVideo)
    }

    // The Analyzing state is a full-screen signature takeover, not a list item.
    (ui as? ImportUi.Analyzing)?.let { a ->
        AnalyzingScreen(a.presetName, analyzeProgress, analyzeStage, analyzeScenes, analyzeFaces, analyzeLaughs)
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg)
            .padding(horizontal = Space.md),
        verticalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        item {
            Column(Modifier.padding(top = Space.xxl, bottom = Space.lg)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("IMPORT FOOTAGE", style = t.micro, color = c.accentActive)
                    Text(
                        "MODELS →", style = t.micro, color = c.accentMagic,
                        modifier = Modifier.clickable(onClick = onOpenModels),
                    )
                }
                Text("ch0p", style = t.displayL, color = c.textHi)
                Text(
                    "Auto-edit long footage into shorts — on device.",
                    style = t.body, color = c.textMid, modifier = Modifier.padding(top = Space.xs),
                )
            }
        }

        when (val state = ui) {
            is ImportUi.Idle, is ImportUi.Failed -> {
                item { ImportButton { picker.launch(arrayOf("video/*")) } }
                if (state is ImportUi.Failed) {
                    item { Notice(state.message, c.danger) }
                }
            }

            is ImportUi.Probing -> item { Notice("Reading file…", c.accentActive) }

            is ImportUi.Probed -> Unit  // auto-advances to Proxying; no manual step

            is ImportUi.Proxying -> {
                item { MetadataCard(state.meta) }
                item {
                    Column(Modifier.padding(vertical = Space.sm)) {
                        Text("BUILDING PROXY", style = t.micro, color = c.accentActive)
                        LinearProgressIndicator(
                            progress = { if (proxyProgress >= 0f) proxyProgress else 0f },
                            color = c.accentActive,
                            trackColor = c.surface2,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = Space.sm),
                        )
                    }
                }
                // Poll Transformer progress while transcoding.
                item {
                    LaunchedEffect(Unit) {
                        while (ui is ImportUi.Proxying) {
                            proxyProgress = proxyGen.queryProgress()
                            delay(200)
                        }
                    }
                }
            }

            is ImportUi.Ready -> {
                item {
                    Column(Modifier.padding(vertical = Space.sm)) {
                        Text("PROXY READY · CHOOSE A STYLE", style = t.micro, color = c.success)
                        Text(
                            "Analysis runs on the proxy. Pick a preset to auto-edit.",
                            style = t.body, color = c.textMid, modifier = Modifier.padding(top = Space.xs),
                        )
                    }
                }
                if (installedWhisper.isNotEmpty()) {
                    item {
                        TranscriptionPicker(installedWhisper, whisperModelId) { whisperModelId = it; haptics.scrubTick() }
                    }
                }
                item { PresetPager(onSelect = { preset -> haptics.confirm(); runEdit(state.meta, state.proxy, preset) }) }
            }

            is ImportUi.Analyzing -> Unit  // rendered full-screen above

            is ImportUi.Edited -> {
                item {
                    Column(Modifier.padding(vertical = Space.sm)) {
                        Text("${state.preset.displayName.uppercase(Locale.US)} · EDIT READY", style = t.micro, color = c.success)
                        state.title?.let { title ->
                            Text("“$title”", style = t.titleL, color = c.accentMagic, modifier = Modifier.padding(top = Space.xs))
                        }
                        Text(
                            "${state.edl.units.size} clips · ${formatDuration(state.edl.totalDurationMs)} · ${state.preset.aspectRatio}",
                            style = if (state.title != null) t.titleM else t.titleL,
                            color = c.textHi, modifier = Modifier.padding(top = Space.xs),
                        )
                    }
                }
                item {
                    TimelineEditor(state.edl, AppTheme.haptics) { newEdl ->
                        ui = state.copy(edl = newEdl)
                    }
                }
                items(state.edl.units) { entry -> EdlRow(entry.order, entry.srcInMs, entry.srcOutMs) }
                item { PrimaryButton("Render MP4") { runRender(state.preset, state.edl, state.cropTrajectory, state.captionChunks) } }
                item { Text("Start over", style = t.label, color = c.textMid,
                    modifier = Modifier.fillMaxWidth().clickable { ui = ImportUi.Idle }.padding(Space.md)) }
            }

            is ImportUi.Rendering -> item {
                Column(Modifier.padding(vertical = Space.sm)) {
                    Text("RENDERING · ${state.presetName.uppercase(Locale.US)}", style = t.micro, color = c.accentActive)
                    LinearProgressIndicator(
                        progress = { if (renderProgress >= 0f) renderProgress else 0f },
                        color = c.accentActive, trackColor = c.surface2,
                        modifier = Modifier.fillMaxWidth().padding(top = Space.sm),
                    )
                    LaunchedEffect(Unit) {
                        while (ui is ImportUi.Rendering) {
                            renderProgress = renderer.queryProgress()
                            delay(250)
                        }
                    }
                }
            }

            is ImportUi.Rendered -> {
                item { LaunchedEffect(Unit) { haptics.confirm() } }  // satisfying terminal confirm
                item {
                    Column(Modifier.padding(vertical = Space.sm)) {
                        Text("EXPORT COMPLETE", style = t.micro, color = c.success)
                        Text(state.output.name, style = t.timecode, color = c.textHi, modifier = Modifier.padding(top = Space.xs))
                    }
                }
                item {
                    PreviewPlayer(
                        state.output,
                        Modifier
                            .fillMaxWidth(0.62f)
                            .aspectRatio(9f / 16f)
                            .clip(RoundedCornerShape(Radius.lg))
                            .background(Color.Black),
                    )
                }
                item {
                    Text(
                        "Saved to app storage · ${formatBytes(state.output.length())}",
                        style = t.body, color = c.textMid,
                    )
                }
                item { PrimaryButton("Share clip") { shareVideo(context, state.output) } }
                item {
                    Text(
                        "Edit another", style = t.label, color = c.textMid,
                        modifier = Modifier.fillMaxWidth().clickable { ui = ImportUi.Idle }.padding(Space.md),
                    )
                }
            }
        }
    }
}

@Composable
private fun TranscriptionPicker(installed: List<ModelSpec>, selectedId: String?, onSelect: (String?) -> Unit) {
    val c = AppTheme.colors
    val t = AppTheme.type
    Column(Modifier.padding(vertical = Space.sm), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Text("TRANSCRIPTION", style = t.micro, color = c.textMid)
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            Choice("Off", selectedId == null) { onSelect(null) }
            installed.forEach { spec ->
                Choice(spec.displayName.substringBefore(" ("), selectedId == spec.id) { onSelect(spec.id) }
            }
        }
        Text(
            if (selectedId == null) "No captions / speech-aware cuts for this clip."
            else "Captions + speech-aware cuts on.",
            style = t.label, color = c.textLow,
        )
    }
}

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    val c = AppTheme.colors
    val t = AppTheme.type
    Text(
        label, style = t.label, color = if (selected) c.bg else c.textHi,
        modifier = Modifier
            .clip(RoundedCornerShape(Radius.pill))
            .background(if (selected) c.accentActive else c.surface2)
            .clickable(onClick = onClick)
            .padding(horizontal = Space.md, vertical = Space.sm),
    )
}

@Composable
private fun EdlRow(order: Int, srcInMs: Long, srcOutMs: Long) {
    val c = AppTheme.colors
    val t = AppTheme.type
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.sm))
            .background(c.surface1)
            .padding(horizontal = Space.md, vertical = Space.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("%02d".format(order + 1), style = t.timecode, color = c.accentActive)
        Text("${formatClock(srcInMs)} → ${formatClock(srcOutMs)}", style = t.timecode, color = c.textHi)
        Text("${"%.1f".format((srcOutMs - srcInMs) / 1000.0)}s", style = t.timecode, color = c.textMid)
    }
}

private fun shareVideo(context: android.content.Context, file: File) {
    val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "video/mp4"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share clip"))
}

private fun formatClock(ms: Long): String {
    val totalSec = ms / 1000
    return "%d:%02d.%d".format(totalSec / 60, totalSec % 60, (ms % 1000) / 100)
}

@Composable
private fun ImportButton(onClick: () -> Unit) {
    val c = AppTheme.colors
    val t = AppTheme.type
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(RoundedCornerShape(Radius.lg))
            .background(c.surface1)
            .border(BorderStroke(HairlineWidth, c.hairlineHi), RoundedCornerShape(Radius.lg))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text("＋  Select a video", style = t.titleM, color = c.textHi)
    }
}

@Composable
private fun PrimaryButton(label: String, onClick: () -> Unit) {
    val c = AppTheme.colors
    val t = AppTheme.type
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(c.accentActive)
            .clickable(onClick = onClick)
            .padding(vertical = Space.md),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = t.titleM, color = c.bg)
    }
}

@Composable
private fun MetadataCard(meta: VideoMetadata) {
    val c = AppTheme.colors
    val t = AppTheme.type
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.lg))
            .background(c.surface1)
            .border(BorderStroke(HairlineWidth, c.hairline), RoundedCornerShape(Radius.lg))
            .padding(Space.md),
        verticalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        Text((meta.displayName ?: "clip").uppercase(Locale.US), style = t.micro, color = c.textHi)
        MetaRow("duration", formatDuration(meta.durationMs))
        MetaRow("resolution", "${meta.orientedWidth}×${meta.orientedHeight}")
        MetaRow("frame rate", "${"%.0f".format(meta.frameRate)} fps")
        MetaRow("codec", "${meta.codec.name}${if (meta.bitDepth >= 10) " · 10-bit" else ""}")
        MetaRow("size", formatBytes(meta.sizeBytes))
        MetaRow("audio", if (meta.hasAudio) "yes" else "none")
    }
}

@Composable
private fun MetaRow(label: String, value: String) {
    val c = AppTheme.colors
    val t = AppTheme.type
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = t.label, color = c.textMid)
        Text(value, style = t.timecode, color = c.textHi)
    }
}

@Composable
private fun RiskNotice(risk: IngestRisk) {
    if (risk.level == IngestRisk.Level.OK) return
    val c = AppTheme.colors
    val color = if (risk.level == IngestRisk.Level.UNSUPPORTED) c.danger else c.accentActive
    Notice(risk.reasons.joinToString("\n"), color)
}

@Composable
private fun Notice(message: String, color: androidx.compose.ui.graphics.Color) {
    val t = AppTheme.type
    val c = AppTheme.colors
    Text(
        message,
        style = t.body,
        color = color,
        textAlign = TextAlign.Start,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(c.surface1)
            .padding(Space.md),
    )
}

private fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d".format(m, s)
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1e9)
    bytes >= 1_000_000 -> "%.0f MB".format(bytes / 1e6)
    bytes <= 0 -> "—"
    else -> "%.0f KB".format(bytes / 1e3)
}
