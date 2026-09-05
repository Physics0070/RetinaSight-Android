# RetinaSight AI — The Tech Stack, Explained

Written to be **spoken**, not read off a slide. The spine is: follow one
photograph through the system. Every layer shows up naturally, and a judge can
interrupt at any point and you'll still be somewhere sensible.

---

# THE ONE-SENTENCE VERSION

> "It's a native Android app that runs two open-source AI models entirely on the
> phone — a vision model that grades the retina and a language model that
> explains it — with no server anywhere in the diagnostic path."

If they only hear one sentence, that's the one.

---

# FOLLOW ONE PHOTOGRAPH

## Step 0 — Before any photo: consent

Consent is captured first and **enforced in code** — the Continue button is
disabled until it's given. Not a policy on paper; a state the app cannot leave.

> Stack: Jetpack Compose UI, state hoisted into a ViewModel.

## Step 1 — The image arrives

Two ways in: the camera (**CameraX 1.3.4**) or the system photo picker.

We use Android's modern **PickVisualMedia** picker, which needs *no storage
permission at all* — the OS hands us exactly the one image the user chose, and
nothing else.

> Say this: *"The app can read one photo you point at. It has no permission to
> browse your gallery."*

## Step 2 — Quality gate (before the model ever runs)

This is the step most people skip, and it's the one that matters most clinically.

**The model always returns a grade. It has no way to say "this isn't a retina."**
Show it a blurry photo of a table and it will confidently call it grade 2.

So before inference, four measurements from the pixels themselves:

| Measure | How |
|---|---|
| Sharpness | variance of the Laplacian — a standard focus operator |
| Lighting | mean brightness of the retinal disc + how much is blown out white |
| Framing | how much of the frame the disc fills, and how off-centre it is |
| Is-it-a-retina | red-channel dominance — fundus images are red-dominant |

Fail → a **retake screen naming the specific problem**: *"hold steady and let it
focus"*, *"move to a darker room so the pupil widens"*. Something a worker can
fix in ten seconds while the patient is still there.

> The thresholds aren't invented — they're calibrated against 250 real APTOS
> photographs, ported from the same Python that the backend uses.

## Step 3 — Preprocessing (the invisible failure mode)

The image is cropped to the retinal disc and resized to 456×456, then normalised
with ImageNet statistics.

**Why this is the riskiest code in the app:** the model was trained on images
prepared a specific way in Python. If the phone prepares them even slightly
differently, the model still runs, still returns a confident grade — and is
quietly worse. Nothing crashes. Nothing looks wrong.

So the Kotlin is a deliberate arithmetic-for-arithmetic port:

- Crop by **luminance > 18** (ITU-R BT.601), padded 2%
- Resize with **Pillow's antialiased bilinear** — reimplemented by hand, because
  Android's `Bitmap.createScaledBitmap` is naive bilinear and produces different
  pixels

> Say this: *"We reimplemented Pillow's resampling filter in Kotlin rather than
> use Android's built-in scaler, because the built-in one would have silently
> shifted the input distribution the model was trained on."*

That line alone tells a technical judge you know what you're doing.

## Step 4 — Inference

**ONNX Runtime 1.20.0** with the **NNAPI execution provider**, which routes to
the Snapdragon Hexagon NPU / GPU where the driver supports the ops.

The model: **EfficientNet-B0 at 456 px**, trained with an ordinal objective,
exported to ONNX — **16 MB**, used exactly as exported. No conversion, no
re-quantisation, so the numbers on the phone are the numbers that were validated.

It emits **three outputs from one forward pass**:

```
logits (1, 5)          raw scores for the 5 DR grades
cam    (1, 5, 15, 15)  one heat map per grade
grade  (1,)            the decision, already computed in-graph
```

Two things worth saying about that:

**The grade is computed inside the graph.** This model decides by *rounding the
expected grade* — a weighted average across the five classes — not by taking the
highest score. Doing it in the graph means the phone can't accidentally disagree
with the validated result.

**The heat map needed a real design decision.** Grad-CAM requires
backpropagation. **ONNX and TFLite cannot backpropagate.** So the textbook
approach doesn't survive export. But EfficientNet-B0 ends in
`features → global average pool → linear classifier`, and for exactly that shape,
Class Activation Mapping is *mathematically identical* to Grad-CAM and needs only
a forward pass — the classifier weights multiplied by the feature map.

> Say this: *"Grad-CAM needs backprop and TFLite can't backprop. For a
> GAP-plus-linear head, CAM is the same thing forward-only — so we compute it in
> the same pass, 245 floats, essentially free."*

Execution provider affects **speed only** — never the shape or the decision.
A phone without NNAPI produces identical results, slower.

## Step 5 — Explanation

Two layers, deliberately separated:

