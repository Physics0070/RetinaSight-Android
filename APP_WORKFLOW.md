# RetinaSight AI — what the app does, and what it outputs

Upload this to Google AI Studio alongside any design request. It describes the
**behaviour** of the app: every step, every decision rule, and every outcome
the system can produce.

Every number here is taken from the shipped code. None are illustrative.

---

## 1. In one line

A healthcare worker photographs a patient's retina through a fundus adapter;
the phone grades diabetic retinopathy on-device in about 100 ms, decides whether
the patient needs an eye doctor, and says the answer out loud in the patient's
language. No server is involved in the diagnostic path.

---

## 2. The pipeline

```
fundus photograph
      ↓
[1] quality gate          → REJECT (retake) or PASS
      ↓
[2] preprocessing         → 456×456 normalised tensor
      ↓
[3] ONNX model (NNAPI)    → logits(1,5), cam(1,5,15,15), grade(1)
      ↓
[4] derived values        → grade, confidence, expected grade, heat map
      ↓
[5] referral decision     → refer / do not refer
      ↓
[6] urgency + explanation → what to do, in 11 languages
      ↓
[7] speech + local record
```

Steps 1–7 all run on the phone. The photograph never leaves the device.

---

## 3. Step by step, with every outcome

### Step 0 — Consent (before any image exists)

Consent is mandatory and timestamped. Nothing can be captured without it.

| Input | Outcome |
|---|---|
| Consent not given | Capture unreachable. Continue disabled, with the reason shown |
| Consent given | Timestamp stored (`consentGivenAtMillis`); capture unlocked |
| Consent + name + age 1–120 | "Continue to photo" — record carries patient details |
| Consent only, details skipped | Capture proceeds; record is anonymous |

Patient fields: name, age, sex (male / female / other / prefer not to say),
phone, known diabetic (yes / no / unknown), years since diagnosis, and which
eye (left / right).

A patient marked diabetic for **≥ 10 years** is flagged elevated-risk. This is
context for the worker only — **it does not influence the model or the grade.**

---

### Step 1 — Image quality gate

The image is scored before it is graded. If it is not gradeable, the app
**refuses to grade it** rather than returning a confident answer on a bad
photograph.

Thresholds:

```
overall     ≥ 0.55        blur       ≥ 0.45
lighting    ≥ 0.40        framing    ≥ 0.40
visibility  ≥ 0.50        minimum size 224 × 224
```

Six failure modes, which can occur together:

| Issue | Meaning | Told to the worker |
|---|---|---|
| `BLUR` | sharpness below reference | hold steady, let it focus |
| `LOW_LIGHT` | underexposed | more light |
| `OVEREXPOSED` | > 2% clipped pixels | less light |
| `POOR_FRAMING` | retina not centred | recentre |
| `RETINA_NOT_VISIBLE` | may not be a fundus image at all | check the adapter |
| `LOW_RESOLUTION` | smaller than 224 × 224 | use a larger image |

**Outcomes:** `PASS` → proceed to grading. `REJECT` → retake screen showing the
score and each failed check. Rejection is a normal event, not an error.

---

### Step 2 — Preprocessing (fixed contract)

Must match training exactly or the grade is invalid:

1. Crop to the retinal disc: luminance (BT.601) > **18**, bounding box, **+2% padding**
2. Resize to **456 × 456**, Pillow-equivalent antialiased BILINEAR
3. `/255`, ImageNet mean/std, NCHW float32

No Ben Graham filtering. No circle mask. Either changes the input distribution.

---

### Step 3 — The model

EfficientNet-B0 at 456 px, exported to ONNX (16 MB), trained on APTOS 2019.

Runs on **NNAPI** (NPU/GPU) where available, CPU otherwise. The provider
changes speed only — never the output.

Outputs `logits (1,5)`, `cam (1,5,15,15)`, `grade (1)`.

**Measured performance, held-out APTOS split, n = 546:**

```
quadratic weighted kappa   0.9324
exact-grade accuracy       83.3%
referable sensitivity      98.2%   at the shipped threshold
referable specificity      92.9%   at the shipped threshold
missed referable patients  4 of 221
```

This is **internal validation** — same dataset, same cameras. It is not
external validation and must never be described as clinical performance.

---

### Step 4 — Derived values

**Grade (what is displayed).** Five classes:

| Grade | Name |
|---|---|
| 0 | No DR |
| 1 | Mild NPDR |
| 2 | Moderate NPDR |
| 3 | Severe NPDR |
| 4 | Proliferative DR |

The graph itself rounds the expected grade. Using argmax instead would disagree
with the reported kappa.

**Expected grade.** `E[g] = Σ i × softmax(logits)ᵢ` — a continuous 0–4 value.
This, not the displayed grade, drives the referral decision.

**Confidence.** Softmax probability of the displayed class, binned into three
bands whose cut points were **measured, not chosen**:

