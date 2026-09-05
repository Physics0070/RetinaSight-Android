# Continuation prompt — paste into a fresh Claude chat (other device)

---

You are joining an in-progress hackathon project. Read this fully before doing anything, then help me continue.

**Context:** I'm at the iQOO Hackathon 2026 (phone-first, on-device track), on the iQOO 15 (Snapdragon 8 Elite Gen 5, Hexagon NPU, 16GB RAM). Limited hours. If I attach a file named `RetinaSight_AI_project.zip` (or `HANDOFF.md`), open it first — it has the full detail. Everything below is the summary.

**Project — RetinaSight AI:** a fully OFFLINE, ON-DEVICE Android app that screens a retina (fundus) image for diabetic retinopathy, grades severity 0–4 with confidence, shows a Grad-CAM heatmap of where the model looked, and explains the result BY VOICE in the user's native Indian language. Target users: rural India, no internet, possibly low literacy. It's a screening/triage aid, not a diagnosis.

**Why on-device (this is the scoring strategy — do not break it):** the hackathon rewards on-device builds heavily ("highest on-device build preferred for Top 10"), local models earn brownie points, on-device inference is free, and HackTracker logs inference calls as proof. Judging weights: End product 30% · Novelty 20% · Creative phone use 15% · Technical depth 15% · Office Kit 10% · Demo 10%. The core inference MUST run locally — never route it through the cloud/OpenRouter.

**Architecture (two on-device stages):**
1. Retina classifier — LiteRT/TFLite, INT8, QNN delegate on the Hexagon NPU. Preprocessing: circle-crop + Ben Graham/CLAHE (same at train & inference). Outputs grade 0–4, confidence, Grad-CAM.
2. Explanation — grounded STRICTLY on the classifier's structured output (a tightly-grounded small on-device LLM OR a deterministic template with real values). It must never invent clinical facts. Then on-device TTS speaks it in the chosen language.

**Tech stack (decided):** Native Android, Kotlin + Jetpack Compose (Material 3, MVVM). Inference via LiteRT/TFLite + QNN delegate (Qualcomm AI Hub for Snapdragon export). Voice via Android TextToSpeech first, AI4Bharat Indic-TTS/IndicF5 (ONNX) as offline fallback. Storage: Room + DataStore; local PDF export.

**Languages:** Hindi, Marathi, Tamil, Telugu, Kannada, Bengali, Gujarati, Malayalam, Punjabi, Odia, English (all covered by AI4Bharat on-device).

**Two hard rules:**
- NOTHING HARDCODED in the medical path: grade, confidence, heatmap, urgency, explanation all come from the real model via a single `InferenceEngine` interface. No canned results, no fake demo path. (UI button text localized via Android resource strings is fine — that's normal i18n, not the forbidden kind.)
- OFFLINE-FIRST: no internet needed for the core flow; show a "works offline" indicator.

**Model status:** we have a base DR classifier at ~80% accuracy (reported). Accuracy plan for the ML side: fine-tune (NOT full retrain) on the pre-merged Combined DR 21K + IDRiD, hold out APTOS for validation, use preprocessing + class-imbalance fix, judge on quadratic weighted kappa. (Do NOT full-retrain on 88K EyePACS — won't converge, and domain shift can lower APTOS accuracy.)

**Current state:** strategy + stack + datasets decided; project docs written; the app is greenfield (nothing coded yet); model exists on our ML machine. We're building the app UI first (make it presentable), then improving model accuracy.

**What I need from you now:** [FILL IN — e.g. "scaffold the Kotlin/Compose UI from the UI_UX_BUILD_PROMPT against a MockInferenceEngine" OR "write the fine-tuning + preprocessing pipeline in <PyTorch/TF>" OR "help me debug X"].

Before writing code: state your assumptions, keep it minimal, don't add features I didn't ask for, and define how we'll verify each step works. If something's ambiguous, ask me instead of guessing.
