"""Export CalorieK to ONNX (fp32 and dynamic int8) the way it would ship on Android, then re-score both
on a test set so we know what quantisation costs.

    python -I export_onnx.py --bench <dir> --datasets hellofresh mmfood

Writes <bench>/onnx/caloriek_fp32.onnx and caloriek_int8.onnx, and pred_<dataset>_caloriek-onnx-{fp32,int8}.csv.
The ONNX graph takes the already-preprocessed (1, 3, 224, 224) float tensor: SigLIP's preprocessing is a plain
bilinear resize to 224 x 224 and (x / 255 - 0.5) / 0.5, simple to reproduce in Kotlin.
"""
from __future__ import annotations

import argparse
import csv
import sys
import time
from pathlib import Path

import numpy as np
import torch

sys.path.insert(0, str(Path(__file__).resolve().parent))
import models  # noqa: E402


class Wrapped(torch.nn.Module):
    def __init__(self, ck: models.CalorieK):
        super().__init__()
        self.vision, self.head = ck.vision, ck.head

    def forward(self, pixel_values):
        out = self.vision(pixel_values=pixel_values)
        return self.head(out.pooler_output)        # (1, 5): kcal, mass_g, protein_g, carb_g, fat_g


def preprocess(path: str) -> np.ndarray:
    """Same as the SigLIP2 processor: RGB, bilinear 224 x 224 (aspect not kept), scale to [-1, 1]."""
    from PIL import Image
    with Image.open(path) as im:
        im = im.convert("RGB").resize((224, 224), Image.BILINEAR)
    x = np.asarray(im, dtype=np.float32) / 255.0
    return ((x - 0.5) / 0.5).transpose(2, 0, 1)[None]


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--bench", required=True, type=Path)
    ap.add_argument("--datasets", nargs="+", default=["hellofresh"])
    a = ap.parse_args()
    out_dir = a.bench / "onnx"
    out_dir.mkdir(exist_ok=True)
    fp32, int8 = out_dir / "caloriek_fp32.onnx", out_dir / "caloriek_int8.onnx"

    ck = models.CalorieK(a.bench / "models" / "caloriek")
    model = Wrapped(ck).eval()
    if not fp32.exists():
        torch.onnx.export(model, (torch.zeros(1, 3, 224, 224),), str(fp32), input_names=["pixel_values"],
                          output_names=["nutrition"], opset_version=18, dynamo=False)
    if not int8.exists():
        from onnxruntime.quantization import QuantType, quantize_dynamic
        quantize_dynamic(str(fp32), str(int8), weight_type=QuantType.QInt8)
    for p in (fp32, int8):
        print(f"{p.name}: {p.stat().st_size / 1e6:.0f} MB")

    # Check my preprocessing matches the HF processor the PyTorch runs used.
    import onnxruntime as ort
    sess = {k: ort.InferenceSession(str(p), providers=["CPUExecutionProvider"]) for k, p in (("fp32", fp32), ("int8", int8))}
    for ds in a.datasets:
        with open(a.bench / "data" / ds / "manifest.csv", encoding="utf-8") as f:
            rows = list(csv.DictReader(f))
        for k, s in sess.items():
            path = a.bench / "out" / f"pred_{ds}_caloriek-onnx-{k}.csv"
            with open(path, "w", newline="", encoding="utf-8") as f:
                w = csv.writer(f)
                w.writerow(["id", "kcal", "mass_g", "protein_g", "carb_g", "fat_g", "alt_kcal", "seconds", "detail", "error"])
                for r in rows:
                    t = time.time()
                    y = s.run(None, {"pixel_values": preprocess(r["path"])})[0][0]
                    w.writerow([r["id"], *[round(float(v), 2) for v in y], "", round(time.time() - t, 3), "", ""])
            print(f"{ds} {k} done")


if __name__ == "__main__":
    main()
