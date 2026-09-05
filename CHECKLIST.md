# RetinaSight AI — Build Checklist

**Legend:** `[ ]` not started · `[~]` in progress · `[x]` done · `[!]` blocked / needs you

> **Verification status — BUILT AND RUNNING ON THE iQOO 15.**
>
> A portable toolchain now lives on this machine at `D:\android-toolchain`
> (JDK 17 + Android SDK 35 + build-tools 35 + adb + Gradle 8.9, no admin, no Android Studio).
>
> - `gradle assembleDebug` → **BUILD SUCCESSFUL**, 18.5 MB APK, 0 errors, 3 deprecation warnings
> - Installed and launched on the device: vivo **I2501 (iQOO 15)**, Android 16 (SDK 36), arm64-v8a, **SM8850 = Snapdragon 8 Elite Gen 5**
> - No crashes in logcat; home + settings screens render; HackTracker is logging it
> - Benchmark APIs confirmed live on hardware: charge counter **1,355,000 µAh**, voltage 3512 mV, temp 32.1 °C
> - Fixed: content drew under the status bar (Android 15+ forces edge-to-edge at targetSdk 35)
>
> Rebuild + reinstall in one line:
> ```
> gradle assembleDebug ; adb install -r app\build\outputs\apk\debug\app-debug.apk
> ```

---

## Phase 0 — Scaffolding
- [x] Toolchain check (none on this machine)
- [x] Gradle scaffolding (settings, root build, gradle.properties, version catalog)
- [x] `app/build.gradle.kts` (+ `buildConfig = true` for the mock guard) + proguard
- [x] AndroidManifest — **no INTERNET permission**, camera optional, TTS query
- [x] Launcher icon as vector adaptive icon (no binary PNGs needed)
- [x] Toolchain installed at `D:\android-toolchain`; building with `gradle` directly
      (the wrapper jar is still absent — run `gradle wrapper` once if you want `./gradlew`)

## Phase 1 — Core foundation
- [x] `DrGrade` (0–4, throws on out-of-range model output), `Urgency`, `RetinaResult`
- [x] `InferenceEngine` interface — the single model seam
- [x] `MockInferenceEngine` — **refuses to run in release builds**; output varies per image
- [x] Theme: 5-step severity scale, oversized typography, no dynamic colour
- [x] `AppLanguage` (11 languages), `LanguageState`, `LocalizedContent` (no Activity recreate)
- [x] `LanguagePreferences` (DataStore)
- [x] `ScanHistoryStore` (JSON + JPEG, no Room/KSP — one less thing to break)
- [x] `SpeechManager` (Android TTS, reports unsupported languages honestly)

## Phase 2 — Localization
- [x] `values/strings.xml` — 65 strings, English
- [x] `values-hi/strings.xml` — Hindi, complete
- [x] `values-mr/strings.xml` — Marathi, complete
- [x] No user-facing string literals inside composables
- [!] ta / te / kn / bn / gu / ml / pa / or — **need native-speaker fill-in.**
      Medical wording must not be machine-guessed. `resourceConfigurations` in
      `build.gradle.kts` already lists them; add `values-<code>/strings.xml` per language.

## Phase 3 — Screens
- [x] `Routes` + `RetinaNavHost` (ScanViewModel hoisted above the graph — no Bitmap in nav args)
- [x] `MainActivity` (language gating: Loading / NotChosen / Chosen)
- [x] Language select — every language in its own script, speaks on tap
- [x] Home — one giant action, offline badge
- [x] Capture — CameraX + circular alignment ring + gallery fallback + software-bitmap decode
- [x] Analyzing — never blank, states "working on your phone"
- [x] Result — severity banner, confidence band, Grad-CAM toggle, urgency card, auto-speak
- [x] History — list + severity progression strip
- [x] Settings — language, voice test, model status, disclaimer

## Phase 4 — Voice
- [x] `SpeechManager` wired into result + language picker + settings
- [x] Auto-speak on result, Listen/Stop toggle
- [ ] AI4Bharat ONNX TTS fallback for languages with no system voice

## Phase 4b — Field benchmark (the USP)
- [x] `DeviceTelemetry` — battery current/voltage/charge-counter, thermal status + headroom
- [x] OEM unit normalisation for `CURRENT_NOW` (mA vs uA) with a warning flag
- [x] `BenchmarkRunner` — idle baseline, N-screening camp simulation, refuses to run while charging
- [x] Two independent energy methods + automatic divergence warning above 30%
- [x] Latency p50/p90/p99, net inference power, uAh + mWh per screening
- [x] **Screenings per charge** on a 7000 mAh cell
- [x] Thermal curve + throttle-onset detection
- [x] `BenchmarkScreen` with live progress, metric tables, thermal chart
- [x] Telemetry APIs confirmed working on the real iQOO 15
- [x] CSV export to external files dir (USB-reachable, deck-ready)
- [x] Wired into Settings -> Field benchmark
- [x] `BENCHMARK_METHODOLOGY.md` — defensible answer to "how did you measure that?"
- [ ] **Calibrate `CURRENT_NOW` units once on the real iQOO 15** — needs the device
- [ ] Run 100-screening camp with the REAL model (not the mock) and capture the CSV

## Phase 5 — Real model integration (PyTorch confirmed)
Pipeline written in `ml/`. Verified on this machine where possible; training needs a GPU.
- [x] Framework confirmed: **PyTorch**
- [x] `preprocess.py` — circle crop + circle mask + Ben Graham, numpy/PIL only (**verified**)
- [x] `metrics.py` — QWK + referable-DR sensitivity/specificity (**verified**)
- [x] `model.py` — EfficientNet-B0 + **gradient-free CAM head** (**CAM ≡ Grad-CAM verified to 9e-7**)
- [x] `dataset.py` — fundus-safe augmentation, class weights, balanced sampler (**verified**)
- [x] `prepare_data.py` — build CSVs; APTOS held out as a whole dataset, not a random split
- [x] `train.py` — fine-tune from existing checkpoint, warmup+cosine, select on QWK (**plumbing verified**)
- [x] `evaluate.py` — TTA, confusion matrix, slide-ready numbers
- [x] `export_tflite.py` — INT8 via ai-edge-torch, ONNX fallback, real-image calibration
- [ ] **Install timm + run the real fine-tune on a GPU machine** — needs you
- [ ] Re-evaluate AFTER INT8 quantisation (it costs QWK; know how much)
- [ ] Compile for QNN delegate via Qualcomm AI Hub
- [ ] Write `OnDeviceInferenceEngine.kt` + Kotlin twin of `preprocess.py`, then change **one line** in `AppContainer.kt`

## Phase 6 — Demo prep
- [x] First successful Gradle build (portable toolchain on this machine)
- [x] Install + run on the iQOO 15 — verified by screenshot, no crashes
- [ ] Airplane-mode end-to-end test
- [ ] HackTracker shows on-device inference, zero cloud calls
- [ ] Pitch rehearsal (3 runs)

---

## Needs you (blockers, in priority order)
1. ~~Run the first Gradle build~~ — **done, app runs on the phone**
2. ~~Model framework~~ — **PyTorch confirmed; pipeline written in `ml/`**
3. **Native-speaker translations** for the 8 remaining languages.
4. **Clinical review** of the grade/urgency wording in `values/strings.xml` before the demo.
5. **Benchmark calibration run** on the iQOO 15 (unplugged, airplane mode) once the real model is wired.
6. **Run the fine-tune** on a GPU machine (`pip install -r ml/requirements.txt`) and send me errors.
