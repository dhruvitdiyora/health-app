"""Run models over a test set's manifest and write <bench>/out/pred_<dataset>_<model>.csv.

    python -I run.py --bench <dir> --dataset mmfood --models calorie-lens caloriek

Resumes: images already in the output file are skipped. Each row records the per-image CPU latency.
"""
from __future__ import annotations

import argparse
import csv
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))   # -I drops the script dir from sys.path
import models  # noqa: E402

OUT_FIELDS = ["id", "kcal", "mass_g", "protein_g", "carb_g", "fat_g", "alt_kcal", "seconds", "detail", "error"]


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--bench", required=True, type=Path)
    ap.add_argument("--dataset", required=True)
    ap.add_argument("--models", nargs="+", required=True)
    a = ap.parse_args()

    with open(a.bench / "data" / a.dataset / "manifest.csv", encoding="utf-8") as f:
        rows = list(csv.DictReader(f))
    (a.bench / "out").mkdir(exist_ok=True)
    for name in a.models:
        out_path = a.bench / "out" / f"pred_{a.dataset}_{name}.csv"
        done = set()
        if out_path.exists():
            with open(out_path, encoding="utf-8") as f:
                done = {r["id"] for r in csv.DictReader(f)}
        todo = [r for r in rows if r["id"] not in done]
        if not todo:
            print(f"{name} on {a.dataset}: already complete"); continue
        model = models.load(name, a.bench)
        new_file = not out_path.exists()
        with open(out_path, "a", newline="", encoding="utf-8") as f:
            w = csv.DictWriter(f, fieldnames=OUT_FIELDS, extrasaction="ignore")
            if new_file:
                w.writeheader()
            for i, r in enumerate(todo, 1):
                t = time.time()
                try:
                    p = model.predict(r["path"])
                    w.writerow(dict(id=r["id"], seconds=round(time.time() - t, 3), error="", **p))
                except Exception as e:                       # record and keep going: one bad image shouldn't stop a run
                    w.writerow(dict(id=r["id"], seconds=round(time.time() - t, 3), error=repr(e)[:200]))
                f.flush()
                print(f"\r{name} {a.dataset}: {i}/{len(todo)}", end="", flush=True)
        print()


if __name__ == "__main__":
    main()
