# RetinaSight AI — Field Benchmark Methodology

The USP is not "we run offline." Medios already does that. The USP is that we run offline,
**open, hardware-agnostic — and we publish the field numbers nobody publishes.**

This document is the defensible answer to "how did you measure that?"

---

## What we publish

| Metric | Unit | Why a health officer cares |
|---|---|---|
| Latency p50 / p90 / p99 | ms | Queue throughput at a camp; p99 is what the last patient in line feels |
| Idle / busy / **net** power | mW | Net is inference cost, isolated from the phone merely being on |
| Energy per screening | µAh and mWh | The unit that converts to patients per charge |
| **Screenings per charge** | count | The field metric nobody reports. On a 7000 mAh cell |
| Thermal curve + throttle onset | °C, status vs. screening index | Whether the phone survives a 100-patient camp |

## The competitive frame (verified)

Medios (Remidio) is offline, smartphone-based, reports ~93% sensitivity / 92.5% specificity,
and generates a report in **~20 seconds** — but it is **proprietary and tied to Remidio's own
Fundus-on-Phone hardware**. Sources: [Indian J Ophthalmol / PubMed](https://pubmed.ncbi.nlm.nih.gov/31957735/),
[Ophthalmology Times](https://www.ophthalmologytimes.com/view/offline-smartphone-ai-accurately-detects-dr-glaucoma-and-amd-in-real-world-study).

So the honest claim is: **offline AND open AND hardware-agnostic, with published field
telemetry.** Do NOT claim we beat their accuracy — we have not run their test set. Latency is
a fair comparison only if we state that their ~20 s covers capture-to-report on their hardware,
while ours is inference latency on the NPU. State the scope, then the number.

---

## How each number is obtained

All of it comes from public Android APIs. No root, no server, no external meter.

### Latency
`SystemClock.elapsedRealtimeNanos()` around `InferenceEngine.analyze()`. Percentiles by
nearest-rank on the sorted sample. The model is warmed up first, so cold-start is excluded
and reported separately if needed.

### Power
- `BatteryManager.BATTERY_PROPERTY_CURRENT_NOW` → µA
- `EXTRA_VOLTAGE` from `ACTION_BATTERY_CHANGED` → mV
- `power_mW = µA × mV / 1e6`

Android exposes **whole-device** draw only — there is no per-app or per-chip rail. So we
measure a **5-second idle baseline** first and report `net = busy − idle`. That subtraction is
what makes the number attributable to inference rather than to the screen being on.

### Energy and screenings per charge — two independent methods

**Method A (primary): fuel gauge.**
`BATTERY_PROPERTY_CHARGE_COUNTER` (µAh remaining) sampled before and after the run.
```
µAh_per_screening   = (charge_start − charge_end) / N
screenings_per_charge = battery_capacity_µAh / µAh_per_screening
```

**Method B (cross-check): integrate power over wall time.**
```
mWh_per_screening = (net_power_mW × run_hours) / N
screenings_per_charge = (capacity_mAh × mean_voltage_V) / mWh_per_screening
```

Both are reported. **If they diverge by more than 30%, the app says so in a warning** rather
than printing a confident wrong number. This is the single most important credibility feature
in the whole harness.

### Thermal
- `PowerManager.getCurrentThermalStatus()` (API 29+) → NONE…SHUTDOWN
- `PowerManager.getThermalHeadroom(0)` (API 30+) → 0.0 cool … 1.0 = SEVERE throttling threshold
- `EXTRA_TEMPERATURE` → battery °C (tenths of a degree)

Throttle onset = first screening where status reaches `MODERATE` or worse.

---

## Known limitations (state these before a judge finds them)

1. **Whole-device power only.** Mitigated by idle-baseline subtraction, not eliminated.
2. **OEM unit/sign variance.** `CURRENT_NOW` is documented in µA but some OEMs report mA and
   some invert the sign. The code normalises magnitude and rescales values that are implausibly
   small for µA, and raises a warning so it gets checked once on the real device.
   **Do this calibration on the iQOO 15 before the demo.**
3. **`getThermalHeadroom` returns NaN** if unsupported or polled faster than ~1 Hz. We cache at
   1 Hz and fall back to thermal status + battery temperature if it is unavailable.
4. **Must run on battery.** The harness refuses to start while charging, because the charge
   counter moves the wrong way and every energy number would be meaningless.
5. **Fuel gauge resolution.** Over too few screenings the charge counter may not move at all.
   That is why the default camp is 100 — and the app warns if the delta was unmeasurable.
6. **Synthetic stand-in image** is used if no fundus photo has been captured. Latency at fixed
   input resolution is essentially image-independent after resize, but say so rather than imply
   the run used real patient images.

---

## How to run it

Settings → **Field benchmark** → choose 50 / 100 / 200 → **Run camp simulation**.
Unplug the charger first. Export CSV writes to the app's external files directory,
reachable over USB, ready to paste into the deck.

## Before you publish numbers

- [ ] Run once with the **real** TFLite model on the NPU, not the mock
- [ ] Unplugged, screen on, airplane mode on
- [ ] Confirm the two energy methods agree within 30%
- [ ] Sanity-check `CURRENT_NOW` units once on the iQOO 15
- [ ] Run 100 screenings, not 50, so the fuel gauge registers a real delta

---

# MEASURED RESULTS — first clean run

**Run:** 2026-09-05 23:09:18 · vivo I2501 (iQOO 15), Android 16 / SDK 36 ·
unplugged, 76% battery, 33.2 °C start · 100 screenings · NNAPI (NPU/GPU)
**Raw CSV:** `exports/retinasight_benchmark_20260905_230918.csv`

## Latency — 100 real inferences, trustworthy

| | ms |
|---|---|
| mean | 103.84 |
| **p50** | **101.38** |
| p90 | 109.65 |
| p99 | 116.12 |
| min | 92.33 |
| max | 284.30 |

The max is the first inference (warm-up); the distribution is otherwise tight,
p99 within 15% of the median.

## Power and energy — real, but from a 10-second window

| | value |
|---|---|
| idle baseline | 1214.58 mW |
| during screening | 12105.51 mW |
| net (inference) | 10890.93 mW |
| energy per screening | 90.00 µAh · 0.37 mWh |
| measured battery capacity | 6062 mAh (7000 mAh spec) |
| continuous screening | 2.05 h |
| patients per charge | 245, assuming 30 s of phone time per patient |
| inference-bound upper limit | 70,901 back-to-back |

## Thermal — no throttling, over 10.4 seconds

Thermal status `NONE` for all 100 screenings, headroom 0.61 → 0.62, battery
temperature 33.2 → 33.2 °C.

## What this run does NOT show

State this before a judge finds it:

- The 100 screenings completed in **10.38 s** wall time. The runner has no
  per-patient spacing — it is a back-to-back inference loop, not a camp.
- Therefore "no throttling" means *no throttling under 10 seconds of sustained
  load*. It is not evidence about a four-hour camp. The battery temperature
  not moving at all is the tell.
- `patients_per_charge = 245` is an extrapolation: measured energy per
  inference, multiplied out under a 30 s/patient assumption. The assumption is
  declared in the CSV, not hidden.
- The fuel gauge updates far slower than 10 Hz, so the 100 power samples taken
  across 10 s are correlated. The energy figure is the more reliable of the two
  power methods because it reads the charge counter across the whole run.

To claim a camp-duration thermal result, the runner needs a spaced mode that
holds the phone under load for tens of minutes. That has not been run.