**The authoritative advice is a translated template**, chosen by the model's real
grade and confidence. It can be *wrong about the grade* — but it can never
*invent a treatment*, because every sentence is fixed and human-written.

**On top of that, an optional on-device LLM restates it** — Qwen2.5-1.5B-Instruct,
Apache-2.0, 1.6 GB, via **MediaPipe Tasks GenAI**. It is never asked what's wrong
with the patient; it's handed the grade, confidence and urgency as fixed facts
and asked to say them in plainer words.

> Say this: *"A 1.5-billion-parameter model asked to explain a diagnosis will
> eventually invent a symptom. Asked to rephrase four given facts, it has very
> little room to. That containment is the design."*

And we validate its output — it currently produces byte-level-BPE mojibake in
Devanagari, so we detect and discard that rather than show a patient garbage.

## Step 6 — Speaking it

Android TextToSpeech speaks a **purpose-written summary** — not the screen read
aloud. Three facts only: what was found, how sure, what to do. Headings and
button labels are screen furniture; narrating them wastes the only channel a
person who can't read actually has.

## Step 7 — Saving and (optionally) sharing

Saved locally as JSON + JPEG. Then:

- **The eye photo never leaves the phone.** Only the structured result syncs.
- Sync is a **separate, optional path** — connect a clinic and finished records
  upload when connectivity returns. Idempotent on a local ID, so a retry after a
  dropped connection can't create a duplicate clinical record.
- **Screening never waits for any of it.**

---

# THE LAYER LIST (if they ask flat-out)

| Layer | Choice | Why that choice |
|---|---|---|
| Language / UI | Kotlin 2.0.20, Jetpack Compose (BOM 2024.09.00) | Native gives direct NNAPI control; cross-platform wrappers abstract away the delegate |
| Build | AGP 8.5.2, Gradle 8.9, JDK 17 | |
| API / ABI | min 26, target 35, `arm64-v8a` only | ABI filter halved the APK |
| Vision inference | ONNX Runtime 1.20.0 + NNAPI | Model runs as-exported |
| LLM | MediaPipe Tasks GenAI 0.10.24 | Qwen2.5-1.5B, Apache-2.0 |
| Camera | CameraX 1.3.4 | |
| Navigation | Navigation Compose 2.8.0 | |
| Storage | DataStore 1.1.1 + JSON files | **No Room, no KSP** — annotation processors are the most common build breaker under deadline, and a few hundred rows don't need SQL |
| Networking | `HttpURLConnection` | One JSON POST doesn't justify OkHttp's dependency tree in an APK already carrying a 16 MB model |
| Telemetry | `BatteryManager`, `PowerManager` | Field metrics measured, not estimated |

**Size:** 49 Kotlin files, 6,750 lines, 64 MB APK (16 MB of it the model).

---

# THE THREE DECISIONS THAT SHOW JUDGEMENT

If you only have 30 seconds of technical time, use these:

1. **CAM instead of Grad-CAM** — because TFLite can't backpropagate, and for this
   architecture they're mathematically identical.
2. **Preprocessing ported arithmetic-for-arithmetic** — because a mismatch there
   fails *silently*, which is worse than crashing.
3. **Quality gate before inference** — because the model cannot refuse to answer,
   so something else has to.

Each one is a case where the obvious approach breaks on-device and we did the
non-obvious thing for a stated reason.

---

# NUMBERS — MEASURED vs NOT YET

## Measured and defensible
- **QWK 0.9271 ± 0.0062** across 3 seeds — on a stratified split of APTOS
  (say "internal validation", not "external"). 94.1% is the *mean* referable
  sensitivity across those seeds; it is not the shipped model's number.
- **The shipped checkpoint**: QWK 0.9324; referable sensitivity **91.4%** at the
  grader's rounding point, **98.2%** at the 1.15 referral threshold the app ships
  (specificity 94.8% → 92.9%). Verified by reproducing the training split and
  matching the recorded confusion matrix exactly.
- Model loads on device: logcat shows `provider=NNAPI (NPU/GPU)`
- LLM loads on device: `LlmNarrator: LLM ready (1597 MB)`
- Quality gate accepts **9/10** real APTOS photos; the one rejection is in the
  blurriest 5% of the dataset

## NOT yet measured — do not quote a number
- **On-device inference latency (ms/image).** The benchmark screen is built but
  a clean run hasn't completed. **Run Settings → Field benchmark, unplugged,
  before you quote any millisecond figure.**
- Power, patients-per-charge, thermal curve — same run produces all of them.

> If asked before you've run it: *"The harness is built and reads the battery
> fuel gauge directly — we'll have the table shortly. I'm not going to quote a
> number I haven't measured."* That answer earns more credit than a guess.
