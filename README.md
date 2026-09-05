<div align="center">

# 👁️ RetinaSight AI

**Diabetic retinopathy screening that runs entirely on an Android phone.**

No server in the diagnostic path. The result is spoken aloud in the patient's own language.

<br>

![Platform](https://img.shields.io/badge/platform-Android%208.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-Compose-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![Runtime](https://img.shields.io/badge/ONNX_Runtime-NNAPI-005CED?style=for-the-badge&logo=onnx&logoColor=white)
![Offline](https://img.shields.io/badge/inference-100%25_on--device-success?style=for-the-badge)

![QWK](https://img.shields.io/badge/QWK-0.9324-blue?style=flat-square)
![Sensitivity](https://img.shields.io/badge/referable_sensitivity-98.2%25-brightgreen?style=flat-square)
![Latency](https://img.shields.io/badge/p50_latency-101–114_ms-blue?style=flat-square)
![Languages](https://img.shields.io/badge/languages-11-orange?style=flat-square)
![Licence](https://img.shields.io/badge/model-Apache--2.0-lightgrey?style=flat-square)

<br>

> ### The referral decision happens while the patient is still in the chair.

</div>

---

<details>
<summary><b>📑 Contents</b></summary>

- [The problem](#-the-problem)
- [An honest constraint](#-an-honest-constraint-stated-up-front)
- [What it does](#-what-it-does)
- [How it works](#-how-it-works)
- [The model](#-the-model)
- [Why the referral threshold is deliberately unbalanced](#-why-the-referral-threshold-is-deliberately-unbalanced)
- [Measured on device](#-measured-on-device)
- [Limits](#-limits-stated-plainly)
- [Build](#-build)
- [Languages](#-languages)
- [Design rules](#-design-rules)
- [Repository layout](#-repository-layout)

</details>

---

## 🎯 The problem

India has roughly **25,000 vision centres** with a fundus camera and **no ophthalmologist**.

Images from screening camps queue for a human grader for days or weeks. By the time a grade comes back, the patient has gone home and follow-up collapses.

```
Today        capture ──▶ upload ──▶ queue (days–weeks) ──▶ grade ──▶ patient unreachable
RetinaSight  capture ──▶ grade on the phone (~0.1 s) ──▶ referral, in the chair
```

**Who actually uses it:** vision-centre technicians, NGO screening camps (Aravind, LVPEI, Sightsavers), and teleophthalmology programmes — not patients themselves.

---

## ⚠️ An honest constraint, stated up front

> **A smartphone camera cannot photograph a retina.**
>
> It physically requires optics through the pupil. This app is the **grading and workflow layer**; capture needs a fundus adapter (D-EYE, oDocs, Peek) or an existing clinic camera. The capture screen says exactly this.

Any pitch implying phone-only capture is false, and a judge will break it in one question.

---

## ✨ What it does

| | Feature | How |
|:--:|---|---|
| 🔬 | **Grades DR 0–4** | EfficientNet-B0 @ 456 px, ONNX Runtime on NNAPI (NPU/GPU) |
| 🔥 | **Explains the grade** | Class activation heat map, aligned to the preprocessed image |
| 🔊 | **Speaks the result** | Android TTS across 11 Indian languages |
| 🚦 | **Gates bad photos** | Blur / lighting / framing / retina-visibility checks *before* grading |
| ✍️ | **Records consent** | Mandatory, stored as a timestamp, enforced before capture |
| 📴 | **Works offline** | The eye photograph never leaves the phone; only structured results sync |
| 📊 | **Measures itself** | On-device benchmark: latency, power, patients-per-charge, thermal curve |

---

## 🧭 How it works

```
   Consent ──▶ Patient details ──▶ Capture / import
       │                                  │
       │                                  ▼
       │                          Image quality gate ──✗──▶ Retake
       │                                  │ ✓
       │                                  ▼
       │                 Crop retina ▸ 456 px ▸ ImageNet normalise
       │                                  │
       │                                  ▼
       │                    EfficientNet-B0  (ONNX ▸ NNAPI)
       │                                  │
       │              ┌───────────────────┼───────────────────┐
       │              ▼                   ▼                   ▼
       │           logits             CAM 15×15          ordinal grade
       │              │                   │                   │
       └──────────────┴─────────┬─────────┴───────────────────┘
                                ▼
              Grade · Heat map · Templated explanation · Spoken summary
                                │
                                ▼
                   Local history  ──(optional, result only)──▶  Clinic
```

Everything above the clinic hand-off runs with the radio off.

---

## 🧠 The model

`app/src/main/assets/dr-v2.onnx` — 16 MB, EfficientNet-B0, 456 px, trained on APTOS 2019 (3,662 images) with an ordinal objective.

<table>
<tr><th align="left">Metric</th><th align="left">Value</th></tr>
<tr><td>QWK — shipped checkpoint</td><td><b>0.9324</b></td></tr>
<tr><td>QWK — 3 seeds</td><td>0.9271 ± 0.0062</td></tr>
<tr><td>Accuracy</td><td>83.3%</td></tr>
<tr><td>Referable sensitivity / specificity <b>as shipped</b></td><td><b>98.2% / 92.9%</b></td></tr>
</table>

The model decides by **rounding the expected grade**, not by argmax — using argmax on device would silently disagree with the reported QWK.

---

## ⚖️ Why the referral threshold is deliberately unbalanced

Rounding the softmax-weighted mean grade refers at an expected grade of **1.5**. That is a *balanced* operating point, and balance is the wrong objective for screening:

> A missed referral can end in blindness. A false alarm costs one clinic visit.
> Those errors are not symmetric, so the threshold should not sit in the middle.

Sweeping the threshold over the held-out validation split (n = 546, 221 referable):

| Threshold | Sensitivity | Specificity | ❌ Missed | ⚠️ False alarms |
|---|---|---|---|---|
| 1.50 — rounding | 91.4% | 94.8% | 19 | 17 |
| **1.15 — shipped** | **98.2%** | **92.9%** | **4** | **23** |
| 1.00 | 100% | 84.3% | 0 | 51 |

**15 fewer missed patients for 6 more false alarms.**

The full sweep lives in the doc comment of [`ReferralPolicy.kt`](RetinaSightApp/app/src/main/java/com/retinasight/ai/core/model/ReferralPolicy.kt). The *displayed grade* still comes from the graph's own rounded output, so QWK is unaffected — only the referral decision moved.

---

## 📱 Measured on device

vivo iQOO 15 · Snapdragon 8 Elite Gen 5 · NNAPI · unplugged · 100 screenings · 2026-09-05

<table>
<tr><th align="left">Latency</th><th>p50</th><th>p90</th><th>p99</th></tr>
<tr><td>Run A</td><td><b>113.9 ms</b></td><td>125.6 ms</td><td>132.6 ms</td></tr>
<tr><td>Run B</td><td><b>101.4 ms</b></td><td>109.7 ms</td><td>116.1 ms</td></tr>
</table>

Raw CSVs for both runs are in [`exports/`](exports/) — timings, power, thermal. No patient data.

> [!WARNING]
> **Latency is measured. Power and thermal are not.**
>
> 100 back-to-back inferences finish in **10.4–11.8 seconds**. Over that window the battery temperature never moved (33.1 → 33.1 °C), so "no throttling" says nothing about a real camp. The two runs disagree by **40% on net power** (7,805 vs 10,891 mW) and on patients-per-charge (321 vs 245) — which is itself proof that a ten-second sample cannot measure power.
>
> Quote the millisecond figures freely. For power, say *"a ~10-second sustained-load sample"* — never *"over a camp"*. Fixing this is [Package A](TASKS_SOHAM.md).

---

## 🚧 Limits, stated plainly

| | Limit |
|:--:|---|
| 📊 | **Internal validation only.** A stratified split of APTOS: same dataset, same cameras. Not external validation; it will not hold at 0.93 on a different camera. IDRiD / Messidor is the next step ([Package C](TASKS_CHITRANGAD.md)). |
| 🏥 | **Not a medical device.** No regulatory clearance; `clinically_validated: false` in the model card. A screening aid requiring clinician confirmation. The word *diagnosis* is never used. |
| 🗣️ | **8 of 11 locales are unreviewed.** Machine-drafted, pending native-speaker review ([Packages B & C](WORK_SPLIT.md)). |
| 🔋 | **Power and thermal are extrapolated.** See the warning above. |

---

## 🔧 Build

Requires **JDK 17** and the **Android SDK**.

```bash
cd RetinaSightApp
gradle assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

<details>
<summary><b>The narrator model is not in this repo</b></summary>

<br>

The optional on-device narrator is **Qwen2.5-1.5B-Instruct** (int8, ~1.5 GB, Apache-2.0) via MediaPipe Tasks GenAI. Far too large for git, and never bundled in the APK. Download the `.task` build and push it to:

```
/sdcard/Android/data/com.retinasight.ai/files/llm/narrator.task
```

The app runs fully without it. The narrator only **restates** the CNN's decision in simpler words — it never decides anything, and its output is discarded if it contains byte-BPE artefacts.

</details>

---

## 🗣️ Languages

<div align="center">

English · हिन्दी · मराठी · தமிழ் · తెలుగు · ಕನ್ನಡ · বাংলা · ગુજરાતી · മലയാളം · ਪੰਜਾਬੀ · ଓଡ଼ିଆ

</div>

```bash
python scripts/check_translations.py
```

Verifies every locale has every key with matching format placeholders, and exits non-zero if not — a dropped `%1$s` is a crash, and a missing key silently shows a patient advice in a language they may not read.

> [!IMPORTANT]
> The eight non-English/Hindi/Marathi locales are **machine-drafted and awaiting native-speaker review**. Each file says so in a header comment. These strings tell a patient how urgently to see a doctor; a mistranslation is a clinical error, not a typo.

---

## 📐 Design rules

These are load-bearing. Break them only deliberately, and say so.

1. **Nothing hardcoded in the medical path.** Grade, confidence, heat map, urgency and explanation all derive from the real model and the actual image.
2. **The diagnostic path never touches the network.** `INTERNET` exists solely for optional clinic upload.
3. **The eye photograph never leaves the phone.** Only the structured result syncs.
4. **The explanation is templated, not generated.** It can be wrong about the grade; it can never invent a treatment.
5. **Consent precedes capture**, enforced in code.
6. **Never claim "diagnosis."**
7. **Never quote a number that has not been measured.**

---

## 📂 Repository layout

| Path | What |
|---|---|
| `RetinaSightApp/` | The Android app (Kotlin, Jetpack Compose) |
| `scripts/` | Translation checker, environment bootstrap |
| `exports/` | Raw on-device benchmark CSVs |
| [`WORK_SPLIT.md`](WORK_SPLIT.md) | The three work packages and who owns them |
| [`PITCH_PACK.md`](PITCH_PACK.md) | Pitch and jury Q&A |
| [`TECH_STACK_EXPLAINED.md`](TECH_STACK_EXPLAINED.md) | Spoken technical walkthrough |
| [`BENCHMARK_METHODOLOGY.md`](BENCHMARK_METHODOLOGY.md) | How the on-device benchmark measures power |
| [`HANDOFF.md`](HANDOFF.md) | Full engineering state, including failed approaches |

> 💡 `HANDOFF.md` §5 lists the dead ends that cost hours. **Read it before changing preprocessing, localisation or the coroutine setup.**

---

<div align="center">
<sub>Built for the iQOO Hackathon 2026 · Pune · Apache-2.0 model weights</sub>
</div>
