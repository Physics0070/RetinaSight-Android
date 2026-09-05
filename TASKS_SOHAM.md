# Package A — make the power and thermal numbers real

**Owner:** Soham (`Physics0070`) · **Branch:** `bench/spacing`

## The problem

`exports/retinasight_benchmark_20260905_230250.csv` is a clean run: 100 real
on-device inferences on a vivo I2501, unplugged. The latency is trustworthy —
p50 113.9 ms, p90 125.6 ms, p99 132.6 ms.

Everything else in that file is not what it looks like. The last row reads
`100,11.77,NONE,0.65,33.10`: the whole "camp" took **11.77 seconds**. So

- `power_net_inference,7805.09,mW` is a 12-second sustained-load sample
- `patients_per_charge,321` is that sample extrapolated to a full battery
- `battery_temp_start` and `battery_temp_end` are both 33.10 °C because the
  phone was under load for twelve seconds, not because it resists a camp
- `throttle_onset_screening,none` says nothing at all about a real camp

A judge who opens the CSV will notice the elapsed column. Better we fix it.

## What to build

`BenchmarkRunner` runs inferences back to back. Add a **duty cycle**: a
configurable idle gap between screenings so the run occupies real time, and the
thermal and battery sampling see something other than a flat line.

1. Add `secondsPerPatient` to the benchmark config (default 30 — the same
   number `benchmark_duty_cycle` already shows the user).
2. Between screenings, idle for `secondsPerPatient - inferenceTime`. Keep the
   screen on and the app foregrounded; the point is to measure the phone as it
   actually sits during a camp.
3. Sample battery level, temperature and thermal status on a wall-clock
   schedule rather than per screening, so the curve has real time on the x-axis.
4. Offer a short mode for demos — 20 patients at 30 s is 10 minutes, which is
   about the longest anyone will wait at a stall.

## Definition of done

- A run of at least 20 patients whose elapsed time is minutes, not seconds.
- Battery percentage actually drops during it, so energy per screening comes
  from a measured delta instead of an extrapolation.
- `BENCHMARK_METHODOLOGY.md` states the duty cycle used and which figures are
  measured versus derived.
- The three docs stop hedging: replace "over a 12-second sustained-load sample"
  with the real figure, or keep the hedge if the numbers still do not support it.

## Do not

- Do not run it plugged in. The runner refuses while charging, deliberately —
  charge current swamps the measurement.
- Do not delete the 2026-09-05 CSV. It is honest evidence for the latency
  numbers; the new run supplements it rather than replacing it.
