# RetinaSight AI — UI/UX design brief

Paste or upload this whole file into Google AI Studio.

---

## 0. What I want from you

Design the **complete screen set** for an Android app that is already built and
working. I am not asking for a rewrite — I am asking for the visual and
interaction design of every screen **in every state it can actually be in**.

For each screen below, give me:

1. A layout description precise enough to build from (hierarchy, spacing, what
   is emphasised, what recedes).
2. **Every state variant listed for that screen.** A design that only covers
   the happy path is not usable — this is a medical device workflow and the
   unhappy paths are where patients get hurt.
3. Interaction and motion: what animates, how long, what triggers it.
4. Jetpack Compose implementation notes where a layout is non-obvious.

Return it screen by screen. Do not summarise. I would rather have twelve
thorough screens than a mood board.

---

## 1. What the app does

RetinaSight AI grades **diabetic retinopathy** from a fundus (retina)
photograph, entirely on the phone, and tells the patient the result out loud in
their own language.

**Who uses it:** vision-centre technicians and NGO screening-camp staff in
India — *not* patients, and not ophthalmologists. There are ~25,000 vision
centres with a fundus camera and no eye doctor. Images queue for a human grader
for days or weeks; by then the patient has gone home.

> The referral decision has to happen while the patient is still in the chair.

**One constraint stated up front:** a phone camera **cannot** photograph a
retina on its own — it needs optics through the pupil. The app is the grading
and workflow layer. Capture requires a fundus adapter (D-EYE, oDocs, Peek) or a
clinic fundus camera. The capture screen says so explicitly. Do not design
anything that implies the bare phone can image an eye.

**Physical context that should drive every decision:**

- Used **outdoors, in direct sunlight**, at arm's length.
- The operator may be wearing gloves.
- The patient may be elderly and may not read any language.
- The phone is often **handed across a table** to the patient or a relative.
- A camp session means **forty-plus patients back to back**. An animation that
  delights once is an obstruction by the fortieth.
- The device is a mid-range Android phone. No guaranteed GPU headroom.

---

## 2. Non-negotiable rules

These are not preferences. Breaking any of them is a defect.

1. **Never invent a clinical number.** If the app does not measure it, it does
   not appear. No intraocular pressure, no pupil diameter, no "optical axis
   lock", no fabricated accuracy figures. A technician will copy a
   plausible-looking value into a record.
2. **Never use the word "diagnosis".** This is screening and triage. It has no
   regulatory clearance.
3. **Colour is never the only signal.** Every severity state carries colour +
   icon + words + speech. Assume colour blindness and assume glare.
4. **All text must be translatable.** Eleven languages ship: English, Hindi,
   Marathi, Bengali, Gujarati, Punjabi, Odia, Tamil, Telugu, Kannada,
   Malayalam. Never design around a fixed English string length — **Tamil and
   Malayalam labels run 2–3× the English width** and will wrap. Show me how
   each layout survives that.
5. **Minimum 16sp for anything user-facing.** Large numbers much bigger.
6. **WCAG AA minimum, target AAA** on anything carrying meaning.
7. **The diagnostic path never touches the network.** The photograph never
   leaves the phone. Design must never imply upload is required.
8. **The explanation shown to the patient is templated, never generated.**

---

## 3. Existing design system — build on this, do not replace it

```
Deep Navy        #0A2463   primary, app chrome
Calming Teal     #247BA0   secondary, interactive accent
Laser Cyan       #5EEAD4   scan beams, HUD values, dark-surface accent
Medical BG       #F8FAFC   app background
Card Surface     #FFFFFF   cards
Surface Variant  #F1F5F9   secondary fills
Text Primary     #0F172A
Text Secondary   #475569   captions, supporting copy
Outline Border   #E2E8F0   hairline card borders ONLY, never text
Darkroom BG      #070B14   capture + analysis ground
```

**Severity scale — five steps, each carries white text so each is dark enough
for AA:**

