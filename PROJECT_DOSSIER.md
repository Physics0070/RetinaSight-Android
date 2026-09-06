# RetinaSight AI — project dossier

**The single entry point.** What the project is, what state it is in, everything
that was built and verified, what every document contains, and what is still
missing.

Read this first. It tells you which of the other documents you actually need.

**Updated:** 2026-09-06 · **Device:** vivo iQOO 15, Android 16 / SDK 36 ·
**Repo:** https://github.com/Physics0070/RetinaSight-Android

---

## 1. What this is, in four lines

An Android app that grades **diabetic retinopathy** from a fundus photograph
**entirely on the phone** — no server in the diagnostic path — and tells the
patient the result **out loud in their own language**, in 11 of them.

The users are vision-centre technicians and NGO screening camps, not patients
and not ophthalmologists. It decides **who needs an eye doctor**, while the
patient is still in the chair.

**Constraint stated up front:** a phone camera cannot photograph a retina. This
is the grading and workflow layer; capture needs a fundus adapter or a clinic
camera. The app says so on its own capture screen.

---

## 2. Current state — verified, not assumed

Everything marked ✅ was checked against the running app or the shipped
artefact during this session.

| Area | State | How it was verified |
|---|---|---|
| Build | ✅ green | `assembleDebug` — real recompiles, not cache hits |
| Installed on device | ✅ | `adb install -r` → Success |
| Inference on NNAPI | ✅ | HUD reads `NNAPI (NPU/GPU)` live |
| Model accuracy | ✅ QWK 0.9324 | **re-ran the shipped ONNX** — all 25 confusion cells match |
| Referral threshold 1.15 | ✅ | reproduced: 98.2% / 92.9%, 4 missed |
| 11 languages | ✅ 198/198 keys | `check_translations.py` passes |
| Quality gate | ✅ | demoed live — non-retina photo → 43%, refused |
| End-to-end flow | ✅ | full walkthrough captured in 17 screenshots |
| Grad-CAM | ✅ | hotspots land on lesions in the moderate test case |
| `core/` untouched | ✅ | empty `git status` throughout the UI work |

**Not done, and stated plainly:**

| Gap | Why it matters |
|---|---|
| **External validation** | All metrics are one dataset, one camera set. IDRiD harness is written and verified; only the data is missing (needs IEEE DataPort login) |
| **Paced energy benchmark** | Power figures come from a ~10-second window; two runs disagree by 40%. Needs one unplugged 25-min run |
| **8 of 11 locales unreviewed** | Machine-drafted, pending native review |
| **No regulatory clearance** | `clinically_validated: false` — this is screening support, not diagnosis |
| **`ClinicScreen` unreachable** | Deliberate: never tested against a live backend, so not exposed in a demo |
| Nothing committed | All work is in the working tree; awaiting your call on commit authorship |

---

## 3. What was built this session

### 3.1 Verification pass — before any code was written

The starting handoff claimed a long list of completed work. It was **audited
rather than trusted**, and it held up: threshold, confidence bands,
preprocessing contract, sync race fix, `onLost` fix, delete-removes-image, duty
cycle, UI fixes, TTS install wiring — all present in code.

**Independently reproduced the model's headline numbers.** Built
`scripts/validate_onnx.py`, ran the shipped `dr-v2.onnx` over the APTOS
validation split through the serving preprocessing, and matched the training
run exactly — all 25 confusion cells, QWK, accuracy, sensitivity, specificity.

**Two real defects found and fixed**, both in `PITCH_PACK.md`:
- Claimed *"8 of 11 languages have no translations — they fall back to English."* False; all 11 were complete.
- The **spoken pitch line** claimed a *"thermal curve over a simulated 100-patient camp."* The run was 100 back-to-back inferences in ~10 seconds, unpaced — the exact phrasing the project's own rules forbid.

### 3.2 Design system overhaul

Adopted the AI Studio "Remix RetinaSight AI" design study — but **studied, not
copied**.

| Token | Value |
|---|---|
| Deep Navy | `#0A2463` — primary |
| Calming Teal | `#247BA0` — secondary |
| Laser Cyan | `#5EEAD4` — HUD, beams |
| Medical BG | `#F8FAFC` |
| Text Secondary | `#475569` |
| Outline Border | `#E2E8F0` — borders only |
| Darkroom BG | `#070B14` |

