# Work split — three packages

Three roughly equal packages, split so nobody blocks anybody. Each one is
independently committable: different files, no shared merge surface.

| Owner | Package | Files touched | Blocking? |
|---|---|---|---|
| Soham (`Physics0070`) | A — Benchmark methodology | `core/benchmark/` | Blocks the power claim in the pitch |
| Ruturaj (`ruturajnalbalwar-arch`) | B — Dravidian language review | `values-ta,te,kn,ml/` | Blocks the "11 languages" claim |
| Chitrangad (`chitrangad-ram-sapate`) | C — Indo-Aryan review + external validation | `values-bn,gu,pa,or/`, `ml/` | Blocks the "11 languages" and generalisation claims |

Full briefs: [TASKS_SOHAM.md](TASKS_SOHAM.md) · [TASKS_RUTURAJ.md](TASKS_RUTURAJ.md) ·
[TASKS_CHITRANGAD.md](TASKS_CHITRANGAD.md)

---

## Why these three

Every one of them closes a gap where the pitch currently claims more than the
evidence supports. That is the only ranking that matters this close to judging.

1. **The power numbers are extrapolated from twelve seconds.** The 100-screening
   run finished in 11.77 s of back-to-back inference. Latency is real; watts,
   patients-per-charge and the thermal curve are not camp measurements.
2. **Eight of eleven languages are machine-drafted and unreviewed.** These
   strings tell a patient how urgently to see a doctor. A mistranslation here is
   a clinical error, not a typo.
3. **Validation never leaves APTOS.** QWK 0.93 is one dataset and one set of
   cameras. Nothing yet shows the model survives a different camera.

## Ground rules

- Branch per package: `bench/spacing`, `i18n/dravidian`, `i18n/indo-aryan`.
- Run `python scripts/check_translations.py` before pushing any locale change.
  It gates key completeness and format-specifier match, and exits non-zero.
- Never quote a number that has not been measured. If you change what a number
  means, change it everywhere it appears — `HANDOFF.md`, `PITCH_PACK.md`,
  `TECH_STACK_EXPLAINED.md`.
- The diagnostic path stays offline. Nothing in these packages should add a
  network call to screening.