| Band | Range | How often the grade was actually correct |
|---|---|---|
| HIGH | ≥ 0.90 | 93.3% |
| MEDIUM | 0.55 – 0.90 | ~70% |
| LOW | < 0.55 | 50.0% |

**Important subtlety:** confidence is *not* monotonic with accuracy. The band
0.90–0.95 is 97.6% correct, but 0.95–1.00 falls to **83.9%**. The model is
overconfident at the very top.

Below 0.55 the app additionally advises a retake.

**Heat map.** Class activation map from the classifier weights — a 15×15 grid
upsampled and overlaid on the *preprocessed* image, not the original photo
(overlaying the original misaligns it).

---

### Step 5 — Referral decision

This is the decision the app exists to make, and it is **separate from the
grade**.

Referral triggers when **expected grade ≥ 1.15**, which is *below* the rounding
point of 1.5.

Why: missing a referable patient and over-referring a healthy one are not
symmetric errors. On the validation split:

| Threshold | Sensitivity | Specificity | Missed | False alarms |
|---|---|---|---|---|
| 1.50 (rounding) | 91.4% | 94.8% | 19 | 17 |
| **1.15 (shipped)** | **98.2%** | **92.9%** | **4** | **23** |

Fifteen fewer missed patients for six more false alarms.

**The consequence that matters for design:** a scan can display **"Grade 1 —
Mild"** and *still be referred*. This is the **borderline referral** state —
expected grade between 1.15 and 1.5. The grade says mild, the action says see a
doctor, and both are correct.

---

### Step 6 — Urgency and explanation

Urgency normally follows the grade:

| Grade | Urgency |
|---|---|
| 0 No DR | `ROUTINE` |
| 1 Mild | `MONITOR` |
| 2 Moderate | `SOON` |
| 3 Severe | `URGENT` |
| 4 Proliferative | `IMMEDIATE` |

**Exception:** a borderline referral is raised to `SOON`, overriding the
grade's own `MONITOR`. Under-referring is the error this app is built to avoid.

The explanation is assembled from **translated templates** — it is never
generated text. An optional on-device model may restate the result in simpler
words, but it only rephrases facts already decided, never decides anything, and
its output is discarded if it fails a script check.

---

### Step 7 — Output to the patient

- **Spoken summary**, automatically, on arrival at the result. Three facts
  only: what was found, how sure the check is, what to do. Headings and buttons
  are not narrated.
- **Eleven languages**: English, Hindi, Marathi, Bengali, Gujarati, Punjabi,
  Odia, Tamil, Telugu, Kannada, Malayalam.
- **Local record**: grade, confidence, expected grade, urgency as given, eye,
  patient details if entered, timestamp, image file. Stored on the device.

---

## 4. Complete outcome matrix

What a worker sees for a given combination:

| Expected grade | Displayed grade | Referred? | Urgency |
|---|---|---|---|
| 0.00 – 1.14 | 0 or 1 | No | `ROUTINE` / `MONITOR` |
| **1.15 – 1.49** | **1 (Mild)** | **Yes** | **`SOON`** (raised) |
| 1.50 – 2.49 | 2 Moderate | Yes | `SOON` |
| 2.50 – 3.49 | 3 Severe | Yes | `URGENT` |
| 3.50 – 4.00 | 4 Proliferative | Yes | `IMMEDIATE` |

Crossed with confidence:

| Confidence | Effect on what is shown |
|---|---|
| HIGH ≥ 0.90 | Grade presented plainly |
| MEDIUM 0.55–0.90 | Grade presented with reduced certainty |
| LOW < 0.55 | Grade shown **plus an advisory to retake** |

**The referral decision holds at every confidence level.** A low-confidence
scan that crosses 1.15 is still referred — uncertainty is a reason to send the
patient onward, not to hold them back.

---

## 5. States that are not about the result

| Condition | Behaviour |
|---|---|
| Offline | Everything works. Offline is the normal, expected state |
| Online | Only affects optional clinic sync, never the grading |
| Camera permission denied | Import from a fundus camera still works — never a dead end |
| No fundus adapter | Stated plainly: a phone camera alone cannot image a retina |
| Narrator model absent | Templated explanation shown instead |
| System voice missing | Offers to install it; text remains |
| Model still loading | Capture waits, is not blocked |

---

## 6. What the app does not do

- It does not diagnose. It screens and triages.
- It does not replace an ophthalmologist — it decides **who needs one**.
- It does not upload the photograph.
- It does not measure intraocular pressure, pupil diameter, visual acuity, or
  anything other than what is listed above. **Any such value shown would be
  fabricated.**
- It has no regulatory clearance. `clinically_validated: false`.

---

## 7. The clinical workflow it replaces

Today:

```
patient → specialist → screening → referral
```

Images queue for a human grader for days or weeks. The patient has gone home.

With RetinaSight:

```
patient → health worker → fundus image → RetinaSight
        → risk / referral in ~100 ms → ophthalmologist only where needed
```

The referral decision happens while the patient is still in the chair.
