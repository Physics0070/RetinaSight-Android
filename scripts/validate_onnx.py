"""Evaluate the shipped ONNX grader against a labelled fundus dataset.

Two jobs, one script:

  1. Reproduce the internal APTOS validation split, to confirm that the file
     shipped in the APK is the checkpoint whose metrics we quote.
  2. Run the same model over an *external* dataset (IDRiD, Messidor) to get the
     generalisation number the project cannot otherwise produce.

The preprocessing is imported from the Omnikon training repo rather than
reimplemented here. That is deliberate: the whole point of the exercise is to
measure the model under the *serving* pipeline, and a second implementation
would only measure the drift between two copies of it.

The reported referral metrics use the expected grade

    E[g] = sum_i i * softmax(logits)_i

and the threshold the app actually ships (ReferralPolicy.REFERRAL_THRESHOLD),
not argmax and not the rounding point of 1.5. Quoting a threshold we do not
ship would make the number unfalsifiable.

Usage
-----
Reproduce the internal split (expect QWK 0.9324, referable sens 0.914):

    python scripts/validate_onnx.py --data <omnikon>/ml/data/aptos_456 \
        --split val --seed 143

External validation on IDRiD (expect it to be worse - say so):

    python scripts/validate_onnx.py --data <path>/idrid --split all
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

import numpy as np

DEFAULT_OMNIKON = Path(r"D:\SOHAM ALL\hackathons\Omnikon")
DEFAULT_MODEL = Path(__file__).resolve().parents[1] / (
    "RetinaSightApp/app/src/main/assets/dr-v2.onnx"
)

# Mirrors ReferralPolicy.kt. Kept as literals so a drift between the app and
# this script shows up as a disagreement rather than being silently imported.
REFERRAL_THRESHOLD = 1.15
ROUNDING_THRESHOLD = 1.5
CLASS_NAMES = ["no_dr", "mild", "moderate", "severe", "proliferative"]


def load_omnikon(root: Path):
    """Import the training repo's dataset + preprocessing modules."""
    if not root.exists():
        raise SystemExit(f"Omnikon repo not found at {root} (pass --omnikon)")
    sys.path.insert(0, str(root))
    sys.path.insert(0, str(root / "backend"))
    from app.ml import preprocessing  # type: ignore
    from ml.datasets import retinal_dataset  # type: ignore

    if preprocessing.opencv_available():
        print(
            "WARNING: OpenCV is installed, so resize uses INTER_AREA, but the\n"
            "         phone uses Pillow's antialiased BILINEAR. Numbers from\n"
            "         this run will not match the device. Uninstall cv2.",
            file=sys.stderr,
        )
    return retinal_dataset, preprocessing


def quadratic_kappa(actual: np.ndarray, predicted: np.ndarray, n_classes: int = 5) -> float:
    """Quadratic weighted kappa, written out rather than pulled from sklearn."""
    confusion = np.zeros((n_classes, n_classes), dtype=np.float64)
    for a, p in zip(actual, predicted):
        confusion[a, p] += 1

    weights = np.zeros((n_classes, n_classes), dtype=np.float64)
    for i in range(n_classes):
        for j in range(n_classes):
            weights[i, j] = ((i - j) ** 2) / ((n_classes - 1) ** 2)

    hist_actual = np.bincount(actual, minlength=n_classes).astype(np.float64)
    hist_pred = np.bincount(predicted, minlength=n_classes).astype(np.float64)
    expected = np.outer(hist_actual, hist_pred)

    # Both matrices must describe the same amount of probability mass.
    expected = expected * (confusion.sum() / expected.sum())

    denominator = (weights * expected).sum()
    if denominator == 0:
        return 0.0
    return float(1.0 - (weights * confusion).sum() / denominator)