**Three things deliberately *not* copied:**

1. **The design's typography.** Its `Type.kt` defines only `bodyLarge` —
   everything else falls to Material defaults. Ours is deliberately oversized
   (16sp floor) with `FontFamily.Default` → **Noto**, which is what renders all
   nine Indic scripts. Adopting a bundled display font would have silently
   broken text in **8 of 11 languages**.

2. **The design's severity colours.** Measured against white text, **4 of 5
   fail WCAG AA** — amber worst at **2.15:1**. Kept the exact hues, dropped each
   to its Tailwind-700 step; all five now pass (5.02–6.47). This also fixed a
   **pre-existing bug**: the old `grade2 #B06E00` measured 4.14:1, below the AA
   promise its own comment made.

3. **The design's fabricated telemetry.** Its HUD showed `EST. IOP 14.6 mmHg`,
   `PUPIL 3.4 mm`, `AXIS LOCK 99.4%` — all hardcoded constants for values the
   app never measures. IOP is the glaucoma number; a technician could copy it
   into a record. Not ported.

**A bug my own palette swap introduced, caught before shipping:** 33 places
used `colorScheme.outline` as a *text* colour. Old theme `#74777F` (readable);
new theme `#E2E8F0` (a border colour) → **1.23:1, invisible**. Repointed all 33
to `onSurfaceVariant` → **7.58:1**.

### 3.3 New interactive components — all pure Compose Canvas

Every one of these was ported from the design export. **None needs SceneView,
Filament, a `.glb` model, WebGL, or any new dependency** — they are drawn with
trigonometry.

| File | What it does |
|---|---|
| `RetinaScannerLogo.kt` | Home mark: **real fundus photograph** in a glass housing, rotating instrument bezel, corner-bracket reticle, crosshair, cyan beam sweeping the tissue |
| `InteractiveEye3D.kt` | Anatomical eye — sclera micro-vessels, layered iris striae, reactive pupil, corneal highlights, micro-saccades, spontaneous blinks, drag-to-look, rotates ±25° toward the selected eye |
| `EyelidShutter.kt` | Privacy lids closing over the retinal photo, with lash-margin shading |
| `LaserScanOverlay.kt` | Volumetric beam during inference |
| `ClinicalHUD.kt` | Monospace instrument strip — **only measured values** |
| `ReferralComponents.kt` | Dominant referral banner + 3-segment confidence meter |
| `feedback/ChimeFeedback.kt` | Synthesised 2.6 kHz confirmation chime + haptics |

**On the logo's retina:** it is a **healthy** (grade 0) image from the public
APTOS set, cropped with the app's own luminance>18 rule, 29 KB WebP. Two
deliberate constraints — branding should not depict disease, and **nothing is
marked on it**. The earlier drawn version had pulsing lesion rings; over a real
photograph those would be a fabricated finding on real tissue.

### 3.4 Interaction and flow

- **Consent chime + haptic** — fires on consent *given*, never on withdrawal
  (a tone on revoking reads as approval)
- **Sex dropdown** — male / female / other / prefer not to say. Display order
  only; `Sex` lives in `core/` and its ordinal is what gets written to stored
  records, so reordering the enum would corrupt existing history
- **Eye selection** — Left button left, 3D eye centre, Right button right
- **Slide transitions** — forward left, back right, 260 ms
- **Darkroom viewfinder** — everything outside the aperture painted out;
  `BlendMode.Clear` on a `graphicsLayer()` so it punches through the scrim, not
  the window
- **Heat-map slider** — cross-fade rather than on/off, so a clinician can wipe
  it across the tissue
- **Language bottom sheet** — springs up over the nav host, back-stack intact
- **Camera rationale dialog** — explains *before* the system prompt
- **Intake breadcrumb** — three thin segments, consent / details / eye
- **Grade 4 pulse** — slow opacity cycle, never a blink
- **Urgent heartbeat haptic** — double pulse on URGENT / IMMEDIATE

### 3.5 The most valuable single change

**`borderlineReferral` was computed in `core/` and never shown anywhere in the
UI.**

