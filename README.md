# RetinaSight AI

Diabetic retinopathy screening that runs **entirely on an Android phone** — no
server anywhere in the diagnostic path — and tells the patient the result **out
loud in their own language**.

Built for the iQOO Hackathon 2026 (Pune). Apache-2.0 model weights, open code.

---

## What problem this actually solves

India has roughly 25,000 vision centres with a fundus camera and **no
ophthalmologist**. Images from screening camps queue for a human grader for
days or weeks. By the time a grade comes back the patient has gone home, and
follow-up collapses.

> **The referral decision happens while the patient is still in the chair.**

The users are vision-centre technicians, NGO screening camps (Aravind, LVPEI,
Sightsavers) and teleophthalmology programmes — not patients themselves.

### An honest constraint, stated up front

**A smartphone camera cannot photograph a retina.** It physically requires
optics through the pupil. This app is the *grading and workflow* layer; capture
needs a fundus adapter (D-EYE, oDocs, Peek) or an existing clinic camera. The
capture screen says so.

---

## What it does

| | |
|---|---|
| Grades DR 0–4 | EfficientNet-B0 at 456 px, ONNX Runtime on NNAPI (NPU/GPU) |
| Explains the grade | Class activation heat map aligned to the preprocessed image |
| Speaks the result | Android TTS, 11 Indian languages |
| Gates bad photos | Blur / lighting / framing / retina-visibility checks before grading |
| Records consent | Mandatory, stored as a timestamp, enforced before capture |
| Works offline | The eye photograph never leaves the phone; only structured results sync |
| Measures itself | On-device benchmark: latency, power, patients-per-charge, thermal curve |

---

## Model

`app/src/main/assets/dr-v2.onnx` (16 MB), EfficientNet-B0, 456 px, trained on
APTOS 2019 (3,662 images) with an ordinal objective.

| Metric | Value |
|---|---|
| QWK (shipped checkpoint) | **0.9324** |
| QWK (3 seeds) | 0.9271 ± 0.0062 |
| Accuracy | 83.3% |
| Referable sensitivity / specificity **as shipped** | **98.2% / 92.9%** |

Measured on device (vivo iQOO 15, Snapdragon 8 Elite Gen 5, NNAPI, unplugged,
100 screenings, 2026-09-05):

| Latency | p50 | p90 | p99 |
|---|---|---|---|
| per image | **101.4 ms** | 109.7 ms | 116.1 ms |

No thermal throttling across the run.

### The referral threshold is deliberately not balanced

The grader decides a grade by rounding the softmax-weighted mean grade, which
refers at an expected grade of 1.5. That is a balanced operating point, and
balance is the wrong objective for screening: a missed referral can end in
blindness, a false alarm costs one clinic visit.

Sweeping the threshold over the held-out validation split (n=546, 221 referable):

| Threshold | Sensitivity | Specificity | Missed | False alarms |
|---|---|---|---|---|
| 1.50 — rounding | 91.4% | 94.8% | 19 | 17 |
| **1.15 — shipped** | **98.2%** | **92.9%** | **4** | **23** |
| 1.00 | 100% | 84.3% | 0 | 51 |

15 fewer missed patients for 6 more false alarms. See
[`ReferralPolicy.kt`](RetinaSightApp/app/src/main/java/com/retinasight/ai/core/model/ReferralPolicy.kt),
which carries the full sweep in its doc comment. The *displayed grade* still
comes from the model graph's own rounded output, so QWK is unaffected — only
the referral decision moved.

### Limits, stated plainly

- **Internal validation only.** A stratified split of APTOS: same dataset, same
  cameras. This is not external validation and will not hold at 0.93 on a
  different camera. IDRiD / Messidor is the next step.
- **Not a medical device.** No regulatory clearance, `clinically_validated: false`
  in the model card. It is a screening aid requiring clinician confirmation.
  The word "diagnosis" is never used.
- **Power, patients-per-charge and thermal are extrapolated from a ~10 second
  sustained-load sample**, not a real camp. 100 back-to-back inferences take
  10.4 s, over which the battery temperature did not move at all
  (33.2 → 33.2 °C). Latency is solid; treat the rest as an upper-bound estimate.

---

## Build

Requires JDK 17 and the Android SDK. A portable toolchain lives outside the
repo; point `JAVA_HOME` and `ANDROID_HOME` at your own if you have them.

```bash
cd RetinaSightApp
gradle assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### The narrator model is not in this repo

The optional on-device narrator is Qwen2.5-1.5B-Instruct (int8, ~1.5 GB,
Apache-2.0) via MediaPipe Tasks GenAI. It is far too large for git and is never
bundled in the APK. Download the `.task` build and push it to:

```
/sdcard/Android/data/com.retinasight.ai/files/llm/narrator.task
```

The app runs fully without it. The narrator only *restates* the CNN's decision
in simpler words — it never decides anything, and its output is discarded if it
contains byte-BPE artefacts.

---

## Languages

11 languages: English, Hindi, Marathi, Tamil, Telugu, Kannada, Bengali,
Gujarati, Malayalam, Punjabi, Odia.

```bash
python scripts/check_translations.py
```

verifies every locale has every key with matching format placeholders, and
fails the build if not — a dropped `%1$s` is a crash and a missing key silently
shows a patient advice in a language they may not read.

> **The eight non-English/Hindi/Marathi locales are machine-drafted and are
> awaiting native-speaker review.** Each file says so in a header comment. These
> strings tell a patient how urgently to see a doctor; a mistranslation is a
> clinical error.

---

## Design rules

These are load-bearing. Break them only deliberately:

1. **Nothing hardcoded in the medical path.** Grade, confidence, heat map,
   urgency and explanation all derive from the real model and the actual image.
2. **The diagnostic path never touches the network.** `INTERNET` exists solely
   for optional clinic upload.
3. **The eye photograph never leaves the phone.** Only the structured result syncs.
4. **The explanation is templated, not generated.** It can be wrong about the
   grade; it can never invent a treatment.
5. **Consent precedes capture**, enforced in code.
6. **Never claim "diagnosis."**
7. **Never quote a number that has not been measured.**

---

## Repository layout

| Path | What |
|---|---|
| `RetinaSightApp/` | The Android app (Kotlin, Jetpack Compose) |
| `scripts/` | Translation checker, environment bootstrap |
| `docs/screenshots/` | Device screenshots |
| `PITCH_PACK.md` | Pitch and jury Q&A |
| `TECH_STACK_EXPLAINED.md` | Spoken technical walkthrough |
| `BENCHMARK_METHODOLOGY.md` | How the on-device benchmark measures power |
| `HANDOFF.md` | Full engineering state, including failed approaches |

`HANDOFF.md` §5 lists the dead ends that cost hours. Read it before changing
preprocessing, localisation or the coroutine setup.
