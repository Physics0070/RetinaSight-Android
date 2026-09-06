# RetinaSight AI — Evaluation Round Pack

Everything you need at the table: the pitch, the stack, and the questions that
will actually be asked.

---

# 1. THE PITCH (≈4 minutes, table format)

## Open with the problem, not the tech (30s)

> "At a diabetic retinopathy screening camp in rural Maharashtra, a technician
> photographs 300 retinas in a day. Those images then queue for a human grader.
> Turnaround is days to weeks. **By the time the result comes back, the patient
> has gone home** — and follow-up collapses. That gap is where people go blind
> from a disease that is treatable if caught early.
>
> RetinaSight AI closes that gap. The referral decision happens **while the
> patient is still in the chair**."

## What it is (20s)

> "It's an Android app that grades diabetic retinopathy **entirely on the phone**
> — no server, no internet — and tells the patient what to do in their own
> language, out loud."

## Demo — run it in this order (2 min)

1. **Turn on airplane mode first.** Say: *"Everything you're about to see runs
   with the radios off."*
2. **Scan Eye → Consent.** "Consent is mandatory, enforced in code — Continue
   stays disabled until it's given."
3. **Patient + which eye.** "DR is asymmetric — one eye can be grade 0 while the
   other needs urgent referral. A result without an eye is clinically incomplete."
4. **Choose a fundus image → result appears in ~100 ms.**
5. **Point at the heat map.** "That's a class activation map showing where the
   model looked. Grad-CAM needs backpropagation and TFLite can't backpropagate —
   so we compute CAM from the classifier weights in the same forward pass.
   Mathematically identical, zero extra cost."
6. **Tap Listen.** Spoken summary in the selected language.
7. **Show a deliberately bad photo → quality gate rejects it** with a specific,
   actionable reason.
8. **Settings → Field benchmark.** Show the telemetry table.

## The differentiation (45s)

> "The closest product is Medios by Remidio — also offline, also on a phone. Two
> things they can't say:
>
> **One: they're locked to their own camera.** Medios ships bundled with
> Remidio's Fundus-on-Phone hardware. We're hardware-agnostic — any fundus image
> source, any Android phone.
>
> **Two: nobody publishes field numbers.** We measure milliseconds per image,
> milliwatts, patients per charge and the thermal curve on the device itself,
> from the battery fuel gauge — 100 back-to-back screenings, and we're explicit
> that the power figures come from that ten-second window rather than a full
> day's camp. That's the table a district health officer actually needs and
> nobody reports it."

## Close on the honest limit (25s)

> "One thing we'll say before you ask: **you cannot photograph a retina with a
> phone camera alone.** This needs a fundus adapter or an existing PHC camera.
> We're the grading and workflow layer, not the optics. Capture is a solved,
> separately-procured problem — the missing piece in rural India is grading
> capacity at the point of care, and that's what this is."

---

# 2. TECH STACK

## The app — 49 Kotlin files, 6,750 lines, 64 MB APK

| Layer | Choice | Why |
|---|---|---|
| Language / UI | **Kotlin 2.0.20 + Jetpack Compose** (BOM 2024.09.00) | Native gives direct NNAPI/delegate control; cross-platform wrappers abstract it away |
| Build | AGP 8.5.2, Gradle 8.9, JDK 17 | — |
| Min / target | API 26 / 35, `arm64-v8a` only | ABI filter halved the APK (104 → 51 MB before the LLM) |
| Inference | **ONNX Runtime 1.20.0** + NNAPI EP | Runs the exported model as-is — no conversion, no re-quantisation |
| On-device LLM | **MediaPipe Tasks GenAI 0.10.24** | Qwen2.5-1.5B-Instruct, Apache-2.0 |
| Camera | CameraX 1.3.4 | |
| Navigation | Navigation Compose 2.8.0 | |
| Storage | DataStore 1.1.1 + JSON files | Deliberately **no Room/KSP** — annotation processors are the #1 build breaker under deadline |
| Networking | `HttpURLConnection` | One JSON POST doesn't justify pulling OkHttp into an APK carrying a 16 MB model |

## The models — two, both on-device

| | Model | Size | Runs on |
|---|---|---|---|
| **Grader** | EfficientNet-B0, 456 px, ordinal objective → ONNX | 16 MB | NNAPI (Hexagon NPU/GPU) |
| **Narrator** | Qwen2.5-1.5B-Instruct int8, Apache-2.0 | 1.6 GB | MediaPipe + XNNPack |

The grader emits **three outputs in one forward pass**: `logits (1,5)`,
`cam (1,5,15,15)`, `grade (1,)` — the grade already rounded from the expected
grade inside the graph.

## Training (prior work, PyTorch)

APTOS 2019, 3,662 images cached at 456 px · EfficientNet-B0 · ordinal loss ·
3 seeds · **QWK 0.9271 ± 0.0062** (mean referable sensitivity 94.1% ± 2.4% across seeds)

**The shipped checkpoint** is the QWK 0.9324 run. On its own held-out split
(n=546, 221 referable) it measures:

| Referral threshold | Sensitivity | Specificity | Missed | False alarms |
|---|---|---|---|---|
| 1.50 — rounding, the grader's balanced point | 91.4% | 94.8% | 19 | 17 |
| **1.15 — what the app ships** | **98.2%** | **92.9%** | **4** | **23** |