```
Grade 0  No DR            fill #047857   tint #ECFDF5
Grade 1  Mild NPDR        fill #0369A1   tint #F0F9FF
Grade 2  Moderate NPDR    fill #B45309   tint #FFFBEB
Grade 3  Severe NPDR      fill #C2410C   tint #FFF7ED
Grade 4  Proliferative    fill #B91C1C   tint #FEF2F2
```

**Component grammar already in place:**

- Cards: white, `1dp #E2E8F0` border, `2dp` elevation, radius 16–20, padding 18.
- Severity cards: tint fill, `1.5dp` border of the grade colour at 60% alpha.
- Primary CTA: full width, min height 88dp, radius 20, Deep Navy.
- Segmented buttons: height 46, radius 12.
- Text fields: radius 12, focus Calming Teal.
- Typography: system font (resolves to Noto, which covers all nine Indic
  scripts). **Do not specify a custom display font** — bundling one breaks
  rendering in eight of the eleven languages.

**Animation components that already exist**, all drawn in Compose Canvas with
no 3D engine and no model files:

- Scanning logo: stylised fundus, optic disc, fovea, vascular arcades, a teal
  beam on a 2.8s sweep, four lesions that pulse with crosshair rings as the
  beam crosses them.
- Laser scan overlay for the inference moment.
- Anatomical 3D eye: micro-saccades, spontaneous blinks, drag-to-look,
  tap-to-blink, rotates ±25° toward the selected eye.
- Eyelid privacy shutter: two lids that close over the retinal photo.
- Clinical HUD: monospace strip on dark ground.

---

## 4. The screens, and every state each can be in

### 4.1 Home

Entry point. One obvious action.

Contains: connectivity pill, scanning logo, wordmark, tagline, primary "Scan
Eye", secondary "Past checks", settings affordance.

**States:**
- Online / Offline (the pill must read truthfully; offline is the *normal* and
  *good* state — screening works either way and the design should not make
  offline look like a fault)
- Model ready / model still warming up
- History empty / history populated

---

### 4.2 Consent and patient intake

One scrolling screen, three steps. **Consent is mandatory and gates
everything.** Details are optional — a queue of forty people is real, and a
worker who cannot skip typing will stop recording anything at all.

**Step 1 — Consent.** A checkbox row, whole row tappable. Plays a confirmation
chime and haptic when *given* (never when withdrawn — that reads as approval).

**Step 2 — Patient details.** Name, age, sex (dropdown: male / female / other /
prefer not to say), phone, known diabetic (yes / no / unknown), years since
diagnosis.

**Step 3 — Which eye.** Left button on the left, right button on the right,
with the anatomical 3D eye between them rotating toward the selection.

Two exits: "Continue to photo" (needs consent + name + valid age) and "Skip
details" (needs consent only, always available).

**States:**
- Consent not given → primary CTA disabled, and it must **say why**, not just
  grey out
- Consent given, details incomplete
- Consent given, details complete
- Elevated-risk flag (diabetic ≥ 10 years) — how should this surface without
  alarming the patient reading over the worker's shoulder?
- Age invalid (outside 1–120)
- Long-language layout: every label at 2–3× width

---

### 4.3 Capture

Darkroom. Everything outside the circular aperture is painted out.

Contains: clinical HUD strip, circular viewfinder with alignment ring, shutter,
"import from fundus camera", and the standing note that a phone camera alone
cannot image a retina.

**HUD shows only measured facts:** camera ready/denied, offline/online, and the
actual inference provider (`NNAPI (NPU/GPU)` or `CPU`).

**States:**
- Camera permission not yet asked → rationale dialog first, in the patient's
  language, then the system prompt
- Permission granted → live preview
- Permission denied → import path still fully available, never a dead end
- Portrait / landscape (in landscape the controls must stay reachable — the
  column scrolls)
- Image decode failed after import

---

### 4.4 Analysing

Shown while inference runs. **On this hardware it takes about 100ms**, so this
is usually seen for a moment. Design for a flash, not a wait — but it must also
hold up if a slower phone takes two seconds.

