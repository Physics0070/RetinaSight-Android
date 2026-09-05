# UI/UX Build Prompt — RetinaSight AI (paste this into your AI builder on the other device)

You are building the **UI/UX for RetinaSight AI**, a fully **offline, on-device Android app** that screens a retina (fundus) image for diabetic retinopathy and explains the result by voice in the user's native Indian language. Primary users are **rural people with little/no internet and possibly low literacy.** Build the UI now against a **mock inference interface**; the real on-device model will be wired in later. Do not fake medical results in the shipped path — the mock is only a placeholder to be swapped for the real model.

## Non-negotiable rules
1. **Nothing hardcoded in the medical path.** Grade, confidence, heatmap, urgency, and explanation must all come from a single `InferenceEngine` interface (mocked for now, real model later). Never hardcode a diagnosis or a fixed demo result into a screen.
2. **Offline-first.** No feature may require internet. Do not request the INTERNET permission for the core flow. Show a persistent, reassuring "Works offline" indicator.
3. **Localization is via Android resource strings** (`values-hi/`, `values-mr/`, `values-ta/`, `values-te/`, `values-kn/`, `values-bn/`, `values-gu/`, `values-ml/`, `values-pa/`, `values-or/`, `values/` for English). This is correct i18n and is expected — it is NOT the "hardcoding" we forbid. No user-facing string literal inside a composable.
4. **Voice-first & low-literacy friendly.** Every result must be readable AND spoken aloud. Lead with icon + color + voice; text is secondary.
5. **Fast.** Target sub-second perceived response; preload heavy things; show progress, never a frozen screen.

## Tech stack (match this exactly)
- Kotlin + Jetpack Compose (Material 3).
- MVVM: `ViewModel` + `StateFlow`; screens are stateless composables driven by UI state.
- `InferenceEngine` interface (abstraction over the model) with a `MockInferenceEngine` for now.
- On-device TTS: Android `TextToSpeech` first; architit so an AI4Bharat ONNX TTS fallback can be plugged in.
- Local persistence: Room (scan history) + DataStore (language preference).
- Local PDF export for the report.

## Target languages
Hindi, Marathi, Tamil, Telugu, Kannada, Bengali, Gujarati, Malayalam, Punjabi, Odia, English. Each must render in its native script and be selectable at first run and in Settings.

## Users & context (design for this)
- Outdoor/bright light → high contrast, large type, no thin gray text.
- May not read → big icons, color coding, voice for everything.
- Older/basic-phone users → large tap targets (min 48dp), few steps, no clutter.
- No internet, low trust → explicit offline badge; explain what the app is doing.

## Screens
1. **Language select (first run):** grid of large buttons, each showing the language **in its own script** (हिन्दी, मराठी, தமிழ், తెలుగు, ಕನ್ನಡ …). Tapping speaks the language name aloud. Persists choice.
2. **Home:** one giant primary button "**Scan Eye**" (icon + label + spoken on focus) and a secondary "History". Minimal else. Offline badge visible.
3. **Capture:** camera preview with a **circular alignment guide** for the fundus + short voice guidance; plus "Upload from gallery." Big shutter button.
4. **Analyzing:** on-device progress animation, "Analyzing on your phone — no internet needed" (localized + spoken). Never blank.
5. **Result:** 
   - Large **severity band**, color-coded across the 5 DR grades (e.g., green→No DR, up to red→Proliferative), with an icon and the grade name in native language.
   - **Confidence** shown simply (e.g., a filled bar + "high/medium/low" spoken).
   - **Grad-CAM heatmap toggle** over the eye image.
   - **Plain-language explanation** in native language, with a prominent **▶ Listen** button (auto-plays on first view).
   - **Referral urgency** as a clear, color-coded, spoken line.
   - Actions: **Save** (local PDF) and **Save to history**. (No cloud share in core flow.)
6. **History:** list of past scans with date, grade chip, thumbnail; tap → result; simple **progression view** (grade over time).
7. **Settings:** language, TTS voice test, offline model status, about (screening aid, not a diagnosis — disclaimer, localized).

## Design system
- **Color:** one calm primary; a 5-step severity scale (green / lime / amber / orange / red) reused everywhere a grade appears. WCAG AA contrast minimum. Provide light theme (rural daytime use); dark optional.
- **Type:** large base size (min 16sp body, 20sp+ for key labels); a font stack that renders all target Indian scripts (e.g., Noto Sans + Noto Sans Devanagari/Tamil/Telugu/Kannada/Bengali/Gujarati/Malayalam/Gurmukhi/Oriya).
- **Icons:** consistent, filled, meaningful without text.
- **Motion:** minimal, only to show progress/state; no decorative animation that delays results.
- **Layout:** one primary action per screen; generous spacing; thumb-reachable controls.

## The InferenceEngine contract (mock now, real later)
Define an interface the UI depends on, so the real model drops in without UI changes:
```kotlin
data class RetinaResult(
    val grade: Int,            // 0..4
    val gradeLabelKey: String, // resource key, not a literal
    val confidence: Float,     // 0f..1f
    val heatmapBitmap: Bitmap?,// Grad-CAM overlay
    val urgencyKey: String,    // resource key for referral urgency
    val explanationText: String// generated/grounded text in selected language (from model, not hardcoded)
)
interface InferenceEngine { suspend fun analyze(image: Bitmap, languageTag: String): RetinaResult }
```
`MockInferenceEngine` returns clearly-labeled placeholder data for UI development only. The production `OnDeviceInferenceEngine` will implement the same interface using the real model + Grad-CAM + on-device explanation.

## Acceptance criteria
- App runs with no internet and requests no network permission for the core flow.
- Language switch changes every visible string and the spoken output; nothing stays English by accident.
- Result screen speaks the explanation aloud automatically and via a Listen button.
- Grad-CAM toggle works.
- Swapping `MockInferenceEngine` → real engine requires zero screen changes.
- All target scripts render correctly; no tofu (□) glyphs.
- No user-facing string literal exists inside composables (all via resources).
- 48dp+ tap targets; AA contrast; readable in bright light.
```
Build the full Compose project skeleton, all screens, the design system, the localization resource files (start with Hindi + Marathi + English, structure the rest), the ViewModels, and the MockInferenceEngine. Then stop for review before wiring the real model.
```
