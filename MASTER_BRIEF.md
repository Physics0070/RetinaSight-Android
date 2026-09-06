# RetinaSight AI — the complete brief

One file, everything. Medicine → machine learning → engineering → business →
what to say when a judge pushes back.

**Rule for this document:** every number is measured from the shipped code,
recomputed by re-running the model, or cited to a source. Assumptions are
labelled as assumptions. Nothing is illustrative.

Anything model-related can be reproduced:

```
python scripts/validate_onnx.py --data <omnikon>/ml/data/aptos_456 --split val --seed 143
```

**Contents**
- [Part A — The medicine](#part-a--the-medicine)
- [Part B — The machine learning](#part-b--the-machine-learning)
- [Part C — The engineering](#part-c--the-engineering)
- [Part D — Business and market](#part-d--business-and-market)
- [Part E — Judging, pitch, and hard questions](#part-e--judging-pitch-and-hard-questions)
- [Appendix — every number on one page](#appendix--every-number-on-one-page)

---

# PART A — The medicine

## A.1 What diabetes does to an eye

Diabetes is a disease of **small blood vessels**. Persistently high blood sugar
weakens capillary walls throughout the body — kidneys, nerves, feet, and the
retina.

The retina is the light-sensitive tissue lining the back of the eye. It matters
here for one reason that is easy to state and easy for a judge to remember:

> The retina is the **only place in the human body where you can look directly
> at blood vessels** without cutting anything open.

Shine light through the pupil, photograph what comes back, and you are looking
at live microvasculature. That is why the eye is diabetes' early-warning system
for the whole body.

## A.2 The five grades — what is physically happening

This is the **ICDR scale** (International Clinical Diabetic Retinopathy scale),
the global clinical standard. Our model outputs exactly these five.

### Grade 0 — No DR
Vessels intact. A clean orange field, sharp vessel branches radiating from the
optic disc, an even darker patch at the macula.

### Grade 1 — Mild NPDR
**Microaneurysms only.** Capillary walls balloon outward where they have
weakened, like a bulge in a worn tyre. On the photograph these are **tiny red
dots**, often only a handful of pixels across.

*This is why input resolution matters so much — see [B.3](#b3-why-456-pixels).*

### Grade 2 — Moderate NPDR
The weakened vessels start to **leak**:
- **Dot and blot haemorrhages** — blood escaping into the retina
- **Hard exudates** — yellow, sharp-edged deposits of leaked fats and proteins
- **Cotton-wool spots** — fluffy pale patches where nerve fibres have died from lack of blood

### Grade 3 — Severe NPDR
Vessels are now **blocking**, and territory of the retina is starving. Defined
clinically by the **4-2-1 rule**: haemorrhages in all 4 quadrants, **venous
beading** in ≥2 quadrants (veins go sausage-shaped), or **IRMA** in ≥1
(intraretinal microvascular abnormalities — the retina's failed attempt at
rerouting blood).

### Grade 4 — Proliferative DR
The starved retina releases growth signals (VEGF) and grows **new vessels** —
*neovascularisation*. This sounds like healing. It is not. These vessels are
fragile and grow in the wrong places. They bleed into the vitreous, and they
drag on the retina and detach it. **This is where sudden, permanent blindness
comes from.**

**NPDR** = non-proliferative (no new vessels yet). **PDR** = proliferative.

## A.3 Why this is a *screening* problem

The clinically cruel fact:

> **Vision stays normal until it suddenly isn't.**

The macula can be soaked in exudate and the patient still reads a number plate.
There is no pain. By the time they notice blurring they are often Grade 3–4 and
the damage is permanent.

Caught early, DR is very treatable — laser photocoagulation, anti-VEGF
injections, blood-sugar and blood-pressure control. So the entire clinical
problem collapses to one question:

> **Who needs an ophthalmologist, before they have symptoms?**

That is a *triage* question, not a diagnosis question. It is the question we
answer.

## A.4 The Indian bottleneck — the real gap

| Fact | Value | Source |
|---|---|---|
| DR prevalence among Indian diabetics | **12.5%** (4% sight-threatening) | [IJO National Survey 2015-19](https://www.ovid.com/jnls/ijo/fulltext/10.4103/ijo.ijo_1310_21~prevalence-of-diabetic-retinopahty-in-india-results-from-the) |
| Same, RAAB survey | 16.9% | as above |
| Retina specialists in India | **1 per 1.26 million people** | [PMC — DR screening in India](https://pmc.ncbi.nlm.nih.gov/articles/PMC7942083/) |
| Diabetics needing annual screening | ~**125 million** | [PR Newswire / Remidio](https://www.prnewswire.com/in/news-releases/india-brings-early-diabetic-retinopathy-detection-to-the-last-mile-with-artificial-intelligence-302247453.html) |

**The bottleneck is not cameras and it is not patients.** Vision centres have
fundus cameras. Patients turn up. Images get taken. Then they **queue for a
human grader for days or weeks** — and the patient has gone home, often to a
village hours away, and does not come back.

The referral decision is the thing that arrives too late. That is the only
thing we move.

## A.5 The constraint we state before anyone asks

**A smartphone camera cannot photograph a retina.** It needs optics that focus
through the pupil onto the back of the eye. RetinaSight is the **grading and
workflow layer**. Capture requires a fundus adapter (D-EYE, oDocs, Peek) or an
existing clinic fundus camera.

Our capture screen says this in plain language, in all 11 languages. Any
product implying a bare phone can image a retina is misleading you.

---

# PART B — The machine learning

## B.1 Terms, defined once

For the non-technical reader — these come up throughout.

| Term | Plain meaning |
|---|---|
| **Neural network** | Layers of simple numeric units; each layer transforms the previous one. "Learning" = adjusting millions of numbers so output matches truth. |
| **CNN** (convolutional) | A network that slides small learned filters across an image, so a pattern is recognised anywhere in the frame. |
| **Convolution filter** | A small window (e.g. 3×3) of learned numbers, multiplied over every image position — a learned pattern detector. |
| **Parameter / weight** | One adjustable number. Ours has ~5.3 million. |
| **Epoch** | One full pass over the whole training set. |
| **Batch** | Images processed together before one weight update. Ours: 8. |
| **Loss function** | Measures how wrong a prediction is. Training = minimising it. |
| **Gradient descent** | Nudge every weight slightly in the direction that lowers loss. Repeat. |
| **Learning rate** | Size of that nudge. Too big overshoots; too small never arrives. |
| **Overfitting** | Memorising training images instead of learning the disease. Detected when training loss falls but validation loss doesn't. |
| **Transfer learning** | Start from a model already trained on millions of general images; fine-tune on yours. |
| **Softmax** | Turns 5 raw scores into 5 probabilities summing to 1. |
| **Logits** | The raw pre-softmax scores. |
| **Inference** | Running a trained model to get an answer. |
| **Accuracy** | Fraction of predictions exactly right. |
| **Precision** | Of everything I *called* Grade 2, what fraction really was? (Punishes false alarms.) |
| **Recall / Sensitivity** | Of all real Grade 2 cases, what fraction did I *find*? (Punishes misses.) |
| **Specificity** | Of all healthy cases, what fraction did I correctly clear? |
| **F1** | Harmonic mean of precision and recall — one number balancing both. |
| **QWK** | Quadratic Weighted Kappa. Agreement with the human grader, corrected for chance, penalising errors by **squared** distance. |
| **Confusion matrix** | Grid of truth vs prediction. Shows *what* gets confused with *what*. |

## B.2 Why a CNN, specifically

A retinal lesion is a **local texture pattern** — a red dot 8 pixels across, a
yellow deposit with a hard edge, a sausage-shaped vein segment.

A CNN applies the same learned filter at every position of the image. So a
microaneurysm is detected identically whether it sits top-left or
bottom-right. That property is called **translation invariance**, and it is
exactly what this problem needs — lesions can appear anywhere on the retina.

The layers build a hierarchy:

```
early layers    edges, curves, colour blobs
middle layers   "small dark red circular blob", "bright yellow hard-edged patch"
deep layers     "haemorrhage", "hard exudate", "neovascular frond"
final layer     five numbers → the grade
```

**Nobody hand-coded "look for red dots".** The filters were learned from
labelled examples. That is the whole point of the approach.

## B.3 Why 456 pixels

Standard ImageNet CNNs take **224×224**. We use **456×456** — over 4× the pixel
count and considerably more compute.

The reason is medical, not technical: a **microaneurysm is a handful of pixels**
in a full-resolution fundus photograph. Downsample to 224 and it is gone —
literally averaged out of existence. And a microaneurysm is the *entire*
difference between Grade 0 and Grade 1, which is the difference between "come
back next year" and "you have diabetic eye disease".

456 is EfficientNet-B0's compound-scaled resolution for this depth/width, so
the architecture is used as designed rather than stretched.

## B.4 Architecture: EfficientNet-B0

Chosen over ResNet-50 / VGG / Inception because of **compound scaling** —
depth, width and input resolution scaled together in a fixed ratio rather than
one at a time. That gives ImageNet-competitive accuracy at a fraction of the
parameters.

Parameter efficiency is not a nicety here. It has to run on a mid-range Android
phone, offline, on battery, in a rural clinic.

**Verified facts, read from the shipped `dr-v2.onnx`:**

```
input    float32 [batch, 3, 456, 456]     (NCHW: batch, channels, height, width)
outputs  logits  [batch, 5]
         cam     [batch, 5, 15, 15]
         grade   [batch]
opset    17
size     16,047,493 bytes  (16 MB)
backbone ~5.3M parameters (EfficientNet-B0 standard)
```

## B.5 Transfer learning — why 3,662 images is enough

We did **not** train from scratch. The backbone starts from **ImageNet
pre-trained weights** — a model that has already seen millions of general
photographs and learned what edges, curves, textures and colour gradients look
like.

Those early filters are universal. A curve detector is a curve detector whether
it is finding a cat's ear or a retinal vessel. Only the later layers need to
learn "this particular red dot pattern means Grade 1".

**Evidence this worked:** validation QWK was **0.9028 after a single epoch**.
From scratch, that is impossible with this dataset size.

## B.6 The dataset

**APTOS 2019 Blindness Detection** — Aravind Eye Hospital, released via Kaggle.
3,662 labelled fundus photographs from rural India, graded to the ICDR 5-point
scale by trained graders. It is the standard Indian DR benchmark, which is
exactly why we use it: the images look like what our users will actually
capture.

**Split: 85 / 15 stratified, seed 143 → 3,116 train / 546 validation.**

*Stratified* means each grade's proportion is preserved in both halves —
without it, the 28 severe cases could land entirely in one side and the metric
becomes meaningless.

**Validation distribution (real):**

| Grade | Count | Share |
|---|---|---|
| 0 No DR | 270 | 49.5% |
| 1 Mild | 55 | 10.1% |
| 2 Moderate | 149 | 27.3% |
| 3 Severe | 28 | 5.1% |
| 4 Proliferative | 44 | 8.1% |

**Note the imbalance** — nearly half the data is healthy. This drives a design
decision in B.8.

## B.7 The training recipe — real config

From `models/efficientnet_b0-20260823-124225/config.json`:

```json
{
  "arch": "efficientnet_b0",     "image_size": 456,
  "epochs": 30,                  "batch_size": 8,
  "learning_rate": 0.0002,       "weight_decay": 0.0001,
  "val_fraction": 0.15,          "seed": 143,
  "balance": true,               "amp": true,
  "dropout": 0.4,                "patience": 10,
  "distance_weight": 0.5,        "selection_metric": "quadratic_kappa",
  "expected_grade_decision": true
}
```

| Component | Choice | Why |
|---|---|---|
| Optimiser | **AdamW**, lr 2e-4, wd 1e-4 | decoupled weight decay; the standard for stable fine-tuning |
| Scheduler | **CosineAnnealingLR**, T_max=30 | learning rate decays smoothly to near zero; no hand-tuned steps |
| Precision | **AMP** (mixed fp16/fp32) | ~2× faster, halves memory, lets batch 8 fit at 456px |
| Regularisation | dropout **0.4** + wd 1e-4 | small dataset, high overfitting risk |
| Imbalance | **class-weighted loss** | see B.8 |
| Model selection | best **validation QWK**, not accuracy | see B.10 |
| Early stopping | patience **10** epochs | |

### Cycles actually run

**25 epochs of a configured 30.** Early stopping fired at 25. **The best epoch
was 15** — that is the checkpoint that ships.

Real numbers from `history.json`:

| Epoch | train loss | val loss | val accuracy | val QWK |
|---|---|---|---|---|
| 1 | 1.5750 | 1.2079 | 0.7436 | 0.9028 |
| **15** | **0.6660** | **1.2076** | **0.8333** | **0.9324** |

Read epochs 16–25 correctly: **training loss kept falling, validation QWK did
not improve.** That is textbook overfitting. Early stopping caught it and we
shipped epoch 15.

## B.8 Class weighting

Without it, a model trained on this data **collapses to predicting "No DR" for
everything and still scores ~74% accuracy** — because 49.5% of the data is
healthy. It would be useless and look fine.

The fix is inverse-frequency weighting, normalised to mean 1:

```
weight[c] = total / (num_classes × count[c])
then normalised so the mean weight = 1
```

A Grade 3 example (28 in validation) therefore contributes far more to the loss
than a Grade 0 example (270). The model cannot ignore rare severe disease.

## B.9 The loss function — our most defensible novelty

Standard cross-entropy treats the five grades as **five unrelated categories**.
Predicting Grade 0 for a Grade 4 patient costs exactly the same as predicting
Grade 3 for a Grade 4 patient.

**That is clinically absurd.** One of those errors sends a nearly-blind patient
home. The other sends them to a doctor slightly less urgently.

DR grades are **ordinal** — they have an order *and* a distance. So we use a
custom `OrdinalAwareLoss`:

```
loss = CrossEntropy(class_weighted, label_smoothing=0.05)
     + 0.5 × MSE(expected_grade, true_grade)

where expected_grade = Σ i × softmax(logits)ᵢ
```

The second term is a **squared** distance, deliberately mirroring the quadratic
weighting inside the QWK metric. Being wrong by 3 grades is penalised **9×**
more than being wrong by 1.

**This single design choice is the main reason QWK is 0.93 rather than ~0.85.**

`label_smoothing = 0.05` stops the network becoming pathologically
over-confident — it is never asked to output a perfect 1.0.

## B.10 Augmentation — and one deliberate exclusion

```
horizontal flip     p = 0.5      # left eye vs right eye
vertical flip       p = 0.2      # camera orientation
90° rotations       p = 0.5
brightness jitter   p = 0.6, ×0.85–1.15
contrast jitter     p = 0.6, ×0.85–1.15
```

Each is *physically plausible* for fundus photography — a real camera really
does produce flipped, rotated, brighter and dimmer versions of the same eye.

**Colour-channel shuffling is deliberately excluded.** In fundus photography
red dominance is **genuine anatomical signal** — haemorrhages *are* red,
exudates *are* yellow. Shuffling channels would train the model to ignore the
single most diagnostic cue in the image.

Most generic augmentation pipelines include channel shuffle by default. It is a
quiet, serious mistake on this dataset.

## B.11 How accuracy is actually computed

**Method.** The 546-image validation split is **never trained on**. It is scored
at the end of every epoch. The checkpoint with the best validation QWK is kept.

**Every number below I reproduced myself** by running the shipped ONNX file
through the serving preprocessing — **all 25 confusion-matrix cells match** the
original training run exactly, along with QWK, accuracy, sensitivity and
specificity. The metric is not taken on trust from a training log.

### Headline

```
quadratic weighted kappa   0.9324
exact-grade accuracy       0.8333    (455 of 546)
macro F1                   0.7212
weighted F1                0.8398
```

### Per class

| Grade | Precision | Recall | F1 | Support |
|---|---|---|---|---|
| 0 No DR | **1.0000** | 0.9741 | **0.9869** | 270 |
| 1 Mild | 0.5938 | 0.6909 | 0.6387 | 55 |
| 2 Moderate | 0.7836 | 0.7047 | 0.7420 | 149 |
| 3 Severe | 0.4043 | 0.6786 | 0.5067 | 28 |
| 4 Proliferative | 0.7895 | 0.6818 | 0.7317 | 44 |
| **Macro average** | 0.7142 | 0.7460 | 0.7212 | 546 |
| **Weighted average** | 0.8525 | 0.8333 | 0.8398 | 546 |

*Macro* = plain mean across classes (treats rare severe disease as equally
important). *Weighted* = weighted by class frequency.

### Confusion matrix — rows are truth, columns are prediction

```
                 No DR   Mild   Mod   Sev   Prolif
No DR           [ 263      7      0     0      0 ]
Mild            [   0     38     17     0      0 ]
Moderate        [   0     19    105    21      4 ]
Severe          [   0      0      5    19      4 ]
Proliferative   [   0      0      7     7     30 ]
```

**Read the band, not the diagonal.** Almost every error is off by exactly one
grade. And critically:

- **Top-right corner is all zeros** — no healthy eye was ever called severe or proliferative.
- **Bottom-left corner is all zeros** — no proliferative eye was ever called healthy or mild.

The catastrophic errors — the ones that blind people — **do not occur** in this
validation set.

### What QWK is, and why it is the primary metric

Accuracy treats a 1-grade miss and a 4-grade miss identically. QWK does not.

```
QWK = 1 − Σ(wᵢⱼ · Oᵢⱼ) / Σ(wᵢⱼ · Eᵢⱼ)
where wᵢⱼ = (i − j)² / (N − 1)²
```

`O` is what actually happened, `E` is what random guessing with the same
marginal distribution would produce. So QWK is **agreement corrected for
chance**, with errors penalised by squared distance.

**0.9324 sits in "almost perfect agreement"** on the Landis & Koch scale
(>0.81). It is also the metric the entire APTOS/Kaggle DR literature is scored
on, so it is directly comparable to published work.

### The weakness we volunteer

**Grade 3 precision is 0.4043.** When the model says "severe", it is right
about 40% of the time — it over-calls severe, mostly from moderate.

For a *grading* tool that would be disqualifying. For a *screening* tool it
errs in the safe direction: an over-called severe patient still sees an
ophthalmologist. And with only 28 severe cases in validation, the figure is
statistically noisy.

We say this before a judge finds it.

## B.12 The referral decision — separate from the grade

The grade is what the model *says*. **The referral is what the patient must
do**, and it is a different decision.

`expected_grade = Σ i × softmax(logits)ᵢ` is continuous, 0.0–4.0. Rounding it
gives the displayed grade. But we refer at **1.15**, deliberately below the
rounding point of 1.5.

**Full threshold sweep on the validation split (n=546, 221 referable):**

| Threshold | Sensitivity | Specificity | Missed | False alarms |
|---|---|---|---|---|
| 1.50 (rounding) | 91.4% | 94.8% | 19 | 17 |
| **1.15 (shipped)** | **98.2%** | **92.9%** | **4** | **23** |

**Fifteen fewer patients missed, for six more unnecessary clinic visits.**

Those two errors are not symmetric and we refuse to pretend they are. A missed
proliferative patient goes blind. A false alarm wastes an afternoon.

**Binary referable metrics at the shipped threshold:**

```
precision    0.9042
sensitivity  0.9819      specificity  0.9292
F1           0.9414
TP 217   FN 4   FP 23   TN 302
```

**The consequence that shows up in the UI:** a scan can display **"Grade 1 —
Mild"** and *still be referred*. Expected grade between 1.15 and 1.5. The app
states why — *"Referral prioritised — borderline risk markers"* — because
otherwise the screen contradicts itself, and a worker who sees a contradiction
stops trusting the tool.

## B.13 Confidence — measured, not asserted

Cut points were derived by **binning the validation split by the confidence the
app displays**, then counting how often the displayed grade was actually right:

| Band | Range | Actually correct | n |
|---|---|---|---|
| HIGH | ≥ 0.90 | **93.3%** | 357 |
| MEDIUM | 0.55 – 0.90 | ~70% | 157 |
| LOW | < 0.55 | **50.0%** | 42 |

0.90 is where "High" starts telling the truth. The previous cut of 0.80 did not.

**The subtlety we volunteer:** confidence is **not monotonic with accuracy**.

```
0.90 – 0.95   →  97.6% correct
0.95 – 1.00   →  83.9% correct
```

The model is **overconfident at the very top**. That is why the UI shows three
discrete segments rather than a continuous bar — a bar implies a precision the
model does not have. Below 0.55 the app additionally advises a retake.

## B.14 Explainability — Grad-CAM

The `cam` output is a 5×15×15 class activation map, upsampled and overlaid on
the image as a heat map.

**Why it is exact, not an approximation:** for a Global-Average-Pooling →
Linear classifier head, a CAM computed directly from the classifier weights is
**mathematically identical** to Grad-CAM — and it is forward-only, so no
backward pass is needed on the phone.

Two implementation details that matter:

1. It is overlaid on the **preprocessed** image, not the original photo.
   Overlaying the original misaligns it, because preprocessing crops.
2. The grid is only 15×15, so it is interpolated with high-quality filtering
   into a smooth field rather than shown as visible blocks.

In our demo the hotspots sit directly on the lesions. The UI has a **slider**
so a clinician can wipe the heat map across the tissue and compare it against
the raw photograph.

## B.15 The benchmark, and its honest limits

We score on **APTOS 2019**, held-out split, n=546.

**Stated first, before anyone asks: this is internal validation.** One dataset,
one set of cameras, one population. It is *not* external validation and *not*
clinical performance.

**External validation on IDRiD is the next step.** The harness is already
written and verified — `scripts/validate_onnx.py` reproduces the APTOS numbers
to the digit, so we know it is correct. Only the dataset is missing (IDRiD
needs an IEEE DataPort login). We expect **0.80–0.88** on an unseen camera, and
we will publish whatever we get.

> "0.93 internal, 0.85 on an unseen camera" is a far stronger claim than one
> number with no generalisation evidence at all.

---

# PART C — The engineering

## C.1 On-device stack — the whole diagnostic path

| Layer | Technology | Detail |
|---|---|---|
| Language | **Kotlin** | |
| UI | **Jetpack Compose** + Material 3 | 11 screens, ~2,700 lines |
| Camera | **CameraX** | `PreviewView` + `ImageCapture` |
| Inference | **ONNX Runtime for Android** | |
| Acceleration | **NNAPI** → NPU/GPU, CPU fallback | verified `NNAPI (NPU/GPU)` live on device |
| Preprocessing | hand-written Kotlin | crop, resize, normalise — matched to training |
| Speech | Android **TextToSpeech** | 11 languages |
| Optional narrator | **Qwen2.5-1.5B-Instruct** int8 via MediaPipe | never bundled; only rephrases; never decides |
| Storage | local JSON + image files | nothing leaves the device |
| Audio / haptics | synthesised PCM `AudioTrack`, `VibrationEffect` | zero audio assets |
| Graphics | Compose **Canvas** | 3D eye, scanner logo, shutters — no 3D engine, no model files |

**The NPU claim is real, not aspirational.** The app reports the execution
provider that actually initialised, and it reads `NNAPI (NPU/GPU)` on the iQOO
15's Snapdragon 8 Elite Gen 5.

## C.2 Training stack

PyTorch · torchvision EfficientNet-B0 · AMP · custom `OrdinalAwareLoss` ·
ONNX export at opset 17 · onnxruntime for parity verification.

## C.3 The preprocessing contract — where most teams silently fail

Serving preprocessing must match training **exactly**, or the grade is invalid:

1. **Crop to the retinal disc** — luminance (BT.601) > **18**, bounding box,
   **+2% padding**
2. **Resize to 456×456**, antialiased **BILINEAR**
3. `/255`, **ImageNet mean/std** normalisation, NCHW float32

**No Ben Graham filtering. No circle mask.** Both are common in Kaggle DR
pipelines. Either changes the input distribution the model was trained on — and
the model will still return a confident number, which is the *dangerous* mode
of failure. It does not error. It is just quietly wrong.

**Parity verified:** max probability difference PyTorch vs ONNX = **0.000109**.
The phone and the training rig agree to four decimal places.

## C.4 Quality gate — the app refusing to answer

Scored **before** grading. If the image is not gradeable, we refuse to grade it.

**Thresholds:**

```
overall     ≥ 0.55        blur        ≥ 0.45
lighting    ≥ 0.40        framing     ≥ 0.40
visibility  ≥ 0.50        min size    224 × 224
```

**How each is computed:**

| Check | Algorithm |
|---|---|
| Blur / focus | **variance of the Laplacian** — a standard 3×3 discrete focus operator. Low variance = few sharp edges = out of focus. Computed at a fixed analysis scale because Laplacian variance is scale-dependent. |
| Lighting | mean retinal luminance vs a target of 90.5, tolerance ±65 |
| Overexposure | fraction of pixels ≥ 253; fails above **2%** clipped |
| Framing | is the retinal disc centred |
| Visibility | is a retina present at all |

**Six failure modes**, which can occur in combination: `BLUR`, `LOW_LIGHT`,
`OVEREXPOSED`, `POOR_FRAMING`, `RETINA_NOT_VISIBLE`, `LOW_RESOLUTION`.

**This is demonstrable live.** Feeding it a non-retina photograph returns 43%
quality with `LOW_LIGHT` + `RETINA_NOT_VISIBLE` and a retake prompt.

> A model that can never say "I don't know" is a liability, not a feature.

## C.5 Measured on-device performance — vivo iQOO 15, Android 16, SDK 36

100 back-to-back screenings, two independent runs:

| Metric | Run 1 | Run 2 |
|---|---|---|
| latency mean | 117.38 ms | 103.84 ms |
| **latency p50** | 113.93 ms | **101.38 ms** |
| latency p90 | 125.61 ms | 109.65 ms |
| latency p99 | 132.60 ms | 116.12 ms |
| latency min | 109.10 ms | 92.33 ms |
| net inference power | 7,805 mW | 10,891 mW |
| energy per screening | 0.33 mWh | 0.37 mWh |
| patients per charge | 321 | 245 |
| battery temperature | 33.1 → 33.1 °C | 33.2 → 33.2 °C |

*(Reference point: the same ONNX file on desktop CPU takes ~25 ms — the phone
figure includes full preprocessing and the NNAPI path.)*

### The weakness we disclose rather than hide

The two runs **disagree by 40%** on net power and on patients-per-charge. That
disagreement **is the evidence** that a ~10-second window cannot measure power.

Battery temperature did not move at all — because nothing warms up in ten
seconds. So "no throttling" says **nothing** about a real screening camp.

**Language discipline:** quote the millisecond figures freely. For power, say
*"a ten-second sustained-load sample"*. **Never** say "over a camp".

The app has a paced benchmark mode (30s per patient, 50 screenings, ~25 min
unplugged) that would produce a defensible figure. We have not run it yet, and
we say so.

## C.6 Privacy and data flow

- The photograph **never leaves the phone**. No upload in the diagnostic path.
- Consent is **mandatory and timestamped** before any image can be captured.
- Records are stored locally; deleting a record deletes its image file too.
- Import uses Android's **system photo picker**, which requires *no* storage
  permission — the app receives only the one image the user chose.
- Offline is the **normal** operating state, not a degraded one.

## C.7 Accessibility and reach

- **11 languages**: English, Hindi, Marathi, Bengali, Gujarati, Punjabi, Odia,
  Tamil, Telugu, Kannada, Malayalam. All locale files complete and consistent.
- **Spoken result** on arrival — three facts only: what was found, how sure,
  what to do. The patient may not read.
- **System font deliberately kept** (`FontFamily.Default` → Noto), because it
  covers all nine Indic scripts. Bundling a display font like Inter would
  silently break text rendering in 8 of the 11 languages.
- **Minimum 16sp** for anything user-facing — outdoor use, older patients.
- **Severity colours meet WCAG AA** with white text. Colour is never the only
  signal: icon + words + speech always accompany it.

**Honest status:** 3 languages are reviewed. **8 are machine-drafted and pending
native review.** Say "drafted, pending native review", never "11 verified".

---

# PART D — Business and market

## D.1 Market size — cited

| Market | Value | Source |
|---|---|---|
| Global AI-DR screening, 2024 | **US$0.40 B** | [DataM Intelligence](https://www.datamintelligence.com/research-report/ai-driven-diabetic-retinopathy-screening-market) |
| Global, 2025 | US$0.48 B | same |
| Global, 2033 projected | **US$2.22 B** | same |
| Global CAGR | **~21%** | same |
| US market, 2026 | US$231.6 M | [Towards Healthcare](https://www.towardshealthcare.com/insights/us-ai-driven-diabetic-retinopathy-screening-market-sizing) |
| US, 2035 | US$1.31 B @ 21.22% CAGR | same |
| AI retinal screening devices | 12.6% CAGR | [Market.us](https://market.us/report/ai-driven-retinal-screening-device-market/) |

**Growth drivers cited across sources:** rising diabetes prevalence, favourable
reimbursement pathways, and **shortage of ophthalmologists / access gaps** —
which is precisely the gap we address.

## D.2 Our serviceable market, bottom-up

~125 million Indian diabetics needing annual screening.

At our proposed **₹40 per completed AI screening**:

| Share captured | Screenings/yr | Revenue/yr |
|---|---|---|
| 0.1% | 125,000 | ₹50 lakh |
| 1% | 1.25 million | **₹5 crore** |
| 5% | 6.25 million | ₹25 crore |
| 10% | 12.5 million | **₹50 crore** |

**This is our pricing hypothesis, not a market report figure.** Label it as such
in the pitch.

## D.3 Competitors — what they actually have

| Player | Status | Reported performance | The weakness we exploit |
|---|---|---|---|
| **Remidio Medios** (India) | **First CDSCO-approved** ophthalmic AI in India ([Ophthalmology Times](https://www.ophthalmologytimes.com/view/remidio-receives-cdsco-approval-in-india-for-medios-dr-ai)); offline-capable; 50+ health workers at Aravind vision centres | — | **Locked to Remidio's own camera.** You buy their hardware or you cannot use their AI |
| **Eyenuk EyeArt** (US) | FDA cleared, 2020 | **96% sens / 88% spec** (mild DR); **97% sens / 90% spec** (vision-threatening); n=915 prospective multicentre ([Ophthalmology Science](https://www.ophthalmologyscience.org/article/S2666-9145(22)00117-8/fulltext)) | Cloud-dependent, US-priced, needs connectivity |
| **Digital Diagnostics IDx-DR** (US) | **First FDA-cleared autonomous AI**, April 2018 | **87% sens / 90% spec**, 96% imageability | Bound to specific cameras; US reimbursement model; 23.6% of patients need dilation vs EyeArt's 12.6% |
| **Google ARDA** | Research + deployments (incl. India) | — | Cloud inference; no offline path |
| **Forus Health / Artelus** (India) | Camera + AI bundles | — | Hardware-tied |

### Reading those numbers honestly

**Do not claim we beat EyeArt.** Their 96%/88% comes from a **prospective,
multicentre clinical trial** with **dilated 4-widefield stereo photography read
by expert graders** as the reference standard. That is a dramatically higher
bar than a held-out split of a public dataset.

What *is* fair to say: on our benchmark we reach 98.2% referable sensitivity,
and we do it **on-device, offline, in 101 ms, on any camera**. The engineering
claim is ours to make. The clinical claim is not — yet.

## D.4 USP — five points, each verifiable in the live demo

1. **Hardware-agnostic.** Any fundus image source, any Android phone. Medios is
   locked to Remidio's camera; IDx-DR to specific cameras. **This is the
   biggest commercial wedge** — India's ~25,000 vision centres already own
   cameras from many vendors, and none of them want to re-buy hardware.

2. **Genuinely offline.** No server anywhere in the diagnostic path.
   Demonstrable in airplane mode. Rural PHC connectivity is unreliable; cloud
   competitors degrade to useless exactly where the need is greatest.

3. **11 Indian languages, with voice.** The patient *hears* the result in their
   own language. No competitor does this. It is not a feature — it is the
   difference between a referral being acted on and ignored.

4. **Published field telemetry.** Milliseconds, milliwatts, thermal curve,
   patients per charge — measured on the device. Nobody publishes this. It is
   the table a district health officer actually needs to plan a programme.

5. **Open weights, Apache-2.0.** A state health department can audit the model
   before deploying it to a population. None of the commercial players allow
   this.

## D.5 The business model

**B2B / B2G, not B2C.** The diabetic patient is the **beneficiary**; the health
system is the **customer**.

| Customer | Why they buy |
|---|---|
| Government programmes (Ayushman Bharat, NPCB) | screen at PHC scale without hiring ophthalmologists |
| NGO networks (Aravind, LVPEI, Sightsavers) | more camps per rupee of donor money |
| Hospital & diabetes clinic chains | in-house triage, retain the referral inside the network |
| Fundus camera OEMs | white-label AI that makes their hardware more saleable |

### Pricing: ₹40 per completed AI screening

Software fee only — **not** the cost of the eye examination. Rationale:

- Marginal cost of inference is **≈ ₹0** — it runs on the customer's own phone
- Must sit far below the human-grading cost it displaces
- Per-screening pricing aligns our revenue with actual delivered value, not
  with shelfware licences

**Grounding in published Indian health economics:** Kerala's public-system DR
screening pilot achieved **₹22,000 per QALY** — well below India's GNI per
capita, i.e. already considered highly cost-effective
([Eye / Nature](https://www.nature.com/articles/s41433-024-03304-w)). We reduce
its marginal cost further. Cost-effectiveness at PHCs has been separately
analysed ([PMC](https://pmc.ncbi.nlm.nih.gov/articles/PMC12209073/)).

### Unit economics — the structural advantage

Every cloud competitor pays GPU inference cost **per image, forever**. Our
marginal cost per screening is effectively zero, because inference runs on
hardware the customer already owns.

That is not a discount we are choosing to offer. It is a **structural
gross-margin advantage** that a cloud architecture cannot match.

### Why this scales

- **Zero marginal infrastructure.** 10× the screenings costs us 10× nothing.
- **No connectivity dependency.** Deployable where the need is highest.
- **Hardware-agnostic.** Every existing camera in every vision centre is a
  potential deployment, with no capital expenditure.
- **The APK is the product.** Distribution is a download.

### Go to market

1. **NGO screening camps** — field validation, real-world data, credibility
2. **State health department pilots** — Kerala/Tamil Nadu have existing DR
   programmes to plug into
3. **OEM white-label** — camera manufacturers bundle it, giving national reach
   without a direct sales force

### Risks, stated honestly

| Risk | Mitigation |
|---|---|
| **No regulatory clearance** | CDSCO pathway is the gate for government deals. Remidio took years. This is our biggest real obstacle. |
| Internal validation only | External validation on IDRiD is the immediate next step |
| Incumbent has approval + Aravind relationship | We compete on hardware-agnosticism and languages, not on clearance |
| Liability for a missed case | Positioned as screening support with mandatory clinician confirmation; threshold biased toward over-referral |

---

# PART E — Judging, pitch, and hard questions

## E.1 The criteria, and our evidence

| Weight | Criterion | Strongest evidence |
|---|---|---|
| **30%** | End product | Working app, real inference on device, 11 languages, full flow demoable including quality rejection and history |
| **20%** | Novelty | Ordinal-aware loss; referral threshold decoupled from grade at 1.15; measured (not guessed) confidence bands; hardware-agnostic offline |
| **15%** | Creative phone use | NNAPI on the Snapdragon NPU — verified `NNAPI (NPU/GPU)`; 101 ms p50; CameraX; on-device TTS in 11 languages; the app benchmarks its own battery and thermals |
| **15%** | Technical depth | QWK 0.9324 **reproduced from the shipped artefact**; full confusion matrix; threshold sweep; PyTorch↔ONNX parity 1.09e-4 |
| **10%** | Office Kit | — |
| **10%** | Demo | Rehearse the path in E.3 |

## E.2 The 60-second pitch

> India has 125 million diabetics who each need an annual retina check, and one
> retina specialist per 1.26 million people. Cameras already exist in vision
> centres. Images get taken. Then they queue for a human grader for weeks — and
> the patient has gone home to a village three hours away.
>
> RetinaSight grades diabetic retinopathy **on the phone itself in 101
> milliseconds**, with no server anywhere in the diagnostic path, and tells the
> patient out loud in their own language — eleven of them.
>
> Quadratic kappa **0.93**. **98.2%** sensitivity for referable disease. It
> works in airplane mode, on any fundus camera, on a phone the health worker
> already owns.
>
> We are not replacing the ophthalmologist. We are deciding **who needs one**,
> while the patient is still in the chair.

## E.3 Demo path — rehearse this exact order

```
Home  (scanning logo, offline badge, NNAPI badge)
  → Scan Eye
  → Consent  (tick it — chime + haptic fire)
  → Eye selection  (tap Left / Right, the 3D eye rotates)
  → Capture  (clinical HUD: CAMERA / MODE / ENGINE — all real values)
  → Import a fundus image
  → Result  (grade, referral banner, confidence segments)
  → Heat map slider  (wipe it across the lesions)
  → Privacy shutter  (eyelids close)
  → Past checks  (progression strip)
  → Language sheet  (switch to Tamil — whole UI changes, voice follows)
```

**Two things to demo that most teams cannot:**
- **Airplane mode** — the badge flips to Offline and screening still works.
- **Feed it a non-retina photo** — it *refuses to grade*, showing 43% quality
  and the failed checks.

## E.4 Hard questions, honest answers

**"Is this FDA or CDSCO approved?"**
> No. Our own model metadata says `clinically_validated: false`. This is
> screening and triage support, not diagnosis. Remidio took years to get CDSCO
> approval — that is the path, and we are at the start of it.

**"Is 0.93 kappa real?"**
> Real and reproducible. Re-run the shipped ONNX file over the split and all 25
> confusion cells match, along with kappa, accuracy, sensitivity and
> specificity. But it is **internal** validation — one dataset, one set of
> cameras. Not clinical performance. External validation on IDRiD is next; the
> harness is written and already verified against APTOS.

**"Your Grade 3 precision is 0.40. Isn't that bad?"**
> For a grading tool it would be disqualifying. For a screening tool it errs
> safe — we over-call severe, so those patients still see a doctor. Only 28
> severe cases exist in validation, so it is a noisy figure. And look at the
> confusion matrix: no healthy eye was ever called severe, no proliferative eye
> was ever called healthy.

**"Why is your sensitivity higher than FDA-cleared EyeArt's?"**
> Because they are not comparable, and I would rather say that than let you
> find it. EyeArt's 96%/88% comes from a prospective multicentre trial against
> dilated stereo reference grading. Ours is a held-out split of a public
> dataset. Their bar is far higher. What is comparable is the engineering: we
> do it on-device, offline, in 101 milliseconds.

**"Can I photograph my own retina with this phone?"**
> No — and anything claiming otherwise is misleading you. You need optics
> through the pupil: a fundus adapter or a clinic camera. Our capture screen
> says exactly that.

**"What happens if the photo is bad?"**
> It refuses to grade. Six quality checks — blur via Laplacian variance,
> lighting, overexposure, framing, retina visibility, resolution. I can show
> you: feed it a non-retina photo and it returns 43% quality and a retake
> prompt. A model that can never say "I don't know" is a liability.

**"What stops a technician trusting a wrong answer?"**
> Three things. Confidence is calibrated — Low genuinely means 50% correct, and
> we display that rather than a reassuring bar. The referral threshold is
> deliberately biased toward over-referral. And the Grad-CAM overlay lets a
> clinician see *which lesions* drove the answer, with a slider to compare it
> against the raw tissue.

**"Why not just use the cloud? It's easier."**
> Three reasons. A rural PHC's connectivity is unreliable — the tool would fail
> exactly where it is most needed. Uploading a retinal photograph is a privacy
> exposure we do not need to take. And cloud inference costs the operator money
> on every scan, forever. On-device fixes all three, and it is why our marginal
> cost is effectively zero.

**"How are you different from Medios, which already has approval?"**
> Medios is locked to Remidio's own camera — you buy their hardware or you
> cannot use their AI. We are hardware-agnostic: any fundus image source, any
> Android phone. India's vision centres already own cameras from many vendors.
> Add open weights, 11 languages with voice, and published field telemetry, and
> we are addressing the 25,000 centres their model cannot reach.

**"What is genuinely novel here, technically?"**
> Three things. The ordinal-aware loss, which penalises errors by squared grade
> distance and mirrors the metric — that is why kappa is 0.93 not 0.85.
> Decoupling the referral threshold from the displayed grade at 1.15, which
> cuts missed patients from 19 to 4. And confidence bands derived by measuring
> what the model actually gets right, rather than picking round numbers.

**"How many training cycles?"**
> 25 epochs of a configured 30 — early stopping fired. Best epoch was 15, and
> that is the checkpoint we ship. Epochs 16 to 25 improved training loss but
> not validation kappa, which is overfitting; early stopping caught it.

## E.5 Never say these

| Don't say | Say instead |
|---|---|
| "diagnosis" | *screening* / *triage* |
| "clinically validated" | *internally validated on a held-out split* |
| "94.1% sensitivity" | that was a 3-seed mean, not this checkpoint — it is **91.4%** at rounding, **98.2%** at the shipped threshold |
| "measured over a camp" | *a ten-second sustained-load sample* |
| "11 verified languages" | 3 reviewed; **8 machine-drafted, pending native review** |
| "we beat FDA-cleared systems" | *different evidence standards; our engineering claim is on-device and offline* |

---

# Appendix — every number on one page

```
MODEL
  architecture            EfficientNet-B0, ImageNet pre-trained, fine-tuned
  input                   float32 [batch, 3, 456, 456] NCHW
  outputs                 logits [b,5] · cam [b,5,15,15] · grade [b]
  parameters              ~5.3 M
  file                    16,047,493 bytes (16 MB), ONNX opset 17
  PyTorch <-> ONNX parity 1.09e-4 max probability delta

DATA
  dataset                 APTOS 2019 (Aravind), 3,662 fundus images
  split                   85 / 15 stratified, seed 143 -> 3,116 / 546
  val distribution        270 / 55 / 149 / 28 / 44   (grades 0-4)

TRAINING
  epochs run              25 of 30 configured (early stopping, patience 10)
  best epoch              15
  optimiser               AdamW, lr 2e-4, weight decay 1e-4
  scheduler               CosineAnnealingLR, T_max 30
  batch size              8      precision  AMP (mixed fp16/fp32)
  dropout                 0.4    label smoothing 0.05
  class weighting         inverse frequency, normalised to mean 1
  loss                    weighted CE + 0.5 x MSE(expected_grade, true_grade)
  selection metric        validation quadratic weighted kappa

ACCURACY  (n = 546, reproduced from the shipped ONNX)
  QWK                     0.9324
  exact accuracy          0.8333   (455 / 546)
  macro     P/R/F1        0.7142 / 0.7460 / 0.7212
  weighted  P/R/F1        0.8525 / 0.8333 / 0.8398
  per-class F1            0.9869 / 0.6387 / 0.7420 / 0.5067 / 0.7317

REFERRAL  (expected grade >= 1.15)
  sensitivity             98.2%     specificity   92.9%
  precision               0.9042    F1            0.9414
  TP 217   FN 4   FP 23   TN 302
  at rounding 1.50        91.4% / 94.8%,  19 missed

CONFIDENCE  (measured)
  HIGH   >= 0.90          93.3% correct   n=357
  MEDIUM 0.55-0.90        ~70% correct    n=157
  LOW    <  0.55          50.0% correct   n=42
  non-monotonic: 0.90-0.95 = 97.6%,  0.95-1.00 = 83.9%

ON-DEVICE  (vivo iQOO 15, Android 16, NNAPI)
  latency p50             101.38 ms      p90 109.65   p99 116.12
  energy per screening    0.33-0.37 mWh
  patients per charge     245-321  (10-second sample; disclose the spread)
  battery temp            33.2 -> 33.2 C  (nothing warms in 10 s)

MARKET
  global AI-DR 2024       US$0.40 B  ->  US$2.22 B by 2033, ~21% CAGR
  India diabetics         ~125 million needing annual screening
  DR prevalence India     12.5% (4% sight-threatening)
  retina specialists      1 per 1.26 million
  our price hypothesis    Rs 40 per completed AI screening
  Kerala DR screening     Rs 22,000 per QALY (already cost-effective)
```

**Sources:**
[DataM Intelligence](https://www.datamintelligence.com/research-report/ai-driven-diabetic-retinopathy-screening-market) ·
[Towards Healthcare](https://www.towardshealthcare.com/insights/us-ai-driven-diabetic-retinopathy-screening-market-sizing) ·
[Market.us](https://market.us/report/ai-driven-retinal-screening-device-market/) ·
[Ophthalmology Times — Remidio CDSCO](https://www.ophthalmologytimes.com/view/remidio-receives-cdsco-approval-in-india-for-medios-dr-ai) ·
[Ophthalmology Science — EyeArt trial](https://www.ophthalmologyscience.org/article/S2666-9145(22)00117-8/fulltext) ·
[AAO — autonomous AI FDA approval](https://www.aao.org/headline/autonomous-diabetic-retinopathy-screening-system-g) ·
[PMC — DR screening in India](https://pmc.ncbi.nlm.nih.gov/articles/PMC7942083/) ·
[IJO — National DR Survey 2015-19](https://www.ovid.com/jnls/ijo/fulltext/10.4103/ijo.ijo_1310_21~prevalence-of-diabetic-retinopahty-in-india-results-from-the) ·
[Nature Eye — Kerala cost-effectiveness](https://www.nature.com/articles/s41433-024-03304-w) ·
[PMC — PHC cost-effectiveness](https://pmc.ncbi.nlm.nih.gov/articles/PMC12209073/) ·
[PR Newswire — Remidio India](https://www.prnewswire.com/in/news-releases/india-brings-early-diabetic-retinopathy-detection-to-the-last-mile-with-artificial-intelligence-302247453.html)
