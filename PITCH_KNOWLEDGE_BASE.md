# RetinaSight AI — complete knowledge base

Everything here is read from the shipped code, the training run
(`efficientnet_b0-20260823-124225`), or a cited source. Nothing is estimated.
Where a number is uncertain or not ours, it is labelled.

---

# PART 1 — The medical side, in plain language

## 1.1 What diabetic retinopathy actually is

The retina is the light-sensitive tissue at the back of the eye. It is fed by
very fine blood vessels. Persistently high blood sugar damages the walls of
those vessels, and that damage progresses in a predictable order:

1. **Vessel walls weaken** → tiny bulges form, called **microaneurysms**. These
   are the earliest visible sign. They look like small red dots.
2. **Vessels leak** → blood escapes (**dot/blot haemorrhages**) and fatty
   protein deposits appear (**hard exudates**, yellow-white patches).
3. **Vessels block** → parts of the retina lose blood supply. Nerve-fibre
   tissue dies in small patches (**cotton-wool spots**, fluffy white marks).
   Veins become irregular in calibre (**venous beading**).
4. **The retina responds to starvation** by growing new vessels
   (**neovascularisation**). This is the dangerous step: the new vessels are
   fragile, bleed into the eye, and can pull the retina off its backing.

**The cruel part, and the reason screening exists:** stages 1–3 usually cause
*no symptoms at all*. Vision is normal. The patient feels fine. By the time
vision blurs, the damage is often at stage 4 and is largely irreversible.

So DR is not found by asking the patient how they see. It is found by *looking
at the retina* on a schedule, in people who feel completely well.

## 1.2 How a photograph of the retina is taken

A **fundus photograph** is a picture of the back of the eye, taken through the
pupil. It needs specialised optics — a lens system that focuses through the
pupil onto the retina and illuminates it at the same time.

**A phone camera alone cannot do this.** This is stated on the capture screen.
The app needs either a **fundus adapter** that clips over the phone (D-EYE,
oDocs, Peek) or an existing **clinic fundus camera** whose images are imported.
RetinaSight is the grading and workflow layer, not the optics.

## 1.3 The five-grade scale

The app uses the international clinical scale, the same one graders use:

| Grade | Name | What is on the retina |
|---|---|---|
| 0 | No DR | No visible lesions |
| 1 | Mild NPDR | Microaneurysms only |
| 2 | Moderate NPDR | More than mild, less than severe — haemorrhages, exudates |
| 3 | Severe NPDR | The "4-2-1 rule": haemorrhages in 4 quadrants, *or* venous beading in 2, *or* IRMA in 1 |
| 4 | Proliferative DR | New vessel growth, or vitreous/preretinal haemorrhage |

NPDR = non-proliferative. IRMA = intraretinal microvascular abnormality.

**"Referable" means grade 2 or worse.** That is the clinically standard cut —
it is the point at which a patient should be seen by an ophthalmologist rather
than simply re-screened next year. The whole app is built around getting that
one binary decision right.

## 1.4 What the model is actually looking at

It is not reading a checklist. A convolutional network learns filters that
respond to visual patterns, and for DR those patterns end up being:

- small round dark spots scattered across the retina (microaneurysms,
  haemorrhages)
- bright yellow-white flecks with hard edges (exudates)
- soft-edged pale patches (cotton-wool spots)
- irregular vessel calibre and abnormal branching

The **class activation map** (the heat map in the app) is how we show this. It
marks which regions of the image most drove the prediction. On the moderate
test case in the walkthrough, the hotspots land on the lesions — which is the
evidence that the model is responding to pathology rather than to some artefact
of the camera.

**Deliberate design choice:** the app does **not** apply Ben Graham filtering
or a circle mask, two common Kaggle preprocessing tricks. Both change the input
distribution away from what the model was trained on.

---

# PART 2 — The ML approach

## 2.1 What kind of model

**A convolutional neural network (CNN)**, specifically **EfficientNet-B0**.

