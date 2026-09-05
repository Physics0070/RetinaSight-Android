# Package B — native review of the Dravidian locales

**Owner:** Ruturaj (`ruturajnalbalwar-arch`) · **Branch:** `i18n/dravidian`

## The problem

Four locales ship with complete string sets that **no native speaker has read**:

| Locale | Language | File |
|---|---|---|
| `ta` | Tamil | `RetinaSightApp/app/src/main/res/values-ta/strings.xml` |
| `te` | Telugu | `RetinaSightApp/app/src/main/res/values-te/strings.xml` |
| `kn` | Kannada | `RetinaSightApp/app/src/main/res/values-kn/strings.xml` |
| `ml` | Malayalam | `RetinaSightApp/app/src/main/res/values-ml/strings.xml` |

179 strings each. They compile, they render correctly on the device, and the
grammar is plausible. That is not the same as being safe. These strings tell a
patient how urgently to see a doctor; a wrong register or a softened verb is a
clinical error with a straight face.

## Review order — highest harm first

Do not start at line 1. Start where a mistranslation hurts someone.

1. **`urgency_*`** (5 strings) — "within 3 months" versus "as soon as possible".
   If these blur together the whole referral pathway collapses. Check that each
   step reads as clearly more urgent than the one before it.
2. **`grade_*_desc`** (5) — must describe findings without diagnosing.
3. **`result_disclaimer`**, **`consent_body`**, **`consent_checkbox`** — consent
   is a legal and ethical gate, not UI copy. `consent_body` must clearly say a
   photo is taken, a computer on this phone checks it, and a doctor confirms.
4. **`explain_*`** (5) — especially `explain_borderline_referral`, which has to
   explain why the app refers an eye the grade calls mild, without frightening.
5. **`speak_*`** (5) — these are *heard*, not read. Read them aloud. If it does
   not sound like a person speaking, rewrite it.
6. Everything else — quality gate, benchmark, settings, sync.

## How to work

Edit the `values-xx/strings.xml` files directly. Then, before every push:

```bash
python scripts/check_translations.py
```

It fails if a key goes missing or a format specifier (`%1$d`, `%1$s`) is dropped
or reordered — a dropped specifier is a crash at format time, not a typo.

Check on a real device too, in each language: long Dravidian compounds wrap and
can push buttons off screen. Watch `urgency_immediate` and `capture_adapter_note`.

## Definition of done

- Every one of the four files read end to end by someone who speaks it.
- A line in `HANDOFF.md` recording who reviewed which language and when, so the
  "11 languages" claim has a name behind it instead of a hope.
- `check_translations.py` green.

## If you are unsure

Leave the English. A locale that falls back to English is honest; a confident
mistranslation of "see a doctor immediately" is not.
