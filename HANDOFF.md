# HANDOFF — RetinaSight AI — iQOO Hackathon 2026

**Updated:** 2026-09-06 · **Machine:** Windows 11, `D:\SOHAM ALL\hackathons\IQOO`
**Device:** vivo I2501 (iQOO 15), Android 16 / SDK 36, arm64-v8a, Snapdragon 8 Elite Gen 5
**Repo:** https://github.com/Physics0070/RetinaSight-Android

> Read §5 before writing code. It lists the dead ends that cost hours.

---

## 0. GET RUNNING

```powershell
$env:JAVA_HOME    = "D:\android-toolchain\jdk"
$env:ANDROID_HOME = "D:\android-toolchain\sdk"
$env:PATH = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:PATH"

cd "D:\SOHAM ALL\hackathons\IQOO\RetinaSightApp"
D:\android-toolchain\gradle\bin\gradle.bat assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

**Use a USB cable, not wireless ADB.** Wireless dropped six times in one
session — Android kills it on screen lock and on any Wi-Fi change, and the
connect port differs every time. USB survives all of it. The one thing USB
cannot do is the energy benchmark, because it charges the phone (§5.24).

---

## 1. GOAL

An Android app that grades diabetic retinopathy from a fundus photograph
**entirely on the phone**, with no server in the diagnostic path, and tells the
patient the result **out loud in their own language**.

**The users** are vision-centre technicians, NGO screening camps (Aravind,
LVPEI, Sightsavers) and teleophthalmology programmes — not patients. India has
~25,000 vision centres with a fundus camera and no ophthalmologist. Images queue
for a human grader for days or weeks; by then the patient has gone home.

> **The referral decision happens while the patient is still in the chair.**

**A constraint that must be stated first:** a smartphone camera *cannot*
photograph a retina — it needs optics through the pupil. This app is the grading
and workflow layer. Capture needs a fundus adapter (D-EYE, oDocs, Peek) or a
clinic camera. The capture screen says so.

**vs Medios (Remidio)**, the closest competitor: hardware-agnostic (Medios is
locked to their own camera), open weights and Apache-2.0, 11 Indian languages
with voice, and published field telemetry.

---

## 2. CURRENT STATE

✅ verified on the physical device · 🟡 builds, not verified · ❌ not done

| Area | Status |
|---|---|
| 11 languages, full string sets (181 keys) | ✅ all render in own script |
| Consent gate (mandatory, timestamped) | ✅ |
| Patient details + gating | ✅ Continue needs name + age; skip always available |
| Camera capture + photo import | ✅ |
| Quality gate + retake | ✅ |
| Inference on NNAPI | ✅ `provider=NNAPI (NPU/GPU)` |
| Result: grade, confidence band, CAM heat map | ✅ |
| Referral threshold at 1.15 | ✅ `ReferralPolicy.kt` |
| Confidence bands calibrated | ✅ High ≥ 0.90, measured not guessed |
| Spoken summary (TTS) | ✅ hi/mr/ta all dispatch embedded voices and play audio |
| On-device LLM narration | ✅ English only; template fallback elsewhere (§5.20) |
| History list + progression strip | ✅ |
| History detail report | ✅ |
| Delete a past check (list + detail) | ✅ removes record **and** image file |
| Online/offline badge | ✅ verified both directions via airplane mode |
| Language picker back button | ✅ returns to previous screen, no longer exits |
| Field benchmark: latency | ✅ p50 101.4 / 113.9 ms across two runs |
| Field benchmark: energy, paced | ❌ **never run unplugged** (§6.3) |
| Clinic sync against a live backend | ❌ never tested; entry point removed from home |
| External validation (IDRiD/Messidor) | ❌ (§6.1) |
| Native review of 8 languages | ❌ machine-drafted (§6.2) |

### Hard gaps — say these before a judge finds them

1. **Power, thermal and patients-per-charge are extrapolated from ~10 seconds.**
   100 back-to-back inferences finish in 10.4–11.8 s. Latency is solid. Two runs
   disagreed by **40%** on net power (7,805 vs 10,891 mW) and on
   patients-per-charge (321 vs 245) — that disagreement *is* the proof the
   window is too short. Battery temperature never moved (33.1 → 33.1 °C) because
   nothing warms up in ten seconds, so "no throttling" says nothing about a camp.
   Say *"a ~10-second sustained-load sample"*, never *"over a camp"*.
2. **LLM narration works in English only.** Marathi/Hindi/Tamil fall back to
   templated translated text (§5.20). Do not claim "LLM narration in 11
   languages" — a judge will tap it in Marathi.
3. **Validation is internal.** QWK 0.93 is a stratified split of APTOS: same
   dataset, same cameras. Not external validation.
4. **8 of 11 locales are machine-drafted and unreviewed.** Say "drafted, pending
   native review", not "11 verified languages".
5. **Not a medical device.** No clearance, `clinically_validated: false`. Never
   say "diagnosis".

---

## 3. ACTIVE FILES

`RetinaSightApp/app/src/main/java/com/retinasight/ai/`

| File | Why it matters |
|---|---|
| `core/model/ReferralPolicy.kt` | **The screening threshold.** Full sweep in its doc comment |
| `core/model/RetinaResult.kt` | Confidence bands + their measured derivation; `expectedGrade` |
| `core/inference/RetinaPreprocessor.kt` | **THE CONTRACT** — see below |
| `core/inference/OnDeviceInferenceEngine.kt` | ONNX + NNAPI, softmax, CAM, templated explanation |
| `core/llm/LlmNarrator.kt` | Script check + byte-decode repair + template fallback |
| `core/sync/ConnectivityObserver.kt` | Default-network callback; answers from callback args |
| `core/sync/SyncManager.kt` | All `_status` writes are atomic `update {}` |
| `core/benchmark/BenchmarkRunner.kt` | `secondsPerPatient` duty cycle |
| `core/history/ScanHistoryStore.kt` | `delete(id)` removes the image file too |
| `ui/screens/HistoryDetailScreen.kt` | Full past-check report |
| `ui/components/CommonComponents.kt` | Button labels wrap (`weight(1f, fill = false)`) |
| `MainActivity.kt` | Language picker **overlays** the nav host; `BackHandler` |

**Preprocessing contract — must match exactly:**
1. Crop to retinal disc: luminance (BT.601) > **18**, bounding box, **+2% padding**
2. Resize to **456×456** with **Pillow's antialiased BILINEAR**
3. `/255`, ImageNet mean/std, NCHW float32

**No Ben Graham. No circle mask.** Either changes the input distribution.

**Model:** `dr-v2.onnx` (16 MB), EfficientNet-B0 @ 456 px, APTOS 2019.
Checkpoint `efficientnet_b0-20260823-124225`. QWK **0.9324**, accuracy 83.3%.
Outputs `logits (1,5)`, `cam (1,5,15,15)`, `grade (1,)` — the graph **already
rounds the expected grade**. Using argmax on device would disagree with the
reported QWK.

**Design rules** (break only deliberately, and say so): nothing hardcoded in the
medical path · the diagnostic path never touches the network · the eye
photograph never leaves the phone · the explanation is templated, never
generated · consent precedes capture · never claim "diagnosis" · **never quote a
number that has not been measured**.

---

## 4. CHANGES MADE (2026-09-05 → 06)

**Screening**
- `ReferralPolicy.kt`: referral threshold moved from the rounding point 1.5 to
  **1.15** on the expected grade. On the validation split (n=546, 221 referable):
  91.4% → **98.2% sensitivity**, 94.8% → 92.9% specificity, **19 → 4** missed
  referable patients. The displayed grade still comes from the graph, so QWK is
  unaffected.
- Confidence bands **measured, not guessed**. Binning the split by displayed
  confidence: below 0.55 → 50.0% correct, 0.55–0.90 → ~70%, 0.90+ → **93.3%**.
  High moved 0.80 → 0.90; `LOW_CONFIDENCE` now references the same constant.
  The model is overconfident at the top: 0.90–0.95 is 97.6% correct but
  0.95–1.00 falls to 83.9%, so confidence is *not* monotonic with accuracy.

**Corrections to previously published numbers**
- Referable sensitivity was quoted as 94.1%. That is the **3-seed mean** from
  `456px-ordinal-summary.json`, not this checkpoint. The shipped model's own
  figure is **91.4%** (98.2% at the new threshold).
- **The validation split was reproduced exactly** — all 25 confusion-matrix
  cells, QWK, accuracy, sensitivity and specificity match `metrics.json`. This
  closes the contamination dead end (§5.2).

**Connectivity** — the offline badge reports real state. Two bugs behind it: a
`SyncManager` read-modify-write race, and `ConnectivityObserver.onLost`
re-querying a network still reported active (§5.21, §5.22).

**Narration** — script validation + translated fallback, so the section works in
every language (§5.20).

**Layout** — button labels wrap instead of overflowing; capture title clears the
floating home button; camera preview clipped to its bounds; Tamil/Malayalam
import labels shortened; history detail rebuilt without label/value rows.

**History** — tappable rows, full detail report, delete with confirmation in
both places, `expectedGrade` persisted so history shows the urgency actually
given rather than recomputing it.

**Navigation** — language picker overlays the nav host; back dismisses it.

**Benchmark** — `secondsPerPatient` duty cycle (0/15/30, default 30), recorded
in the report and the CSV.

**Removed** — clinic connect from the home screen. `ClinicScreen` still exists
but is unreachable.

---

## 5. FAILED ATTEMPTS — DO NOT REPEAT

### Model / ML
1. **Do NOT retrain.** `dr-v2.onnx` is at QWK 0.93. A retrain will not converge
   in a hackathon window and domain shift can *lower* APTOS accuracy.
2. ~~Validation split reproduction was contaminated~~ **SOLVED.** It scored 0.978
   because the split used seed 42. Run 124225 predates the `split_seed` field, so
   its split used the **run seed 143**. With 143 it matches exactly.
3. **Do NOT train on IDRiD.** 516 images is far too few, and training on it
   destroys the only external test set available. Use it to *validate*.
4. **Gemma is gated on Hugging Face.** Qwen2.5 is `gated=False` and Apache-2.0.
5. **Kimi K2 / DeepSeek-V3 / GLM / Nemotron cannot run on this phone.** 127–500
   GB at int4 against 8.5 GB RAM. MoE does not help; all weights must be resident.
6. **Grad-CAM does not survive export.** For a GAP→Linear head, CAM from
   classifier weights is mathematically identical and forward-only.

### Android
7. **`createConfigurationContext` on the Activity broke every activity result.**
   Wrap the *application* context and override only `getResources()`.
8. **Kotlin init-order NPE in `SyncManager`** — a flow declared below `init` is
   null when an init coroutine touches it.
9. **`SupervisorJob` alone does not contain a coroutine crash.** You need a
   `CoroutineExceptionHandler`.
10. **Heat map drawn over the original photo is misaligned** — overlay
    `RetinaResult.processedImage`.
11. **Capture buttons unreachable in landscape** — the column needs scroll.
12. **`ChoiceRow` overflowed with 4 options** — use `FlowRow`.
13. **Content drew under the status bar** — `safeDrawingPadding()` at the nav root.
14. **`ImageDecoder` returns a hardware bitmap** with no readable pixels — force
    `ALLOCATOR_SOFTWARE`.
15. **UI automation taps landed in vivo Remote Control.** Always check
    `mCurrentFocus` before sending input events.

### Tooling / environment
16. **Piping `y` to `sdkmanager --licenses` does not work** — write the license
    hashes into `sdk/licenses/` directly.
17. **PowerShell 5.1 wraps native-exe stderr in an ErrorRecord** and sets `$?`
    false on exit 0. Don't redirect native stderr.
18. **PowerShell `>` corrupts binary** — use `adb shell screencap` + `adb pull`.
19. **Bash heredocs mangle Kotlin/JSON** containing apostrophes and backslashes.
    Use the Write tool for large files.

### New this session
20. **MediaPipe corrupts multi-byte scripts, and it cannot be repaired.**
    Qwen output for Marathi/Hindi/Tamil arrives *half decoded*: real Devanagari
    next to byte-encoding leftovers (`Ġ à ¤`) **and `U+FFFD`**. MediaPipe decodes
    token by token, and a 3-byte character straddling a token boundary is
    destroyed before the app sees it. English is 1 byte/char so never splits.
    A GPT-2 byte-decoder repair was implemented and **does not work** — it needs
    *all* characters byte-encoded and this output is a mixture. The fix was to
    validate the script and fall back to the translated `speak_summary`. Do not
    spend more time on this; the data is gone upstream.
21. **`_status.value = _status.value.copy(x = suspendCall())` is a race.** The
    receiver is evaluated *before* the suspend, so the coroutine writes back a
    stale snapshot. It silently reverted `isOnline` to false. Use `update { }`
    and hoist suspend calls out.
22. **`onLost` must not re-query `activeNetwork`** — it still points at the
    network going away, so it reports online and nothing ever corrects it. Use
    `registerDefaultNetworkCallback` and answer from the callback's arguments.
23. **Android 16 blocks shell Wi-Fi toggling.** `svc wifi disable` and
    `cmd wifi set-wifi-enabled disabled` both silently no-op — `wifi_on` stays 1.
    Two "offline" tests were invalid because of this and produced a wrong
    conclusion that the fix had failed. Use `cmd connectivity airplane-mode
    enable` **over USB**, which does work.
24. **The benchmark refuses to run over USB** — USB charges the phone and the
    runner correctly aborts ("Unplug the charger first"). A real energy run needs
    the phone unplugged.
25. **Do not reuse strings as labels they were not written for.** Using
    `eye_prompt` ("Which eye are you photographing?") as a field label, to avoid
    adding a translated string, produced a label so long it wrapped the value one
    character per line. Self-describing values need no label at all.
26. **Compose buttons need `weight(1f, fill = false)` on the label.** Without it
    a long label overflows the button and drags the icon off-centre. Fixing the
    wrap is not enough alone — a 43-character Tamil label still broke mid-word
    across four lines. Shorten the label too.
27. **`PreviewView` paints outside its Compose bounds** — over the title above
    and the instruction below. Use `clipToBounds()` on the preview Box.
28. **A screen that replaces the nav host destroys the back stack.** The language
    picker did this, so back had nothing to pop and finished the activity.
    `BackHandler` alone only half-fixes it — dismissing still rebuilt the graph
    at Home. Render such a screen **over** the nav host instead.
29. **`import androidx.compose.runtime.remember` is a substring of
    `rememberCoroutineScope`** — a naive "is this import present?" check passes
    when it isn't. Match whole lines.

---

## 6. NEXT STEPS (in order)

### 1. External validation on IDRiD — highest value
The one number the project cannot currently produce. Every metric comes from a
single dataset and a single set of cameras. Run `dr-v2.onnx` over IDRiD (516
images, different camera) with the *exact* serving preprocessing — import
`ml/datasets/retinal_dataset.py` and `backend/app/ml/preprocessing.py` from
Omnikon rather than reimplementing. Report QWK, accuracy and referable
sens/spec **at the shipped threshold of 1.15**, not at argmax and not at 1.5.

**Expect it to be worse** (perhaps 0.80–0.88) and say so plainly. "0.93 internal,
0.85 on an unseen camera" is far stronger than one number with no
generalisation evidence at all. Do **not** train on it (§5.3).

### 2. Native review of the 8 drafted locales
`ta te kn ml` and `bn gu pa or`. Review in harm order: `urgency_*` first (these
tell a patient how soon to see a doctor), then `grade_*_desc`, then consent and
disclaimer, then `explain_*`, then the spoken `speak_*` lines. Run
`python scripts/check_translations.py` before every push. Briefs are in
`TASKS_RUTURAJ.md` and `TASKS_CHITRANGAD.md`.

### 3. One unplugged paced benchmark
**Unplug the phone** (§5.24). Settings → Field benchmark → **50** screenings,
**30** s per patient → Run. ~25 minutes, screen on. That gives a real battery
delta, a real thermal curve and a defensible patients-per-charge figure. Then
update `README.md`, `PITCH_PACK.md` and `BENCHMARK_METHODOLOGY.md` and drop the
"~10-second sample" hedge.

### 4. Lower priority
- Clinic sync against a live backend; auth UI (`/sync/push` needs `SYNC_WRITE`)
- AI4Bharat ONNX TTS fallback for languages with no system voice
- Decide whether `ClinicScreen` is deleted or relinked from Settings

---

## 7. OPEN QUESTIONS FOR THE OWNER

- **Commit authorship.** Commits are being rotated across the three GitHub
  profiles at the owner's instruction. Latest is `ruturajnalbalwar-arch`; next in
  rotation is `chitrangad-ram-sapate`. The work is not written by them.
- **TTS reported as "English only".** Could not reproduce: hi/mr/ta each
  dispatched an `-embedded` voice and played audio, at max volume, unmuted, with
  no "voice unavailable" message. Most likely observed during the airplane-mode
  window used to test the offline badge. Recheck before treating it as a bug.
