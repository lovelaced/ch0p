<div align="center">

# ch0p

*Turn long camera footage into short clips — automatically, on your phone, fully offline.*

[![Build](https://img.shields.io/github/actions/workflow/status/lovelaced/ch0p/release.yml?branch=master&style=flat-square)](https://github.com/lovelaced/ch0p/actions)
[![Release](https://img.shields.io/github/v/release/lovelaced/ch0p?style=flat-square)](https://github.com/lovelaced/ch0p/releases)
[![License](https://img.shields.io/badge/license-MIT-blue.svg?style=flat-square)](LICENSE)
[![Platform](https://img.shields.io/badge/platform-Android-3ddc84.svg?style=flat-square)](#install)

<!-- TODO(hero): add a screen recording of share-in → Analyzing telemetry → preview → share.
     Capture on-device with the screen recorder, trim to ~12s, export GIF (≤5MB) to assets/demo.gif. -->

</div>

Share a video into ch0p and it studies the footage — scene changes, motion, speech, laughter, drama, cinematic quality, and what's interesting — then cuts a short for you. Pick a style, watch it work, preview the result, and share it out. Nothing leaves your device: no upload, no account, no cloud, no per-clip cost.

Every cloud clipping tool uploads your footage and only works on talking-head video. ch0p runs entirely on-device and is built for *real camera footage* — action, travel, events, b-roll — where there's often no one talking at all.

---

## Features

- **Fully on-device & private** — analysis, editing, and rendering all run locally. Works on a plane, in the field, with no signal. Your footage never leaves the phone.
- **A real editing brain** — fuses scene length, motion, speech, laughter, drama, aesthetics, and interest into one score, then selects a diverse, well-paced set of moments under a target length (not just the loudest clips).
- **Works without speech** — reads embedded camera telemetry (GoPro/action-cam accelerometer, g-force, and the HiLight tags you press while filming) to find highlights on footage no transcript-based tool can touch.
- **Style presets** — Short-form/TikTok, Cinematic, Promo, Vlog, Action, Talking-head. Each is a tuned recipe for pacing, weighting, aspect ratio, captions, and ordering.
- **Smart reframe & karaoke captions** — follows the subject when cropping landscape to vertical, and burns in word-timed captions from on-device transcription.
- **Download only what your device can run** — premium models (Whisper, YAMNet, Silero, NIMA, face, on-device LLM) are optional, capability-gated downloads. The baseline works on any device with no downloads at all.
- **Share in, share out** — appears in the system share sheet as a video target; exports straight back out to any app.

---

## Install

ch0p is distributed as a sideloadable APK during development.

1. Download the latest `app-debug.apk` from [**Releases**](https://github.com/lovelaced/ch0p/releases).
2. On your phone, allow installing from your browser/files app, then open the APK.
3. Uninstall any previous ch0p build first — debug APKs are signed with a per-build key, so updates won't install over each other.

Then either open ch0p and pick a video, or **Share → ch0p** from your gallery.

> On-device models are downloaded on demand from the in-app **Models** screen — they are not bundled in the APK. The baseline editor runs without any of them.

<details>
<summary>Build from source</summary>

Requires JDK 17 and the Android SDK (with NDK 28.2 + CMake 3.22.1 — the build will fetch them if missing).

```bash
export JAVA_HOME=/path/to/jdk-17
./gradlew :app:assembleDebug          # -> app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:installDebug           # install to a connected device

./gradlew test                        # JVM unit tests
bash tools/hosttest.sh                # native (C++) host test, no device needed
```

</details>

---

## How it works

```
share / pick a video
  → build a low-res proxy            (fast, battery-friendly analysis copy)
  → analyze on-device                (one decode pass fans out every signal)
  → fuse + select                    (segment → score → diverse pick → arrange)
  → render                           (subject-tracking reframe + captions → MP4)
  → preview & share
```

The analyzer samples the proxy a few times a second and computes per-moment signals; the **edit engine** — a small, deterministic core — normalizes them, scores each candidate segment with the active preset's weights, and uses a diversity-aware selection so the cut isn't five near-identical moments. Cuts snap to scene boundaries, silences, and sentence ends rather than landing mid-word.

| Signal | How it's measured | Always on |
|---|---|---|
| Scene length / cuts | Content-aware frame differencing (native C++) | ✅ |
| Action / motion | Frame-difference energy | ✅ |
| Cinematic quality | Sharpness, colorfulness, exposure (+ NIMA if installed) | ✅ |
| Loudness / energy | RMS over the audio track | ✅ |
| Drama | Loudness swells + dynamics | ✅ |
| Interest | Motion + aesthetics + faces | ✅ |
| Speech | Energy VAD (Silero if installed) | ✅ |
| Laughter / applause / music | YAMNet | downloadable |
| Transcript / captions | Whisper (word timestamps) | downloadable |
| Faces / expression | MediaPipe | downloadable |
| Camera telemetry highlights | GPMF accelerometer + HiLight tags | ✅ when present |

### Optional models

Offered only when your device has the RAM and architecture to run them.

| Feature | Model | Size |
|---|---|---|
| Auto captions | Whisper base / small | 57 / 182 MB |
| Robust speech | Silero VAD | 2 MB |
| Laughter & reactions | YAMNet | 4 MB |
| Cinematic scoring | NIMA | 5 MB |
| Face & expression | MediaPipe BlazeFace / Landmarker | 1–4 MB |
| Smart selection & titles | On-device LLM (Gemma) | ~550 MB |

---

## Status

Early but complete in scope: the full pipeline — import, analysis, the editing engine, render, captions, reframe, the model manager, and the Studio UI — is implemented and covered by unit tests plus a native host test. On-device runtime tuning (real-world timings, model quality) is the next milestone.

<details>
<summary>Project layout & contributing</summary>

Modular Kotlin + Jetpack Compose, single-Activity, no XML UI.

```
:edit-engine   the editing brain — pure Kotlin, deterministic, JVM-tested
:ingest        SAF import, codec probe, Media3 proxy, GPMF telemetry parsing
:analysis      native scene/motion/aesthetic (NDK) + audio + on-device ML
:render        Media3 Transformer: trim, reframe, captions → MP4
:models        capability-based model catalog + verified downloads
:app           Compose UI (Studio design system) + the import→share flow
```

No FFmpeg — rendering uses the device's hardware codecs via Jetpack Media3. Native code (scene detection, Whisper) builds via CMake `FetchContent` and is host-testable on macOS/Linux. Run `./gradlew test && bash tools/hosttest.sh` before a PR.

</details>

## License

MIT — see [LICENSE](LICENSE). Bundled and downloadable models carry their own licenses (whisper.cpp MIT, MediaPipe/YAMNet Apache-2.0, Gemma under the Gemma terms).