The app refers at expected grade **1.15**, below the rounding point of 1.5. So a
scan can display **"Grade 1 — Mild"** and still be referred. Before this
session the worker saw a mild grade and an urgent instruction with **no
explanation** — a screen contradicting itself.

It now says *"Referral prioritised — borderline risk markers"*, in all 11
languages, inside a referral banner that outranks the grade.

### 3.6 A permission dialog I built differently from the request

The brief asked for a popup with *"allow / allow for limited time / don't
allow"* for file access. Not built, because it would be theatre:

- Those three options **are Android's own** `READ_MEDIA_IMAGES` prompt
- The app **never requests that permission** — it uses `PickVisualMedia`, the
  system photo picker, which needs *no* storage permission at all
- A custom dialog with those buttons would grant and deny nothing

Instead the dialog went where there **is** a real permission — the camera —
explaining why before the system prompt appears.

---

## 4. Every document, and when to use it

### Written this session

| File | Use it for |
|---|---|
| **`PROJECT_DOSSIER.md`** | *This file.* Start here |
| **`MASTER_BRIEF.md`** | **The complete brief.** Medicine → ML → engineering → business → judge Q&A. Everything, in depth |
| **`JUDGE_BRIEF.md`** | Tighter version of the above for fast revision |
| **`APP_WORKFLOW.md`** | The app's *behaviour* — pipeline, decision rules, every outcome. **Upload this to AI Studio** |
| **`DESIGN_BRIEF.md`** | UI spec — every screen in every state. Use only if asking for more design |

### Pre-existing

| File | Contents |
|---|---|
| `HANDOFF.md` | Engineering handoff, failed attempts (§5 — read before coding) |
| `PITCH_PACK.md` | Pitch script and objection handling |
| `README.md` | Repo overview and setup |
| `BENCHMARK_METHODOLOGY.md` | How the field benchmark measures power |
| `TECH_STACK_EXPLAINED.md` | Stack rationale |
| `TASKS_*.md` | Per-person work packages |
| `PHONE_TEST_SHEET.md` | Manual device test checklist |

**If you only read two:** `MASTER_BRIEF.md` to prepare, `APP_WORKFLOW.md` to
feed any AI tool.

---

## 5. Screenshots — `docs/screenshots/walkthrough/`

17 captures, all from the running app on the physical device. Not mockups.

| # | Screen |
|---|---|
| 01 | Home (drawn logo) |
| 02 | Consent + intake, sex dropdown |
| 03 | Consent given |
| 04 | Eye selection with the 3D eye |
| 05 | Capture — clinical HUD, darkroom |
| 06 | Darkroom mask over a lit scene |
| 07 | System photo picker |
| 08 | **Result — Moderate, High, Grad-CAM on lesions** |
| 09 | Privacy shutter closed |
| 10 | Result advice |
| 11 | Past checks |
| 12 | Settings |
| 13 | Language bottom sheet — four scripts |
| 14 | Intake breadcrumb |
| 15 | Result with referral banner |
| 16 | **Quality rejection — 43%, refused to grade** |
| 17 | **Home with the real fundus logo** |

**08 and 16 are the two that matter.** 08 is real inference on a ground-truth
*moderate* image, graded Moderate at High confidence with heat-map hotspots on
the lesions. 16 is the gate refusing a non-retina photo. Together they prove the
pipeline works *and* knows when not to answer.

---

## 6. The numbers, condensed

```
MODEL      EfficientNet-B0, 456px, ImageNet pre-trained, fine-tuned
           16 MB ONNX opset 17 · PyTorch<->ONNX parity 1.09e-4
DATA       APTOS 2019, 3,662 images, 85/15 stratified, seed 143
TRAINING   25 epochs of 30 (early stop), best epoch 15
           AdamW 2e-4 · cosine · AMP · batch 8 · dropout 0.4
LOSS       weighted CE (ls 0.05) + 0.5 x MSE(expected_grade, true_grade)

QWK        0.9324        accuracy   0.8333 (455/546)
macro      P 0.7142 / R 0.7460 / F1 0.7212
weighted   P 0.8525 / R 0.8333 / F1 0.8398

REFERRAL @ expected grade >= 1.15
           sens 98.2% · spec 92.9% · F1 0.9414 · 4 missed of 221
           (at rounding 1.50: 91.4% / 94.8%, 19 missed)

CONFIDENCE HIGH >=0.90 -> 93.3% correct · MEDIUM -> ~70% · LOW -> 50.0%
           non-monotonic: 0.90-0.95 = 97.6%, 0.95-1.00 = 83.9%

DEVICE     latency p50 101.38 ms · p90 109.65 · p99 116.12
           0.33-0.37 mWh/screening · 245-321 patients/charge
           (10-second sample — runs disagree 40%, disclose it)

MARKET     global AI-DR US$0.40B (2024) -> US$2.22B (2033), ~21% CAGR
           India: ~125M diabetics, 12.5% DR prevalence,
           1 retina specialist per 1.26M people
```