The split was reproduced from the training code and verified cell-for-cell
against the recorded confusion matrix before either row was quoted.

## Deliberate engineering decisions worth naming

- **CAM instead of Grad-CAM** — TFLite/ONNX can't backpropagate; for a
  GAP→Linear head, CAM is mathematically identical and forward-only
- **Preprocessing is a contract** — the Kotlin port reproduces Pillow's
  *antialiased* bilinear and the exact crop, because Android's naive scaler
  would silently shift the input distribution
- **Quality gate before inference** — the model always returns a grade; it
  cannot say "this isn't a fundus image"
- **Explanation is templated, not generated** — a 1.5B model asked to explain a
  diagnosis will eventually invent a treatment
- **No `INTERNET` permission in the diagnostic path** — offline is provable from
  the manifest, not merely observed

---

# 3. JURY QUESTIONS

## The hard ones — rehearse these

**Q: You can't photograph a retina with a phone camera. Where does the image come from?**
> Correct, and we say it before you ask. It needs a fundus adapter (D-EYE, oDocs,
> Peek — ₹15k–₹3L) or an existing PHC camera. We're the grading layer. The
> bottleneck in rural India isn't capture — PHCs receive cameras under NPCBVI —
> it's that there's no ophthalmologist to grade what they capture.

**Q: Medios already does offline AI on a phone. What's new?**
> Hardware-agnostic and open, versus locked to Remidio's camera with a closed
> model. Plus 11 Indian languages with voice, which they don't do. But to be
> straight: Medios is clinically validated and deployed. We're not.

**Q: QWK 0.93 — on what test set?**
> A stratified split of APTOS — **same dataset, same cameras**. That is not
> external validation, and we don't claim it will hold at 0.93 on a different
> camera. Validating on IDRiD or Messidor is the next step.

**Q: What sensitivity do you actually ship, and why that number?**
> 98.2%, at a referral threshold of 1.15 on the expected grade. The grader's own
> rounding point (1.5) gives 91.4% — it is balanced, and balance is wrong for
> screening. A missed referral can end in blindness; a false alarm costs one
> clinic visit. Moving the threshold to 1.15 turns 19 missed referable patients
> into 4, and costs 6 extra false alarms out of 325 healthy eyes. We swept the
> threshold across the validation split and ship the point, not the default.

**Q: Is this a medical device? Do you have regulatory clearance?**
> No. It's a screening aid with mandatory clinician confirmation, and the
> disclaimer is on every result screen and in the About section. Under CDSCO,
> software that *diagnoses* is a regulated device — which is exactly why we don't
> use that word.

**Q: Why offline at all? Cloud would be easier.**
> Honestly, cloud would work in many settings. Offline matters at the margin —
> and the margin is exactly the rural camp with no signal. It also means no
> per-inference cost and no health data leaving the device.

**Q: Who pays for this?**
> Not the patient. NPCBVI government programmes, NGO screening budgets, private
> chains. And the phone isn't the cost driver — the camera is.

## The technical ones

**Q: Is it really on the NPU?**
> ONNX Runtime with the NNAPI execution provider; logcat shows
> `provider=NNAPI (NPU/GPU)`. Provider affects speed only — never output shape
> or the decision. A phone without NNAPI produces identical results, slower.

**Q: How do you know the phone gives the same answer as the desktop?**
> The preprocessing is ported arithmetic-for-arithmetic and we generate
> reference vectors from the Python pipeline to diff against. A preprocessing
> mismatch is how on-device models fail silently — confident, wrong, and
> invisible in the notebook.

**Q: Where do the field metrics come from?**
> `BatteryManager` charge counter and voltage, `PowerManager` thermal status and
> headroom. We measure an idle baseline and report net power, and compute energy
> two independent ways — if they disagree by more than 30% the app says so
> instead of printing a confident wrong number.

**Q: What does the LLM actually do?**
> It restates a result the CNN already decided — grade, confidence, urgency
> passed in as fixed facts. It never decides anything. And we validate its
> output: it currently produces byte-BPE mojibake in Devanagari, so we detect
> and discard that rather than show it.

## Known gaps — say them before they're found

- Validation is internal (APTOS split), not external
- 8 of 11 languages are machine-drafted and not yet reviewed by a native speaker
  (all 11 are complete and render in their own script; `ta te kn ml bn gu pa or`
  await review, urgency and grade strings first)
- The LLM's Indic output is unusable; the guard suppresses it
- Marathi TTS voice isn't installed on a fresh phone (app offers to install it)
- Not clinically validated, not regulatory-cleared

---

# 4. ONE-LINE ANSWERS TO MEMORISE

- **What is it?** Offline DR grading on a phone, in the patient's language.
- **Who uses it?** Vision-centre technicians and NGO camps who have a camera but no ophthalmologist.
- **Why does it matter?** Same-visit referral instead of a days-long grading backlog.
- **Why is it hard?** On-device inference, matched preprocessing, and a quality gate so bad photos don't get confident answers.
- **What's the number?** QWK 0.93 on the grader; 98.2% referable sensitivity at the
  screening threshold we ship; **101 ms per image** on the NPU (p50 of 100 measured
  on-device screenings, no throttling). Power and patients-per-charge come from a
  10-second sustained-load sample — we say so rather than calling it a camp.
- **What's the limit?** Needs a fundus camera. Screening aid, not a diagnosis.