**Why a CNN.** An image is a grid of pixels; a lesion is a small local pattern
that can appear anywhere. A CNN slides small learned filters across the whole
image, so a microaneurysm is detected the same way wherever it sits. Early
layers learn edges and blobs; deeper layers combine those into
lesion-like structures.

**Why EfficientNet-B0 specifically.** It is the smallest member of the
EfficientNet family, which scales depth, width and input resolution together
rather than one at a time. It gives near-ResNet-50 accuracy at roughly a fifth
of the parameters — which is what makes a **16 MB** on-device model possible.

**Transfer learning.** Training starts from **ImageNet weights** rather than
random initialisation (`pretrained=True`,
`EfficientNet_B0_Weights.DEFAULT`). With only ~3,600 retinal images, learning
edge and texture detectors from scratch is not feasible; the ImageNet features
transfer, and training only has to adapt them to retinal appearance. The final
classifier layer is replaced with a fresh `Linear(in_features, 5)`.

## 2.2 The data

| | |
|---|---|
| Dataset | **APTOS 2019** (Asia Pacific Tele-Ophthalmology Society), public |
| Total images | **3,662** |
| Train | **3,116** |
| Validation | **546** (held out, never trained on) |
| Split | Stratified, `val_fraction=0.15`, `seed=143` |
| Input size | **456 × 456** |

**Validation class distribution** — note how skewed it is, which drives several
later decisions:

```
No DR 270 · Mild 55 · Moderate 149 · Severe 28 · Proliferative 44
```

## 2.3 Preprocessing (identical in training and on the phone)

1. Crop to the retinal disc: keep pixels with BT.601 luminance **> 18**, take
   the bounding box, add **2% padding**
2. Resize to **456 × 456**, antialiased bilinear
3. Scale to 0–1, apply **ImageNet mean/std** normalisation, transpose to NCHW
   float32

This contract is enforced on both sides. If the phone preprocessed differently
from training, the reported accuracy would not describe what the phone does.

## 2.4 Augmentation

Applied to training images only, and deliberately restricted to what is
*physically plausible* for fundus photography:

| Augmentation | Probability |
|---|---|
| Horizontal flip | 0.5 |
| Vertical flip | 0.2 |
| 90°/180° rotation | 0.5 |
| Brightness + contrast jitter (0.85–1.15) | 0.6 |

**Colour-channel shuffling is deliberately excluded.** Fundus images are
red-dominant, and that red dominance is genuine signal — shuffling channels
would teach the model to ignore a real feature.

## 2.5 The loss function — the most important technical decision

Standard cross-entropy treats the five grades as **unordered categories**.
Under plain CE, predicting "No DR" for a proliferative case costs exactly the
same as predicting "Mild" for a moderate one. Clinically those are wildly
different errors — one sends a nearly-blind patient home.

But the metric this task is judged on, quadratic weighted kappa, penalises by
**squared distance**. So optimising CE while being measured on QWK is a
structural mismatch.

The fix — `OrdinalAwareLoss` — keeps the five-logit output (so the ONNX
contract is unchanged) and adds a distance term:

```
loss = CrossEntropy(logits, y)  +  λ · (E[grade] − y)²

where  E[grade] = Σ k · softmax(logits)_k
       λ = 0.5   (distance_weight)
```

Plus **class weights** on the CE term (the data is heavily skewed toward grade
0) and **label smoothing = 0.05**.

This is why the model produces a meaningful *continuous* expected grade — and
that continuous value is what the referral threshold operates on.

## 2.6 Training hyperparameters (exact, from `config.json`)

```
architecture      efficientnet_b0      optimiser        AdamW
epochs (max)      30                   learning rate    2e-4
epochs run        25 (early stopped)   weight decay     1e-4
batch size        8                    dropout          0.4
image size        456                  mixed precision  on (AMP)
seed              143                  class balancing  on
patience          10                   distance weight  0.5
selection metric  quadratic_kappa      label smoothing  0.05
```

**Model selection:** the checkpoint saved is the epoch with the best
*validation QWK*, not the last epoch and not the best training loss.

## 2.7 The actual training curve (all 25 epochs)

