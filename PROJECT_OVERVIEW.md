# RetinaSight AI — Project Overview

**Event:** iQOO Hackathon 2026 (phone-first, on-device track)
**Device:** iQOO 15 — Snapdragon 8 Elite Gen 5, dedicated Q3 chip, 16GB LPDDR5X, Hexagon NPU
**One-line pitch:** An offline, on-device retina-screening app that grades diabetic retinopathy, shows where it looked, and explains the result by voice in the user's native Indian language — built for rural clinics with no internet.

---

## 1. Problem
India has a large diabetic population and a severe shortage of ophthalmologists, especially rural. Diabetic retinopathy (DR) is a leading cause of preventable blindness and is treatable if caught early. Existing AI DR tools assume cloud access, English literacy, and a clinician in the loop — none of which hold in a rural setting.

## 2. Solution
A mobile app that runs **entirely on the phone**:
1. Capture or upload a fundus (retina) image.
2. **On-device classifier** grades DR severity (0–4) with a confidence score.
3. **Grad-CAM heatmap** overlays *where* the model looked → interpretability = trust.
4. **On-device explanation** in the user's native language, **read aloud** (voice-first for low-literacy users).
5. **Referral urgency** shown with color + icon + voice ("see a doctor within X").
6. Works with **airplane mode on** — the entire pipeline is local. Reports saved locally as PDF.

It is positioned as a **screening/triage aid, not a diagnosis.**

## 3. Why on-device (hackathon strategy)
The rulebook rewards on-device builds heavily: "highest on-device builds preferred for Top 10," local model = brownie points, on-device inference = free (zero OpenRouter credits), and HackTracker logs inference calls/thermals as proof. Judging weights: End product 30% · Novelty 20% · Creative phone use 15% · Technical depth 15% · Office Kit 10% · Demo 10%. A fully on-device, offline medical app hits Novelty + Creative phone use + Technical depth + the Top-10 preference at once, and the rural/offline framing makes the novelty real, not cosmetic.

## 4. Architecture (two on-device stages)
```
Fundus image (camera / gallery)
   │  preprocessing: circle-crop + Ben Graham / CLAHE  (same at train & inference)
   ▼
[Model 1] Retina classifier — LiteRT/TFLite, INT8, QNN delegate → Hexagon NPU
   │  → DR grade 0–4 + confidence
   │  → Grad-CAM heatmap
   ▼
[Stage 2] Explanation — grounded on the classifier's STRUCTURED output
   │  (on-device small LLM tightly grounded, OR deterministic template with real values)
   ▼
Native-language report + voice (on-device TTS)  →  saved locally (PDF)
```
- **Accuracy lives in Model 1** (the classifier). The jury tests whether it reads the retina correctly — invest accuracy budget here.
- **Stage 2 never invents clinical facts** — it only rephrases real, structured outputs into plain language. This prevents hallucination under jury inspection.

## 5. Model & accuracy plan
- Base: team's existing DR classifier (~80% reported). Fine-tune, do not retrain from scratch.
- Data: **Combined DR 21K** (APTOS+IDRiD+Messidor+EyePACS, pre-merged) + **IDRiD** (Indian population). Hold out **APTOS** for validation. Pull rare-class images from **EyePACS** only if needed.
- Levers, in order of payoff: preprocessing (Ben Graham + circle-crop) → class-imbalance fix (weights/oversample severe & proliferative) → fine-tune top layers → test-time augmentation.
- Metric: **quadratic weighted kappa** (the DR-grading standard), not plain accuracy.
- Deployment: export → INT8 TFLite → QNN delegate for the Hexagon NPU → sub-second inference target.
- Details in `retina_datasets_reference.md`.

## 6. Native-language support (real, on-device)
Target languages (largest Indian languages, all supported by AI4Bharat on-device stacks): **Hindi, Marathi, Tamil, Telugu, Kannada, Bengali, Gujarati, Malayalam, Punjabi, Odia, + English.**

On-device voice/text stack (offline):
- **Android `TextToSpeech`** first — built-in, offline voice packs for several Indian languages, zero app-size cost.
- **AI4Bharat Indic-TTS (VITS) / IndicF5**, exported to **ONNX**, bundled as a fallback for languages or devices the system TTS doesn't cover. (Reference: on-device Hindi TTS via ONNX runs offline on CPU in real time.)
- App UI strings: standard Android per-locale resource files (`values-hi/`, `values-mr/`, `values-ta/`, …). This is correct i18n — not "hardcoding."

**"Nothing hardcoded" rule (authenticity):** every medical value (grade, confidence, heatmap, urgency, explanation) is derived from the real model + the actual image each run. No canned results, no fake demo data path. Only *UI chrome* is localized via resource strings.

## 7. Performance & offline design
- Preload the model at app start; keep it warm.
- INT8 + NPU for sub-second inference; TTS audio cached per phrase.
- Fully offline: no network permission needed for the core path; explicit "offline" indicator in the UI builds trust.
- High-contrast, sunlight-readable UI; large tap targets; voice-first so low-literacy users can use it.

## 8. Tech stack
- **App:** Native Android — Kotlin + Jetpack Compose.
- **Inference:** LiteRT/TFLite + QNN delegate (Qualcomm AI Hub for Snapdragon-optimized export).
- **Explanation:** on-device grounded LLM (MediaPipe LLM Inference; Gemma 3n / Phi-3.5-mini int4) OR deterministic template.
- **Voice:** Android TextToSpeech + AI4Bharat ONNX TTS fallback.
- **Storage:** on-device (Room/DataStore) for history; local PDF export.

## 9. Success criteria
- End-to-end scan → graded result → native-language voice report, **airplane mode ON**, on the iQOO phone.
- Classifier QWK improved over the 80% baseline on APTOS holdout.
- Grad-CAM overlay renders on real images.
- At least 3 Indian languages working end-to-end by voice.
- HackTracker shows on-device inference calls (proof), zero cloud calls in the core path.
