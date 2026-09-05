# Package C — Indo-Aryan review, and a number that survives a different camera

**Owner:** Chitrangad (`chitrangad-ram-sapate`) · **Branch:** `i18n/indo-aryan`

Two parts. Part 1 is the same shape as Ruturaj's package; part 2 is the single
strongest thing anyone could add to the pitch tonight.

---

## Part 1 — native review of four locales

| Locale | Language | File |
|---|---|---|
| `bn` | Bengali | `RetinaSightApp/app/src/main/res/values-bn/strings.xml` |
| `gu` | Gujarati | `RetinaSightApp/app/src/main/res/values-gu/strings.xml` |
| `pa` | Punjabi | `RetinaSightApp/app/src/main/res/values-pa/strings.xml` |
| `or` | Odia | `RetinaSightApp/app/src/main/res/values-or/strings.xml` |

179 strings each, machine-drafted, unreviewed. Same review order as
[TASKS_RUTURAJ.md](TASKS_RUTURAJ.md): `urgency_*` first, then `grade_*_desc`,
then consent and disclaimer, then `explain_*`, then the spoken `speak_*` lines.

Bengali-specific: the drafts use Bengali digits (১২ মাস) in `urgency_*` while the
other locales use Western digits. Pick one and be consistent — mixed numerals in
a referral instruction are exactly the sort of thing that gets misread.

Run `python scripts/check_translations.py` before every push.

---

## Part 2 — external validation

### Why this matters more than it sounds

Every accuracy number the project quotes comes from one dataset. QWK 0.9324,
83.3% accuracy, 98.2% sensitivity at the new referral threshold — all of it is a
held-out split of **APTOS 2019**: same cameras, same clinics, same country, same
capture protocol. It is a genuine held-out split, not contaminated (the split was
reproduced exactly, confusion matrix cell for cell). But it says nothing about a
camera the model has never seen, and domain shift in fundus photography is large.

One number on a second dataset is worth more to a jury than another point of QWK
on this one.

### What to do

1. Get **IDRiD** (516 images, graded 0–4, Indian, different camera) or
   **Messidor-2**. IDRiD is smaller and faster to run.
2. Run `dr-v2.onnx` over it with the *exact* serving preprocessing — crop to the
   retinal disc at luminance > 18 with 2% padding, resize to 456 px with
   Pillow's antialiased bilinear, ImageNet normalise. Do **not** add Ben Graham
   or a circle mask; the training distribution had neither.
   `ml/datasets/retinal_dataset.py` and `backend/app/ml/preprocessing.py` in the
   Omnikon project already do all of this — import them rather than reimplementing.
3. Report QWK, accuracy, and referable sensitivity/specificity **at the shipped
   threshold of 1.15** on the expected grade, not at argmax and not at 1.5.
4. Write it up in `HANDOFF.md` and `PITCH_PACK.md` as external validation,
   clearly separate from the APTOS numbers.

### Expect it to be worse

It will very likely score below 0.93, and that is fine — say so plainly. "0.93
internal, 0.8x external on a camera it has never seen" is a far stronger and more
credible claim than one number with no generalisation evidence at all. A jury
that catches you hiding the gap will not believe the rest either.

### Do not

- Do not retrain, and do not fine-tune on IDRiD. There is no time, it will not
  converge usefully, and it destroys the point of an external test set.
- Do not swap the shipped model based on one external run.