```
ep  train_loss  val_loss  val_acc  val_F1   val_QWK
 1    1.5750     1.2079   0.7436   0.6099   0.9028
 4    1.1331     1.0936   0.8242   0.6989   0.9295
10    0.8595     1.0821   0.8205   0.7062   0.9312
15    0.6660     1.2076   0.8333   0.7212   0.9324   <-- SELECTED
20    0.5838     1.2103   0.8352   0.7118   0.9249
25    0.5286     1.1912   0.8480   0.7189   0.9298
```

**What this curve honestly shows:** training loss falls steadily (1.575 → 0.529)
while **validation loss bottoms out around epoch 10 and then rises**. That is
textbook **overfitting** beginning — the model is memorising the training set.
Early stopping on QWK with patience 10 is what caught it. Epoch 15 was selected
because it had the best validation QWK (0.9324), even though epoch 25 has
higher raw accuracy (0.8480). We optimise for the ordinal metric, not accuracy.

**Cycles:** 25 epochs × 3,116 images = **~77,900 image presentations**, at batch
size 8 = **~9,738 gradient updates**.

## 2.8 The results — every metric, real

### Per-class, on the 546-image held-out split

| Class | Support | Precision | Recall | F1 |
|---|---|---|---|---|
| No DR | 270 | **1.0000** | 0.9741 | 0.9869 |
| Mild | 55 | 0.5938 | 0.6909 | 0.6387 |
| Moderate | 149 | 0.7836 | 0.7047 | 0.7420 |
| Severe | 28 | 0.4043 | 0.6786 | 0.5067 |
| Proliferative | 44 | 0.7895 | 0.6818 | 0.7317 |
| **Macro avg** | 546 | **0.7142** | **0.7460** | **0.7212** |
| **Weighted avg** | 546 | **0.8525** | **0.8333** | **0.8398** |

```
Exact-grade accuracy   0.8333
Quadratic weighted κ   0.9324
```

**Read this honestly.** Precision on **No DR is 1.0000** — when the model says
"no disease", it was never wrong on this split. That is the single most
important cell in the table for a screening tool. Conversely **Severe
precision is 0.4043** — the model over-calls severe. In a screening context
that direction of error is far safer, but it is a real weakness and should be
stated rather than hidden.

### Confusion matrix (rows = truth, columns = predicted)

```
              No DR  Mild  Mod  Sev  Prolif
No DR          263     7    0    0     0
Mild             0    38   17    0     0
Moderate         0    19  105   21     4
Severe           0     0    5   19     4
Proliferative    0     0    7    7    30
```

Note there is **no entry in the bottom-left region** — no proliferative or
severe case was ever called "No DR". Errors are adjacent-grade, which is
exactly what the ordinal loss was designed to produce.

### The binary decision that actually matters — referable (grade ≥ 2)

At the **shipped threshold of expected grade ≥ 1.15**:

```
TP 217   FN 4   FP 23   TN 302

Sensitivity (recall)  0.9819      Specificity  0.9292
Precision (PPV)       0.9042      NPV          0.9869
F1                    0.9414      Accuracy     0.9505
```

At the naive rounding threshold of 1.5: sensitivity 0.9140, specificity 0.9477,
F1 0.9182.

**Moving the threshold from 1.5 to 1.15 takes missed referable patients from 19
down to 4, at a cost of 6 additional false alarms.** In screening, a false
alarm costs one unnecessary clinic visit; a miss costs sight.

## 2.9 How accuracy is calculated, and what QWK means

**Accuracy** = correct predictions ÷ total = 455/546 = 0.8333. On its own it is
a poor metric here, because guessing "No DR" for everything would score 49%
while being clinically useless.

**Quadratic weighted kappa (QWK)** is the standard DR metric. It measures
agreement with the human grader *beyond chance*, penalising by squared
distance:

```
κ = 1 − Σ(w · O) / Σ(w · E)      where  w_ij = (i − j)² / (K − 1)²
```

O = observed confusion matrix, E = expected under chance agreement. Being one
grade out is penalised 1 unit; being four grades out is penalised 16. **0 =
chance, 1 = perfect.** Our **0.9324** means near-expert agreement.

