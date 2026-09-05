# RetinaSight AI — Android App

Offline, on-device retina screening for rural India. Kotlin + Jetpack Compose.

## Build it (first time)

This project was authored without an Android toolchain present, so **the first build
is also the first compile check.** Expect to fix a few things; do this early.

1. Open the `RetinaSightApp` folder in **Android Studio** (Ladybug or newer).
2. Android Studio will offer to create the **Gradle wrapper** — accept.
   (Or from a shell with Gradle installed: `gradle wrapper`.)
3. Let it sync. It downloads AGP 8.5.2 / Kotlin 2.0.20 / Compose BOM 2024.09.00.
4. Run on the iQOO 15 (or any device with **API 26+**).

If sync fails on a version, bump it in `gradle/libs.versions.toml` — all versions
are pinned in that one file.

## What runs today

The UI is complete and navigable end to end. Inference is a **development mock**
(`MockInferenceEngine`) that returns a grade derived from the image's own pixels so
different photos give different screens. It **throws if it ever runs in a release
build** — so it cannot ship by accident.

## Wiring the real model (the only change needed)

`app/src/main/java/com/retinasight/ai/AppContainer.kt`:

```kotlin
val inferenceEngine: InferenceEngine = MockInferenceEngine(appContext)
//                                     ^^^^ replace with OnDeviceInferenceEngine(appContext)
```

Implement `InferenceEngine` (see `core/inference/InferenceEngine.kt`). No screen,
ViewModel or navigation code changes — everything depends on the interface.

## Design rules (do not break these)

1. **Nothing hardcoded in the medical path.** Grade, confidence, heatmap, urgency and
   explanation all come from the model via `InferenceEngine`. No canned results.
   (UI labels in `values-xx/strings.xml` are normal i18n — that is fine.)
2. **Offline-first.** There is deliberately **no `INTERNET` permission** in the manifest.
   If you think you need it, re-read the rural requirement first.
3. **Voice-first.** Every result speaks. Users may not read.
4. **No user-facing string literals in composables.** Always `stringResource(...)`.

## Adding a language

1. Create `app/src/main/res/values-<code>/strings.xml` (copy `values/strings.xml`, translate).
2. The code is already listed in `resourceConfigurations` and `AppLanguage`.
3. Have a **native speaker** check the medical wording. Do not machine-translate it.

## Project layout

```
core/model/       DrGrade, Urgency, RetinaResult        (domain)
core/inference/   InferenceEngine + MockInferenceEngine (the model seam)
core/lang/        AppLanguage, LocalizedContent         (runtime locale switching)
core/speech/      SpeechManager                         (offline TTS)
core/history/     ScanHistoryStore                      (JSON + JPEG, on-device only)
ui/theme/         severity palette, typography
ui/components/    SeverityBanner, ConfidenceIndicator, buttons, offline badge
ui/screens/       Language, Home, Capture, Analyzing, Result, History, Settings
ui/nav/           Routes + RetinaNavHost
ui/scan/          ScanViewModel (one screening run)
```
