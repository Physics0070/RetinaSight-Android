# Phone test sheet — parity check

10 original full-resolution APTOS fundus photos are on the device at
`/sdcard/Pictures/RetinaTest/` and are indexed, so they appear in the photo picker.

## How to test

RetinaSight AI → **Scan Eye** → **Choose from phone** → pick an image → read the grade.

## What to compare against

These are the predictions the **desktop** ONNX produced from the same original
files, through the same preprocessing. The phone should match every row.

| # | File | True label | Desktop prediction | Confidence |
|---|------|-----------|--------------------|-----------|
| 01 | `01_true-no_dr.png` | No signs found | **No signs found** | 0.951 |
| 02 | `02_true-no_dr.png` | No signs found | **No signs found** | 0.929 |
| 03 | `03_true-mild.png` | Mild changes | **Mild changes** | 0.995 |
| 04 | `04_true-mild.png` | Mild changes | **Mild changes** | 0.999 |
| 05 | `05_true-moderate.png` | Moderate changes | **Moderate changes** | 0.951 |
| 06 | `06_true-moderate.png` | Moderate changes | **Moderate changes** | 0.883 |
| 07 | `07_true-severe.png` | Severe changes | **Severe changes** | 0.991 |
| 08 | `08_true-severe.png` | Severe changes | **Severe changes** | 0.971 |
| 09 | `09_true-proliferative.png` | Advanced changes | **Advanced changes** | 0.995 |
| 10 | `10_true-proliferative.png` | Advanced changes | **Advanced changes** | 0.974 |

Source images range from 1050x1050 to 3388x2588, so this also exercises the
crop-and-resize path across very different input sizes.

## What this test does and does not prove

**Does prove:** that the Kotlin preprocessing + ONNX Runtime on the phone
reproduce the Python pipeline. That is the real integration risk — a
preprocessing mismatch produces confident, wrong answers that look fine.

**Does NOT prove accuracy.** These 10 files were picked from the full APTOS set
with no held-out guarantee, so the model has probably seen them. 10/10 here is
expected and is not evidence of performance. The accuracy claim remains
**QWK 0.93** from the original 3-seed validation.

## If a row disagrees

A one-grade disagreement on the 0.883 image (#06) is plausible - it is the least
confident of the ten. Anything larger, or several rows differing, points at the
preprocessing port rather than the model, most likely the resize filter.
Note which files differ and send them to me.
