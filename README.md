# ch0p

Turn long camera footage into short clips — **automatically, on your phone, offline.**

ch0p imports footage filmed on a real camera, analyzes it on-device (scene length,
motion/action, speech, laughter, dramaticness, cinematic quality, interest), and assembles
a short using a selectable style preset — no upload, no cloud, no per-clip cost.

> Status: **early scaffold.** Phase 0 complete — the project builds, the editing brain
> runs and is unit-tested. Ingest, on-device analysis, and the Media3 render pipeline are
> the next phases (see [the plan](#roadmap)).

## Install (sideload)

Debug-signed APKs will be attached to GitHub Releases. Until then, build locally:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
./gradlew :app:assembleDebug          # -> app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:installDebug           # to a connected device
```

> Debug-signed builds: uninstall any previous version before installing (signatures differ).

## How it works

```
Import (SAF) → copy + 540–720p proxy → one decode pass fans out all signals
   → fuse into per-time curves → segment → score → submodular select → arrange
   → reframe trajectory → EDL → Media3 render (crop/grade/captions/music) → MP4
```

The novel part is the **editing brain** (`:edit-engine`) — a pure-Kotlin, deterministic
engine that fuses the signal curves with a preset's weights and selects a diverse,
budget-constrained set of moments. It runs and is tested without a device:

```bash
./gradlew :edit-engine:test
```

Presets ship as pure config (`Presets.kt`): Short-form/TikTok, Cinematic, Promotional,
Vlog, Action, Talking-head. Adding a preset is adding a config object, never a code path.

## Tech

Kotlin · Jetpack Compose · Media3 (Transformer/Effect/ExoPlayer) · MediaPipe Tasks ·
ONNX Runtime · whisper.cpp · OpenCV (NDK). No FFmpeg — the device's hardware codecs do
the work. AGP 9.2 · Kotlin 2.3 · minSdk 31 · arm64-v8a.

## Roadmap

Phased delivery: 0 toolchain ✓ · 1 ingest+proxy · 2 native analysis · 3 edit engine ✓
· 4 render · 5 captions+music · 6 all presets+design · 7 drama/aesthetic tuning · 8 ship.

<details>
<summary>Contributing / module layout</summary>

```
:app          Activity, Compose UI, Studio design system (CompositionLocal tokens)
:edit-engine  the editing brain — pure Kotlin, JVM-tested
(later) :ingest :analysis :render :nativelib :presets
```

The design system lives in `app/.../ui/theme/` (Ink/Amber/Iris palette, Inter+mono type,
spring motion, semantic haptics). Run `./gradlew test` for all unit tests.
</details>