## 2.10 Independent verification

The shipped `dr-v2.onnx` was re-run from scratch through
`scripts/validate_onnx.py`, reading the same file that ships in the APK, with
the serving preprocessing. It reproduces the training checkpoint **exactly** —
all 25 confusion-matrix cells, accuracy 0.8333, QWK 0.9324, sensitivity 0.9140
at 1.5. Artefact: `exports/validation_aptos_val_seed143.json`.

## 2.11 What we must NOT claim

- **This is internal validation.** Same dataset, same cameras, a held-out split.
  It is **not** external validation and **not** clinical performance.
- **No regulatory clearance.** `clinically_validated: false` in the model card.
- **Never the word "diagnosis."** Screening and triage only.
- The honest gap: **IDrid/Messidor external validation has not been run.** The
  harness is written and verified; only the dataset is missing. Expect a drop
  to roughly 0.80–0.88 QWK on an unseen camera — and saying "0.93 internal,
  0.85 external" is a far stronger claim than one number with no
  generalisation evidence.

---

# PART 3 — Tech stack

## 3.1 On the phone

| Layer | Technology |
|---|---|
| Language | **Kotlin** |
| UI | **Jetpack Compose** + Material 3 |
| Inference | **ONNX Runtime for Android** |
| Acceleration | **NNAPI** → Hexagon NPU / GPU, automatic **CPU fallback** |
| Camera | **CameraX** (`Preview` + `ImageCapture`) |
| Image import | `PickVisualMedia` — the system photo picker, **needs no storage permission** |
| Speech | Android **TextToSpeech**, 11 languages |
| Optional narrator | Qwen2.5-1.5B-Instruct int8 via MediaPipe (never bundled) |
| Storage | Local JSON records + image files, on-device only |
| Async | Kotlin **Coroutines** + `StateFlow` |
| Build | Gradle KTS, min SDK 26, target SDK 36 |

**Model artefact:** `dr-v2.onnx`, **16 MB**, opset 17, exported from PyTorch.
Outputs `logits (1,5)`, `cam (1,5,15,15)`, `grade (1)`.

**Measured latency: p50 101.4 ms / 113.9 ms across two runs of 100 back-to-back
inferences** on a Snapdragon 8 Elite Gen 5, provider `NNAPI (NPU/GPU)`.

## 3.2 Training side

PyTorch + torchvision · ONNX export (opset 17) · NumPy/Pillow preprocessing ·
onnxruntime for parity checking. Export parity verified: max probability
difference between PyTorch and ONNX = **0.000109**.

## 3.3 Interpretability — the CAM, and why it is exact

Grad-CAM requires a backward pass, which **does not survive ONNX export**. But
EfficientNet-B0's head is Global Average Pooling → Linear. For that structure,
the class activation map is mathematically identical to a weighted sum of the
final feature maps using the classifier's own weights — **forward-only, and
exact**, not an approximation. That is why the model card says
`"exact_cam": true`. The 15×15 grid is upsampled with high-quality filtering
and overlaid on the *preprocessed* image (overlaying the original would
misalign it).

## 3.4 The quality gate

Before any grading, the image is scored. Thresholds:

```
overall ≥ 0.55 · blur ≥ 0.45 · lighting ≥ 0.40
framing ≥ 0.40 · visibility ≥ 0.50 · minimum 224 × 224
```

Six failure modes: `BLUR`, `LOW_LIGHT`, `OVEREXPOSED`, `POOR_FRAMING`,
`RETINA_NOT_VISIBLE`, `LOW_RESOLUTION`. If the image fails, **the app refuses
to grade it** rather than returning a confident answer on an ungradeable
photograph. This is verified working — see `16_quality_rejected.png`.

## 3.5 Confidence calibration — measured, not chosen

Binning the 546 validation images by the confidence the app displays, and
counting how often the displayed grade was actually correct:

| Confidence | n | Correct |
|---|---|---|
| < 0.55 | 42 | 50.0% |
| 0.55 – 0.90 | 157 | ~70% |
| ≥ 0.90 | 357 | **93.3%** |