def referral_metrics(labels: np.ndarray, expected_grade: np.ndarray, threshold: float) -> dict:
    """Sensitivity/specificity for 'referable' = true grade >= 2."""
    referable_truth = labels >= 2
    referred = expected_grade >= threshold

    tp = int(np.sum(referable_truth & referred))
    fn = int(np.sum(referable_truth & ~referred))
    fp = int(np.sum(~referable_truth & referred))
    tn = int(np.sum(~referable_truth & ~referred))

    return {
        "threshold": threshold,
        "sensitivity": tp / (tp + fn) if (tp + fn) else 0.0,
        "specificity": tn / (tn + fp) if (tn + fp) else 0.0,
        "precision": tp / (tp + fp) if (tp + fp) else 0.0,
        "true_positive": tp,
        "false_negative": fn,
        "false_positive": fp,
        "true_negative": tn,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--data", required=True, type=Path, help="dataset root")
    parser.add_argument("--model", type=Path, default=DEFAULT_MODEL)
    parser.add_argument("--omnikon", type=Path, default=DEFAULT_OMNIKON)
    parser.add_argument(
        "--split",
        choices=["all", "val"],
        default="all",
        help="'val' reproduces the internal split; 'all' scores every image "
        "(the right choice for an external set the model never trained on)",
    )
    parser.add_argument(
        "--seed",
        type=int,
        default=143,
        help="split seed. Run 124225 predates the split_seed field, so its "
        "split used the run seed 143, not 42.",
    )
    parser.add_argument("--val-fraction", type=float, default=0.15)
    parser.add_argument("--limit", type=int, default=0, help="debug: first N images")
    parser.add_argument("--json-out", type=Path, help="write metrics as JSON")
    args = parser.parse_args()

    retinal_dataset, preprocessing = load_omnikon(args.omnikon)
    import onnxruntime as ort

    samples = retinal_dataset.discover_samples(args.data)
    if args.split == "val":
        _, samples = retinal_dataset.stratified_split(
            samples, val_fraction=args.val_fraction, seed=args.seed
        )
    if args.limit:
        samples = samples[: args.limit]
    if not samples:
        raise SystemExit(f"no labelled images found under {args.data}")

    print(f"model   {args.model}")
    print(f"data    {args.data}  ({args.split}, n={len(samples)})")

    session = ort.InferenceSession(str(args.model), providers=["CPUExecutionProvider"])
    input_name = session.get_inputs()[0].name
    output_names = [o.name for o in session.get_outputs()]
    logits_index = output_names.index("logits") if "logits" in output_names else 0

    labels: list[int] = []
    predictions: list[int] = []
    expected_grades: list[float] = []

    for i, sample in enumerate(samples):
        data = Path(sample.path).read_bytes()
        processed = preprocessing.preprocess(data, input_size=(456, 456))
        outputs = session.run(None, {input_name: processed.tensor})
        logits = np.asarray(outputs[logits_index], dtype=np.float64).reshape(-1)

        shifted = logits - logits.max()
        probabilities = np.exp(shifted) / np.exp(shifted).sum()
        expected = float(np.sum(np.arange(len(probabilities)) * probabilities))

        labels.append(int(sample.label))
        # The graph rounds the expected grade; argmax would disagree with the
        # QWK we report, so round here too rather than taking the argmax.
        predictions.append(int(np.clip(round(expected), 0, 4)))
        expected_grades.append(expected)

        if (i + 1) % 50 == 0:
            print(f"  {i + 1}/{len(samples)}", flush=True)

    labels_array = np.asarray(labels)
    predictions_array = np.asarray(predictions)
    grades_array = np.asarray(expected_grades)

    confusion = np.zeros((5, 5), dtype=int)
    for a, p in zip(labels_array, predictions_array):
        confusion[a, p] += 1

    metrics = {
        "model": str(args.model),
        "data": str(args.data),
        "split": args.split,
        "seed": args.seed if args.split == "val" else None,
        "samples": len(samples),
        "accuracy": float((labels_array == predictions_array).mean()),
        "quadratic_kappa": quadratic_kappa(labels_array, predictions_array),
        "confusion_matrix": confusion.tolist(),
        "class_distribution": {
            name: int((labels_array == i).sum()) for i, name in enumerate(CLASS_NAMES)
        },
        "referable_at_shipped_threshold": referral_metrics(
            labels_array, grades_array, REFERRAL_THRESHOLD
        ),
        "referable_at_rounding_threshold": referral_metrics(
            labels_array, grades_array, ROUNDING_THRESHOLD
        ),
        "clinically_validated": False,
    }

    print()
    print(f"accuracy          {metrics['accuracy']:.4f}")
    print(f"quadratic kappa   {metrics['quadratic_kappa']:.4f}")
    print(f"confusion matrix  {metrics['confusion_matrix']}")
    for key in ("referable_at_shipped_threshold", "referable_at_rounding_threshold"):
        r = metrics[key]
        print(
            f"referable @ {r['threshold']:.2f}   "
            f"sens {r['sensitivity']:.4f}  spec {r['specificity']:.4f}  "
            f"missed {r['false_negative']}  false alarms {r['false_positive']}"
        )

    if args.json_out:
        args.json_out.write_text(json.dumps(metrics, indent=2), encoding="utf-8")
        print(f"\nwrote {args.json_out}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
