# HANDOFF — RetinaSight AI — iQOO Hackathon 2026
**Date:** 2026-09-05 · **Machine:** Windows 11, `D:\SOHAM ALL\hackathons\IQOO`
**Device under test:** vivo I2501 (iQOO 15), Android 16 / SDK 36, arm64-v8a, Snapdragon SM8850 (8 Elite Gen 5)

> Read §0 to get running in five minutes. Read §5 before writing any code — it
> lists ten dead ends that cost hours and must not be repeated.

---

# 0. QUICK ORIENTATION

## Build and install, from cold

```powershell
$env:JAVA_HOME    = "D:\android-toolchain\jdk"
$env:ANDROID_HOME = "D:\android-toolchain\sdk"
$env:PATH = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:PATH"

cd "D:\SOHAM ALL\hackathons\IQOO\RetinaSightApp"
D:\android-toolchain\gradle\bin\gradle.bat assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

## Connect the phone (wireless ADB)

```powershell
adb pair 192.168.112.114:<PAIRING_PORT> <6-DIGIT-CODE>   # from Wireless debugging
adb connect 192.168.112.114:39345                        # connect port differs from pairing port
```
The connect port is **not** shown next to the pairing code. If mDNS discovery
returns nothing (common on venue Wi-Fi), port-scan 30000–50000 for the open one.

## What lives where

| Path | What |
|---|---|
| `RetinaSightApp/` | The Android app (49 Kotlin files, 6,750 lines, 64.2 MB APK) |
| `D:\android-toolchain\` | Portable JDK 17 + Android SDK 35 + Gradle 8.9 + adb. **Not on PATH.** |
| `D:\SOHAM ALL\hackathons\Omnikon\` | Prior project: training code, datasets, and the source of `dr-v2.onnx` |
| `/sdcard/Android/data/com.retinasight.ai/files/llm/narrator.task` | Qwen2.5-1.5B on the device (1.6 GB) |
| `test_images/` | 10 APTOS fundus photos + `expected.json` desktop predictions |
| `llm/` | Downloaded Qwen `.task` (1.5 GB) |

---

# 1. GOAL

## The product

An Android app that grades diabetic retinopathy (DR) from a fundus photograph
**entirely on the phone**, with no server anywhere in the diagnostic path, and
communicates the result to the patient **out loud in their own language**.

## The user — this matters, and it is not obvious

**Not** a villager with a phone. The real users are:
- Vision-centre technicians / optometrists at PHCs (India has ~25,000; many have
  a fundus camera and **no ophthalmologist**)
- NGO screening camps (Aravind, LVPEI, Sightsavers) doing bulk capture
- Teleophthalmology programmes where images currently wait days for a grader

**Critical constraint:** a smartphone camera **cannot photograph a retina**. It
physically requires optics through the pupil. This app is the *grading and
workflow layer*; capture needs a fundus adapter (D-EYE, oDocs, Peek) or an
existing clinic camera. Any pitch that implies phone-only capture is false and
a judge will break it.

## The value proposition

Not "offline AI" as an end in itself. At a camp, images queue for a human grader
for days-to-weeks; by then the patient has gone home and follow-up collapses.

> **The referral decision happens while the patient is still in the chair.**

## Differentiation vs Medios (Remidio) — the closest competitor

Medios is also offline and phone-based (~93% sens / 92.5% spec, ~20 s/report).
Three things it cannot claim:
1. **Hardware-agnostic** — Medios is bundled and locked to Remidio's Fundus-on-Phone camera
2. **Open** — Apache-2.0 / open weights vs. a closed proprietary model
3. **11 Indian languages with voice** — matters for consent and comprehension

Plus published field telemetry (ms, mW, patients/charge, thermal), which nobody reports.

## Hackathon context

- iQOO Hackathon 2026, Pune leg, 30 hours, 5–6 Sep 2026
- Judging: End product 30% · Novelty 20% · Creative phone use 15% · Technical depth 15% · Office Kit 10% · Demo 10%
- Rules reward **on-device / local / open-source models**; "highest on-device
  builds preferred for Top 10"; on-device inference costs zero OpenRouter credits
- HackTracker logs inference calls and thermals as proof of on-device work
- The vivo Remote Control app **is** the Office Kit (10% of the score) — it was
  observed running during this session

---

# 2. CURRENT STATE

## Verification legend
✅ observed working on the physical device · 🟡 built + compiles, not verified on device · ❌ not done

## Feature status

| Area | Status | Notes |
|---|---|---|
| Language picker, 11 languages | ✅ | All render correctly in own script |
| Consent gate (mandatory) | ✅ | Continue disabled until ticked — verified |
| Patient details (name/age/sex/phone/diabetes/years) | ✅ | Optional; consent is not |
| Left/right eye selection | ✅ | |
| Camera capture (CameraX) | ✅ | Live preview + alignment ring |
| Photo picker upload | ✅ | `PickVisualMedia`, no storage permission |
| Quality gate + retake screen | ✅ | 9/10 real APTOS photos accepted |
| Inference on NNAPI | ✅ | `provider=NNAPI (NPU/GPU)` in logcat |
| Result: grade + confidence band | ✅ | Graded a real fundus as "Advanced changes", High |
| CAM heat map | ✅ | On by default, aligned to preprocessed image |
| Grounded explanation | ✅ | Templated, localised |
| Spoken summary (TTS) | 🟡 | Code verified; **no Marathi voice installed on device** |
| Voice-install prompt | 🟡 | Opens system TTS download screen |
| On-device LLM load | ✅ | `LlmNarrator: LLM ready (1597 MB)` |
| LLM output quality | ❌ | **Mojibake in Devanagari** — guard suppresses it |
| History + progression strip | 🟡 | |
| Clinic sync queue + connectivity | 🟡 | Never tested against a live backend |
| Global home button | ✅ | |
| Field benchmark harness | ✅ | Clean 100-screening run, 2026-09-05 23:09, CSV in `exports/` |

## Hard gaps — say these before a judge finds them

1. **Latency is measured; power and thermal are extrapolated from 10 seconds.**
   A clean 100-screening run completed on 2026-09-05 (vivo I2501, unplugged,
   76% battery). Latency is solid: 100 real inferences, p50 **101.4 ms**.
   But the whole run took **10.38 s** of back-to-back inference, so the power,
   patients-per-charge and thermal figures come from a ~10 s window in which the
   battery temperature never moved (33.2 → 33.2 °C). Quote the millisecond
   numbers freely. For power and thermal, say "over a 10-second sustained-load
   sample" — do **not** say "over a camp", because the runner has no
   per-patient spacing and never ran for a camp's duration.
2. ~~8 of 11 languages have no translations.~~ **DRAFTED, NOT VERIFIED.** All 11
   now build with full string sets (179 strings per locale; `app_name` is a brand
   name and intentionally lives only in `values/`). The eight new ones are
   **machine-drafted and unreviewed by native speakers** — each file says so in a
   header comment. That is a smaller gap than falling back to English but still a
   real one: say "drafted, pending native review", not "11 verified languages".
   `python scripts/check_translations.py` gates key completeness and placeholder
   match across every locale and exits non-zero on failure.
3. **Validation is internal.** QWK 0.93 is a stratified split of APTOS — same
   dataset, same cameras. Not external validation; it will not hold at 0.93 on
   a different camera.
4. ~~Operating point is balanced, not screening-biased.~~ **FIXED.** The referral
   threshold now sits at 1.15 on the expected grade instead of the rounding
   point of 1.5: 98.2% sensitivity / 92.9% specificity, 4 missed referable
   patients instead of 19. See `core/model/ReferralPolicy.kt`.
5. **Not a medical device, not clinically validated, no regulatory clearance.**
   Every result screen carries the disclaimer.
6. ~~Capture UI implies phone-only capture.~~ **FIXED.** Title is now "Capture the
   fundus image", the instruction names the adapter, the gallery button reads
   "Import from fundus camera", and a new `capture_adapter_note` states plainly on
   screen that a phone camera alone cannot photograph a retina.
7. ~~No git repository initialised.~~ **DONE.** Repo initialised with a
   `.gitignore` excluding the 1.5 GB narrator `.task`, all build output, and the
   patient fundus images in `test_images/`. Staged tree is ~18 MB.

---

# 3. ACTIVE FILES

## The app — `RetinaSightApp/app/src/main/java/com/retinasight/ai/`

| File | Purpose |
|---|---|
| `RetinaSightApplication.kt` | App entry; app-wide CoroutineScope **with an exception handler** so background work can never kill the app |
| `AppContainer.kt` | Manual DI (no framework). **The model swap point.** |
| `MainActivity.kt` | Compose host; language gating (Loading/NotChosen/Chosen); `safeDrawingPadding` for Android 15+ edge-to-edge |
| **core/model/** | |
| `DrGrade.kt` | 5 DR grades; ordinal == clinical grade; throws on out-of-range model output |
| `Urgency.kt` | Referral urgency, derived from grade |
| `RetinaResult.kt` | One screening outcome; carries `processedImage` so the heat map aligns |
| **core/inference/** | |
| `InferenceEngine.kt` | The single seam between UI and model |
| `OnDeviceInferenceEngine.kt` | ONNX Runtime + NNAPI; softmax, CAM→bitmap, grounded explanation |
| `RetinaPreprocessor.kt` | **THE CONTRACT.** Kotlin port of the training preprocessing, incl. hand-written Pillow antialiased bilinear |
| `MockInferenceEngine.kt` | Dev only; **throws in release builds** |
| **core/quality/** | |
| `ImageQualityGate.kt` | Blur / lighting / framing / retina-visibility, with Omnikon's calibrated constants |
| **core/llm/** | |
| `LlmNarrator.kt` | Optional Qwen2.5-1.5B via MediaPipe; prompt is a *rewriting* task; **mojibake guard** |
| **core/lang/** | |
| `AppLanguage.kt` | 11 languages; `endonym` deliberately untranslated |
| `LanguagePreferences.kt` / `LanguageState.kt` | DataStore persistence; Loading vs NotChosen |
| `LocalizedContent.kt` | Runtime locale switching. **Wraps the Activity context** — see §5 |
| **core/speech/** | |
| `SpeechManager.kt` | Android TTS; reports unsupported languages honestly; voice-install intent |
| **core/patient/** | |
| `PatientRecord.kt` | Eye / Sex / DiabetesStatus / PatientRecord; consent is a **timestamp**, not a boolean |
| `PatientStore.kt` | JSON persistence |
| **core/history/** | |
| `ScanRecord.kt` / `ScanHistoryStore.kt` | Local history + sync state; image never uploaded |
| **core/sync/** | |
| `SyncModels.kt` | SyncState / ClinicConnection / SyncStatus / SyncItem |
| `ConnectivityObserver.kt` | Uses `NET_CAPABILITY_VALIDATED`, not merely connected |
| `SyncTransport.kt` | `POST /api/v1/sync/push`, `HttpURLConnection` |
| `SyncManager.kt` | Queue, auto-drain on reconnect. **`clinicFlow` declared above `init` — order is load-bearing** |
| **core/benchmark/** | |
| `DeviceTelemetry.kt` | BatteryManager + PowerManager; normalises OEM mA/µA variance |
| `BenchmarkModels.kt` / `BenchmarkRunner.kt` | Camp simulation; two independent energy methods; refuses to run while charging |
| `BenchmarkExporter.kt` | CSV to external files dir |
| **ui/** | |
| `nav/Routes.kt` / `nav/RetinaNavHost.kt` | Graph + global home button; ScanViewModel hoisted above the graph |
| `scan/ScanViewModel.kt` | One screening run; quality gate; patient persistence; triggers sync |
| `screens/…` | Language, Home, Patient, Capture, Quality, Analyzing, Result, History, Settings, Clinic |
| `components/` | BigActionButton, SecondaryActionButton, OfflineBadge, SeverityBanner, ConfidenceIndicator |
| `theme/` | 5-step severity palette, oversized typography, no dynamic colour |

## Resources
`values/strings.xml` (178 strings) · `values-hi/` · `values-mr/` · colors, themes, vector launcher icon

## Assets
`dr-v2.onnx` (16 MB) · `dr-v2.json` (model card)

## Documentation at repo root
`PITCH_PACK.md` (pitch + jury Q&A) · `SIMPLE_PITCH.md` (non-technical) ·
`TECH_STACK_EXPLAINED.md` (spoken walkthrough) · `BENCHMARK_METHODOLOGY.md` ·
`PHONE_TEST_SHEET.md` (10-image parity table) · `CHECKLIST.md` ·
`retina_datasets_reference.md` · `MASTER_PROMPT.md` + `.claude/commands/` (handoff kit)

---

# 4. THE MODELS

## Grader — `dr-v2.onnx` (16 MB)

| | |
|---|---|
| Architecture | EfficientNet-B0, **456 px**, torchvision layout |
| Training | APTOS 2019 (3,662 images), cached at 456 px, ordinal objective, distance weight 0.5 |
| Source checkpoint | `Omnikon/ml/models/efficientnet_b0-20260823-124225/best.pt` |
| **QWK** | **0.9271 ± 0.0062** (3 seeds); best single 0.9324 |
| Accuracy | 83.5% |
| Referable sensitivity / specificity (rounding point, 1.5) | **91.4% / 94.8%** |
| Referable sensitivity / specificity (**shipped**, threshold 1.15) | **98.2% / 92.9%** |

> 94.1% is the **3-seed mean** from `456px-ordinal-summary.json`, not this
> checkpoint's number, and the run that individually scored 94.12% (`153039`)
> has a *lower* QWK. Pairing 0.9324 with 94.1% quotes two different runs.
| ONNX↔PyTorch parity | max prob diff 1.09e-4 (verified by the prior team) |

**Three outputs from one forward pass:**
```
logits (1,5)         raw scores
cam    (1,5,15,15)   one class activation map per grade, gradient-free
grade  (1,)          ordinal decision, ALREADY rounded in-graph
```
The model decides by **rounding the expected grade**, not argmax. Using argmax on
device would silently disagree with the reported QWK — always use the graph's
`grade` output.

## Preprocessing contract (must match exactly)

1. Crop to retinal disc: luminance (BT.601) > **18**, bounding box, **+2% padding**
2. Resize to **456×456** with **Pillow's antialiased BILINEAR** (OpenCV is absent
   from the training env, so the Pillow branch made the cached training images)
3. `/255`, ImageNet mean/std, NCHW float32

**No Ben Graham. No circle mask.** Adding either changes the input distribution.

## Narrator — Qwen2.5-1.5B-Instruct (1.6 GB, on device only)

Apache-2.0, int8, MediaPipe Tasks GenAI, loads via XNNPack. Never bundled in the
APK. Restates the CNN's decision; never decides. Output is validated and
discarded if it contains byte-BPE artefacts.

---

# 5. FAILED ATTEMPTS — DO NOT REPEAT

## Model / ML

1. **Do NOT retrain.** `dr-v2.onnx` already exists at QWK 0.93. A full retrain on
   88K EyePACS will not converge in a hackathon window and domain shift can
   *lower* APTOS accuracy.
2. ~~My reproduction of their validation split was contaminated (QWK 0.978).~~
   **SOLVED.** The cause: `train.py` originally used one `seed` field for both
   the run and the split, and `split_seed` was added later. Checkpoint `124225`
   has **no `split_seed` in its config**, so it predates the change and its split
   used the *run* seed **143**, not the default 42. Splitting with 42 reproduces
   the contaminated 0.978; splitting with 143 reproduces the recorded confusion
   matrix cell-for-cell (QWK 0.9324, acc 0.8333, sens 0.9140, spec 0.9477).
   **Recipe:** `stratified_split(discover_samples("data/aptos_456"),
   val_fraction=0.15, seed=143)`. Still quote 0.93, never 0.978.
3. **Gemma is `gated=auto` on Hugging Face** — needs a token + licence
   acceptance. Qwen2.5 is `gated=False` and Apache-2.0, which is also the better
   licence for an "open source" claim.
4. **Kimi K2 / DeepSeek-V3 / GLM / Nemotron cannot run on this phone.** At int4
   they are ~500 / 335 / 178 / 127 GB against **8.5 GB available RAM** — 15–60×
   over. MoE does not help; all weights must be resident. Do not revisit.
5. **Grad-CAM does not survive export.** ONNX/TFLite cannot backpropagate. For a
   GAP→Linear head, CAM from classifier weights is mathematically identical and
   forward-only (verified numerically to 9e-7).
6. **The LLM produces byte-BPE mojibake in Devanagari** — `Ġ à ȡ ¤ ķ` artefacts.
   Not a font problem: the app's own Marathi renders perfectly on the same screen.

## Android

7. **`createConfigurationContext` for localisation broke every activity result.**
   It returns a fresh `ContextImpl`, severing the ContextWrapper chain, so Compose
   cannot find `ActivityResultRegistryOwner` — camera permission and the photo
   picker crashed with `IllegalStateException`. **Fix:** wrap the Activity context
   and override only `getResources()`.
8. **Kotlin init-order NPE in `SyncManager`.** A flow declared *below* `init` is
   null when an init coroutine touches it → `NullPointerException` in
   `Flow.collect` at launch. Property initialisers and init blocks run in
   declaration order.
9. **`SupervisorJob` alone does not stop a coroutine crash killing the app.** An
   uncaught exception still reaches the thread's default handler. A
   `CoroutineExceptionHandler` is what actually contains it.
10. **Heat map was drawn over the original photo** and therefore misaligned — the
    CAM is computed over the cropped/resized 456 px image. Must overlay
    `RetinaResult.processedImage`.
11. **Capture buttons were unreachable in landscape** — the column had no scroll
    and the preview pushed them off-screen.
12. **`ChoiceRow` overflowed** with 4 options in portrait; the last was clipped
    and untappable. Now `FlowRow`.
13. **Content drew under the status bar** — Android 15+ forces edge-to-edge at
    `targetSdk 35`. Fixed with `safeDrawingPadding()` at the nav root.
14. **`ImageDecoder` returns a hardware bitmap by default**, which has no readable
    pixels — every `getPixel` and model feed would throw. Force
    `ALLOCATOR_SOFTWARE`.

## Tooling / environment

15. **UI automation taps landed in vivo Remote Control** (Office Kit mirroring the
    laptop) — my `input tap` commands could have clicked things on the laptop.
    **Always check `mCurrentFocus` before sending input events.**
16. **Piping `y` to `sdkmanager --licenses` does not work** — the `.bat` wrapper
    doesn't read piped stdin. Write the license hash files into `sdk/licenses/`
    directly, as CI does.
17. **PowerShell 5.1 wraps native-exe stderr in an ErrorRecord** and sets `$?`
    false even on exit 0 — `java -version` "failed" while succeeding. Don't
    redirect native stderr.
18. **PowerShell `>` corrupts binary** (adds a BOM) — `adb exec-out screencap >
    file.png` produces an invalid PNG. Use `adb shell screencap` + `adb pull`.
19. **Gradle heredocs with apostrophes/backslashes** repeatedly mangled files via
    bash; the Write tool is more reliable for large Kotlin files.

---

# 6. NEXT STEPS (in order)

### 1. ~~Run the field benchmark~~ — DONE (2026-09-05 23:09)
100 screenings, vivo I2501, unplugged. CSVs in `exports/`.
**Latency is real and quotable:** p50 **101.4 ms**, p90 109.7, p99 116.1,
mean 103.8, no throttling. Battery capacity measured 6062 mAh of 7000 spec.

**Caveat that must be stated with it:** the whole run is 10.4 s of back-to-back
inference, so `power_net_inference` (10.9 W), `patients_per_charge` (245) and the
thermal curve come from a ~10 s window in which battery temperature never moved
(33.2 → 33.2 °C). Say "over a 10-second sustained-load sample", never "over a camp".

Remaining work here: add per-patient spacing to `BenchmarkRunner` and run for a
realistic camp duration if a genuine patients-per-charge figure is wanted.

### 2. ~~Bias the operating point toward sensitivity~~ — DONE
Swept the referral threshold over the verified validation split. Shipped 1.15:
sensitivity 91.4% → 98.2%, specificity 94.8% → 92.9%, missed referable 19 → 4.
The displayed grade still comes from the graph's own rounded output, so QWK is
unchanged; only the referral decision moved. See `core/model/ReferralPolicy.kt`,
which carries the whole sweep table in its doc comment.

### 3. ~~Fix the capture-screen copy~~ — DONE
Retitled to "Capture the fundus image", instruction now names the adapter, the
gallery button reads "Import from fundus camera", and a new `capture_adapter_note`
states plainly that a phone camera alone cannot photograph a retina. All three
existing locales updated.

### 4. ~~Draft the 8 missing translations~~ — DRAFTED; NATIVE REVIEW OUTSTANDING
ta, te, kn, bn, gu, ml, pa, or all present (179 strings each) and building into
the APK. Each file's header comment marks it machine-drafted and unverified.

**The remaining blocker is native-speaker review**, and it is a real one: these
strings tell a patient how urgently to see a doctor, so a mistranslation is a
clinical error. Prioritise the strings that carry clinical meaning —
`urgency_*`, `grade_*_desc`, `explain_*`, `consent_body` — over UI furniture.

Gate every build on `python scripts/check_translations.py`.

### 5. Optional / lower priority
- Test sync against the live backend; add clinic auth UI (`/sync/push` needs `SYNC_WRITE`)
- External validation on IDRiD or Messidor — a number that survives a different camera
- AI4Bharat ONNX TTS fallback for languages with no system voice
- `git init` and commit

---

# 7. STANDING DESIGN RULES

Break these only deliberately, and say so:

1. **Nothing hardcoded in the medical path.** Grade, confidence, heat map,
   urgency and explanation all derive from the real model and the actual image.
   UI strings in `values-xx/` are normal i18n and are *not* what this rule forbids.
2. **The diagnostic path never touches the network.** `INTERNET` exists solely
   for optional clinic upload. If a change would make screening require the
   network, that change is wrong.
3. **The eye photograph never leaves the phone.** Only the structured result syncs.
4. **The explanation is templated, not generated.** It can be wrong about the
   grade; it can never invent a treatment.
5. **Consent precedes capture**, enforced in code.
6. **Never claim "diagnosis."** Screening aid with clinician confirmation —
   `clinically_validated: false` is in the prior team's own metrics.
7. **Never quote a number that has not been measured.**
