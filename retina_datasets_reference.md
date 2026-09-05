# RetinaSight AI — Dataset & 10-Hour Accuracy Reference

_iQOO Hackathon 2026. Points come from what you build in these hours over the pre-built model — so the accuracy delta + on-device deployment is what's scored._

## Datasets (ranked for a 10-hour DR accuracy push)

| # | Dataset | Size | Labels | Why now | Link |
|---|---------|------|--------|---------|------|
| 1 | Combined DR (APTOS+IDRiD+Messidor+EyePACS) | ~21,000 | 5-class DR (0–4) | Pre-merged, one label scheme → almost no wrangling | https://www.kaggle.com/datasets/harsha1289/combined-dr-dataset-aptosidridmessidoreyepacs |
| 2 | IDRiD | 516 | 5-class DR + lesion masks | Indian-population fundus (domain match); lesion Grad-CAM | https://ieee-dataport.org/open-access/indian-diabetic-retinopathy-image-dataset-idrid |
| 3 | EyePACS (DR Detection 2015) | ~88,000 (35,126 train) | 5-class DR | Best source of RARE classes (severe/proliferative) | https://www.kaggle.com/c/diabetic-retinopathy-detection |
| 4 | APTOS 2019 | 3,662 | 5-class DR | Keep as clean held-out validation set | https://www.kaggle.com/c/aptos2019-blindness-detection  (JPG: https://www.kaggle.com/datasets/subhajeetdas/aptos-2019-jpg) |
| 5 | Messidor-2 | 1,748 | Referable DR | High-quality generalization test for jury | https://www.adcis.net/en/third-party/messidor2/ |

### Multi-disease expansion (only if going beyond DR)
- RFMiD — 3,200 img, 46 conditions, multi-label — https://ieee-dataport.org/open-access/retinal-fundus-multi-disease-image-dataset-rfmid
- ODIR-5K — 5,000 patients, 8 classes (Normal/Diabetes/Glaucoma/Cataract/AMD/Hypertension/Myopia/Other) — https://www.kaggle.com/datasets/andrewmvd/ocular-disease-recognition-odir5k

## Download (accept competition rules on the site first)
```bash
kaggle datasets download -d harsha1289/combined-dr-dataset-aptosidridmessidoreyepacs
kaggle datasets download -d andrewmvd/ocular-disease-recognition-odir5k
kaggle competitions download -c diabetic-retinopathy-detection   # EyePACS
kaggle competitions download -c aptos2019-blindness-detection
```

## The real 10-hour accuracy plan (more data is NOT lever #1)

Full retrain on 88K EyePACS won't converge in 10h AND domain shift can LOWER your APTOS accuracy. Do this instead, in order:

1. **Preprocessing = biggest cheap win.** Circle-crop black borders + Ben Graham method (subtract local-average color) or CLAHE. Apply at train AND inference.
2. **Fix class imbalance, don't add raw volume.** Class weights or oversample severe/proliferative. Pull ONLY rare-class images from EyePACS if you need more.
3. **Fine-tune, don't retrain.** Start from existing weights, unfreeze top layers, train on Combined 21K + IDRiD, validate on held-out APTOS.
4. **Metric = quadratic weighted kappa** (not plain accuracy).
5. **Test-time augmentation** for the final score. Keep **Grad-CAM** — for a medical jury, interpretability reads as accuracy.

Recommended config: Combined 21K + IDRiD → fine-tune → Ben Graham preprocessing → rare-class oversampling → QWK on APTOS holdout.