**Critical subtlety worth raising before a judge finds it:** confidence is *not*
monotonic with accuracy. The band 0.90–0.95 is **97.6%** correct, but
0.95–1.00 falls to **83.9%**. The model is overconfident at the very top. This
is why the UI shows three discrete bands rather than a continuous percentage —
a continuous bar would imply a precision the model does not have.

---

# PART 4 — Business model and market

## 4.1 The market, with sources

**The disease burden in India:**

- **101 million** people in India have diabetes, projected to **125 million by
  2045** — roughly **1 in 5 adults**
  ([Indian J Ophthalmol 2024](https://journals.lww.com/ijo/fulltext/2024/07004/assessment_of_prevalence_and_need_for_screening_of.28.aspx))
- DR prevalence **12.5%**, vision-threatening DR **4%**; regionally up to
  **17.4%** any DR in Kerala
- **~34.6%** of people with diabetes will develop some DR; **10.2%**
  vision-threatening
- **~4.5% of blindness in India** is due to sight-threatening DR
- Systematic DR screening in India is described as **"in its infancy"**
  ([Lancet Global Health, SMART India](https://www.thelancet.com/journals/langlo/article/PIIS2214-109X(22)00411-9/fulltext))

**The AI screening market:**

- Global AI-driven DR screening: **US$0.48 B (2025) → US$2.22 B (2033)**,
  **21% CAGR**
  ([market.us](https://market.us/report/ai-driven-retinal-screening-device-market/),
  [OpenPR](https://www.openpr.com/news/4390173/ai-driven-diabetic-retinopathy-screening-market-to-reach-usd))
- North America holds ~40–42% share; **Asia-Pacific is the fastest-growing
  region**
- Software is **52.7%** of the market
- Typical AI screening performance in the field: **>90% sensitivity, 85–90%
  specificity** — our measured **98.2% / 92.9%** sits at or above that band,
  on internal validation

**Serviceable market, computed from the above rather than asserted:**

```
101,000,000 diabetics in India
× 1 screening per year (guideline cadence)
= 101 M screenings/year of clinical need

At ₹40 per completed AI screening
= ₹404 crore (~US$48 M) per year at full national coverage
```

That is the ceiling, not a forecast. The realistic near-term wedge is the
**~25,000 vision centres** that already own a fundus camera and have no
ophthalmologist on site.

## 4.2 The competitors, honestly assessed

| Competitor | Position | Regulatory | Weakness we exploit |
|---|---|---|---|
| **Remidio Medios AI** (Bengaluru) | Closest competitor. Offline smartphone DR AI, referable DR in ~10 s | **CDSCO approved** (India's first ophthalmic AI), CE mark, EU MDR Class II, Singapore HSA | **Locked to Remidio's own fundus camera.** Buy their hardware or you cannot use their AI |
| **LumineticsCore** (formerly IDx-DR, Digital Diagnostics) | First fully autonomous AI cleared by FDA in *any* field of medicine (2018, de novo) | FDA cleared | US-focused, expensive, requires specific camera, not built for offline rural use |
| **EyeArt** (Eyenuk) | FDA cleared | FDA cleared | Cloud-dependent workflow, US/EU pricing |
| **AEYE Diagnostic Screening**, **Retina-AI Galaxy** | FDA cleared | FDA cleared | Same profile |

Sources:
[Ophthalmology Times — Remidio CDSCO](https://www.ophthalmologytimes.com/view/remidio-receives-cdsco-approval-in-india-for-medios-dr-ai) ·
[Healio — offline DR AI](https://www.healio.com/news/ophthalmology/20241125/qa-ai-software-offers-costeffective-dr-screening-without-internet-access) ·
[Retina Today 2025](https://retinatoday.com/articles/2025-sept/the-emerging-role-of-ai-in-dr-screening)

**Be honest about where we stand:** Remidio is **CDSCO-approved and deployed**
at Aravind vision centres and in Himachal Pradesh and rural West Bengal. We are
**not cleared and not deployed**. We are a better-architected prototype, not a
better product — yet.

## 4.3 The USP — four things, each defensible

1. **Hardware-agnostic.** Any fundus adapter, any clinic camera, any Android
   phone. Medios is locked to Remidio's own camera. This is the single biggest
   commercial difference: we sell into the **installed base** of cameras that
   already exist, instead of asking a PHC to buy new hardware.
2. **Genuinely offline, and the diagnostic path never touches the network.**
   The photograph never leaves the phone. That is a privacy property, not just
   a connectivity one.
3. **11 Indian languages with voice output.** The patient hears the result in
   their own language. Competitors ship clinician-facing English tools. This is
   the difference between a report and a patient who understands what to do.
4. **Published field telemetry.** Milliseconds per image, milliwatts,
   patients-per-charge, thermal curve — measured on-device from the battery
   fuel gauge. Nobody publishes this, and it is the table a district health
   officer actually needs.

## 4.4 The pricing argument

**₹40 per completed AI screening** (~US$0.48).

Put that against the reference point that exists: in the US, **CPT code 92229**
— autonomous AI interpretation of a retinal exam without an ophthalmologist —
reimbursed at a base rate of **US$40.28 in 2023**
([Retina Today](https://retinatoday.com/articles/2025-sept/the-emerging-role-of-ai-in-dr-screening)).

**We are pricing at roughly 1/80th of the US reimbursement rate for the same
clinical act.** That is only possible because inference runs on hardware the
clinic already owns, with no per-scan cloud cost. The marginal cost of our
101-millionth screening is approximately the electricity to run it.

## 4.5 Business model

**B2B / B2G, not B2C.** The diabetic patient is the beneficiary; the health
system is the customer.

| Customer | Why they buy |
|---|---|
| Vision centres / PHC networks | Same-visit referral instead of a weeks-long grading backlog |
| Government screening programmes | Cost per screening an order of magnitude below a specialist's time |
| NGOs (Aravind, LVPEI, Sightsavers) | Works in camps with no connectivity |
| Diabetes clinics & hospitals | Retinal screening without an on-site ophthalmologist |
| Fundus camera manufacturers | White-label AI that does **not** compete with their hardware |

**Revenue:** per-completed-screening fee, with volume tiers for government
contracts. Zero marginal infrastructure cost — no GPU fleet, no per-inference
cloud bill.

**Why the model is structurally better than the incumbents':** every
cloud-based competitor pays a per-inference server cost and needs connectivity
at the point of care. Our cost per screening is bounded by the phone. That is
what makes ₹40 viable where their economics require Western reimbursement
rates.

## 4.6 Route to market

1. **Wedge:** NGO screening camps — no procurement cycle, immediate volume,
   and the offline requirement is a hard filter that eliminates most
   competitors.
2. **Evidence:** external validation on IDRiD/Messidor, then a prospective
   field study with a partner hospital. **This is the gating step for every
   later stage.**
3. **Regulatory:** CDSCO Class B/C software as a medical device. Remidio has
   shown the path exists.
4. **Scale:** state health programmes, then camera OEM white-label.

## 4.7 The honest risks — say these before a judge does

- **No regulatory clearance.** Remidio has CDSCO; we do not. That is a
  12–24 month path and it gates commercial deployment.
- **Validation is internal only.** One dataset, one set of cameras.
- **8 of 11 locales are machine-drafted** and await native-speaker review.
- **Power and thermal figures come from a ~10-second sustained-load sample**,
  not a full camp. Two runs disagreed by 40% on net power — which is itself the
  proof the window is too short.
- **We cannot capture images without third-party optics.** Our workflow depends
  on hardware we do not make.

---

## One-line answers to memorise

- **What is it?** Offline on-device DR screening and referral triage, in the
  patient's language.
- **Who uses it?** Vision-centre technicians and NGO camps with a camera but no
  ophthalmologist.
- **Why does it matter?** Same-visit referral instead of a weeks-long backlog.
- **What's the number?** QWK **0.9324**; **98.2%** referable sensitivity at the
  shipped threshold; **101 ms** per image on the NPU.
- **What's the catch?** Internal validation only, no clearance, and a phone
  camera alone cannot photograph a retina.