Beam sweeps the scanning mark. Says the work is happening on the phone.

**States:** running, and the three exits below.

---

### 4.5 Quality gate — rejection

If the photograph is not gradeable the app refuses to grade it. **This is a
feature, not an error**, and the design must make retaking feel routine rather
than like a failure.

**Six issues, which can occur in combination:**

```
BLUR                 hold steady, let it focus
LOW_LIGHT            underexposed
OVEREXPOSED          clipped highlights
POOR_FRAMING         retina not centred
RETINA_NOT_VISIBLE   this may not be a fundus image at all
LOW_RESOLUTION       image too small to grade
```

Design the single-issue case, the multi-issue case, and the case where the
image is not a retina at all. Show the quality score.

---

### 4.6 Result — the payoff screen

Speaks aloud automatically on arrival, because the patient may not read.

Contains: severity banner, confidence, the retinal image with a Grad-CAM
heatmap toggle, an eyelid privacy shutter, "what to do", "what this means", an
optional plain-language restatement, save, and new scan.

**Severity: five states.** Grade 0 through Grade 4, each with its own fill,
tint, icon and words.

**Urgency: five states**, which do not map 1:1 to grade —

```
ROUTINE     MONITOR     SOON     URGENT     IMMEDIATE
```

**Confidence: three bands, and this is subtle.**

```
HIGH    ≥ 0.90    measured 93.3% correct
MEDIUM  0.55–0.90 measured ~70% correct
LOW     < 0.55    measured 50.0% correct
```

The model is **overconfident at the very top**: 0.90–0.95 is 97.6% correct but
0.95–1.00 falls to 83.9%. Confidence is *not* monotonic with accuracy. How
should a UI communicate "very sure" honestly given that?

**Referral is a separate axis from grade.** The app refers at an expected grade
of **1.15**, below the rounding point of 1.5. So a scan can display as "Grade 1
— Mild" *and still be referred*. This **borderline referral** state is the
hardest thing on the screen to communicate and I want your best thinking on it:
the grade says mild, the action says go see a doctor, and both are correct.

**Other states:**
- Heatmap available / unavailable
- Retinal view shown / hidden behind the privacy shutter
- Plain-language restatement: not requested / generating / shown / unavailable
- Speaking / stopped
- Saved / not yet saved
- Voice unavailable for the chosen language

---

### 4.7 History

List of past checks, each tappable, with a progression strip showing change
over time.

**States:** empty, one record, many records, delete confirmation, and a
patient whose grade is **worsening across visits** — how should progression
read at a glance?

---

### 4.8 History detail

Full report for one past check: date, patient, which eye, grade, confidence,
urgency actually given at the time, sync state, and delete.

**States:** with patient details / anonymous (details were skipped), synced /
waiting to send, image present / image deleted.

---

### 4.9 Settings, language, benchmark

- Settings: language, voice test, model status, field benchmark entry.
- Language: eleven cards in a bottom sheet, each speaking its own name on tap,
  each rendering in its own script.
- Benchmark: latency, power, thermal curve, patients-per-charge.

---

## 5. What I specifically want you to solve

1. **The borderline referral.** Grade reads mild, action says refer. One
   screen, both true, no contradiction felt by the reader.
2. **Honest high confidence**, given the model is overconfident at the top.
3. **Quality rejection that does not feel like failure**, so workers retake
   instead of forcing a bad image through.
4. **A layout that survives Tamil and Malayalam** at 2–3× English width.
5. **Sunlight legibility** without the palette turning into a warning-label
   aesthetic.
6. **The offline state reading as strength, not fault.**

---

## 6. Format of your answer

Screen by screen, in the order above. For each: layout, every listed state,
motion, and Compose notes. Flag anywhere my constraints conflict with good
design and tell me which to relax.

Do not propose anything requiring a 3D engine, a model file, a custom bundled
font, a network call in the diagnostic path, or any clinical value the app does
not measure.