---

## 7. How to build, run and reproduce

```powershell
$env:JAVA_HOME    = "D:\android-toolchain\jdk"
$env:ANDROID_HOME = "D:\android-toolchain\sdk"
$env:PATH = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:PATH"

cd "D:\SOHAM ALL\hackathons\IQOO\RetinaSightApp"
D:\android-toolchain\gradle\bin\gradle.bat assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

**Use a USB cable, not wireless ADB** — wireless drops on screen lock and Wi-Fi
change. The one thing USB cannot do is the energy benchmark, because it charges
the phone and the runner correctly refuses.

**Reproduce the model metrics:**

```
python scripts/validate_onnx.py --data <omnikon>/ml/data/aptos_456 \
    --split val --seed 143
```

Expect QWK 0.9324, accuracy 0.8333, 98.2%/92.9% at 1.15. Keep `cv2`
uninstalled — with OpenCV present the resize switches to `INTER_AREA` and stops
matching the phone's Pillow BILINEAR. The script warns you.

**Check translations:**

```
python scripts/check_translations.py     # expect 198/198 across 11 locales
```

---

## 8. Rules that must not be broken

These are load-bearing. Each exists because breaking it caused a real defect.

1. **Never show a clinical value the app does not measure.** No IOP, no pupil
   diameter, no axis lock. A technician will copy it into a record.
2. **Never say "diagnosis".** Screening and triage.
3. **Never quote an unmeasured number.** Say "a ten-second sustained-load
   sample", never "over a camp".
4. **Colour is never the only signal.** Icon + words + speech always accompany.
5. **No hardcoded user-facing text.** All 11 locales or it does not ship.
6. **Keep `FontFamily.Default`.** A bundled display font breaks 8 languages.
7. **The diagnostic path never touches the network.**
8. **Consent precedes capture.** Mandatory, timestamped.
9. **Do not touch `core/`** for UI work — the model, inference, preprocessing
   and referral logic are the process, not the presentation.
10. **The preprocessing contract is fixed.** No Ben Graham, no circle mask —
    either silently degrades accuracy while still returning a confident number.

---

## 9. What to do next, in order

1. **Commit.** 33 modified + 11 new files sit in the working tree. Needs your
   call on authorship (rotation says `chitrangad-ram-sapate`).
2. **External validation on IDRiD** — the single highest-value missing number.
   Harness is written and verified; only the dataset is missing. Expect
   0.80–0.88 and publish it.
3. **One unplugged paced benchmark** — 50 screenings, 30 s per patient, ~25 min.
   Gives a defensible patients-per-charge figure and removes the hedge.
4. **Native review of 8 locales** — in harm order: `urgency_*` first, then
   `grade_*_desc`, then consent and disclaimer.
5. **Rehearse the demo** — the path in `MASTER_BRIEF.md` §E.3, including
   airplane mode and the non-retina rejection.

---

## 10. The honest summary

**Strong:** a genuinely working offline medical AI on a phone, with reproducible
metrics, a defensible referral threshold, calibrated confidence, 11 languages
with voice, and a quality gate that refuses to answer when it should. The
engineering is real and every claim in these documents can be checked.

**Weak, and stated first:** internal validation only, no regulatory clearance,
power figures from a ten-second window, and 8 languages awaiting native review.

The project's most valuable habit is that it says these things before anyone
asks. Keep doing that — it is the difference between a demo and a medical
device people would actually deploy.
