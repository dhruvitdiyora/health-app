"""Build fixed, reproducible test sets for comparing food-photo -> calorie models.

Each subcommand downloads a sample into <bench>/data/<name>/ and writes <bench>/data/<name>/manifest.csv
with one row per image: id, path, kcal, mass_g, protein_g, carb_g, fat_g, group, label_quality.
A missing label is left empty (for example the Food Portion Benchmark only has weights).

    python -I datasets.py --bench <dir> n5k      --n 100   # Nutrition5k official test split (weighed, cafeteria)
    python -I datasets.py --bench <dir> mmfood   --n 300   # MM-Food-100K homemade + restaurant photos (estimated labels)
    python -I datasets.py --bench <dir> fpb      --n 200   # Food Portion Benchmark val split (weighed grams only)

Sampling is seeded, so the same command always picks the same images.
"""
from __future__ import annotations

import argparse
import csv
import io
import json
import random
import re
import sys
import urllib.request
from pathlib import Path

SEED = 2026
UA = {"User-Agent": "health-app-food-bench/0.1 (personal research)"}
FIELDS = ["id", "path", "kcal", "mass_g", "protein_g", "carb_g", "fat_g", "group", "label_quality"]


def fetch(url: str, timeout: int = 60) -> bytes:
    with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=timeout) as r:
        return r.read()


def save_image(data: bytes, dest: Path) -> bool:
    """Write the bytes only if they decode as an image; re-encode as RGB JPEG/PNG as received."""
    from PIL import Image
    try:
        with Image.open(io.BytesIO(data)) as im:
            im.verify()
    except Exception:
        return False
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_bytes(data)
    return True


def write_manifest(out_dir: Path, rows: list[dict]) -> None:
    with open(out_dir / "manifest.csv", "w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=FIELDS)
        w.writeheader()
        w.writerows(rows)
    print(f"{len(rows)} rows -> {out_dir / 'manifest.csv'}")


# --------------------------------------------------------------------------- Nutrition5k
N5K = "https://storage.googleapis.com/nutrition5k_dataset/nutrition5k_dataset"


def build_n5k(bench: Path, n: int) -> None:
    """Official RGB test split, overhead RealSense photo. Labels are weighed (the dataset's own)."""
    out = bench / "data" / "n5k"
    test_ids = fetch(f"{N5K}/dish_ids/splits/rgb_test_ids.txt").decode().split()
    meta = {}
    for cafe in ("cafe1", "cafe2"):
        for line in fetch(f"{N5K}/metadata/dish_metadata_{cafe}.csv").decode().splitlines():
            p = line.split(",")
            # dish_id, total_calories, total_mass, total_fat, total_carb, total_protein, then ingredients
            if len(p) >= 6:
                meta[p[0]] = [float(x) for x in p[1:6]]
    candidates = sorted(d for d in test_ids if d in meta and 0 < meta[d][0] <= 2000)   # drop the known corrupt labels
    random.Random(SEED).shuffle(candidates)
    rows = []
    for dish in candidates:
        if len(rows) >= n:
            break
        dest = out / "images" / f"{dish}.png"
        try:
            ok = dest.exists() or save_image(fetch(f"{N5K}/imagery/realsense_overhead/{dish}/rgb.png"), dest)
        except Exception:
            ok = False                  # not every test dish has an overhead scan
        if not ok:
            continue
        kcal, mass, fat, carb, protein = meta[dish]
        rows.append(dict(id=dish, path=str(dest), kcal=kcal, mass_g=mass, protein_g=protein, carb_g=carb, fat_g=fat,
                         group="cafeteria", label_quality="weighed"))
        print(f"\r{len(rows)}/{n}", end="", flush=True)
    print()
    write_manifest(out, rows)


# --------------------------------------------------------------------------- MM-Food-100K
MMFOOD_CSV = "https://huggingface.co/datasets/Humanbased-AI/MM-Food-100K/resolve/main/MM-Food-100K.csv"


def portion_grams(text: str) -> float | None:
    """'["noodles:200g","beef:100g"]' -> 300. None if any part isn't in plain grams."""
    try:
        parts = json.loads(text)
    except Exception:
        return None
    total = 0.0
    for p in parts:
        m = re.fullmatch(r"\s*[^:]+:\s*([\d.]+)\s*g\s*", str(p))
        if not m:
            return None
        total += float(m.group(1))
    return total or None


def build_mmfood(bench: Path, n: int) -> None:
    """Real user-submitted phone photos. Labels are AI/crowd estimates (not weighed): noisy by design.
    Only homemade and restaurant food (plated meals), 50-1500 kcal, food_prob >= 0.9; half of each group."""
    out = bench / "data" / "mmfood"
    out.mkdir(parents=True, exist_ok=True)
    csv_path = out / "MM-Food-100K.csv"
    if not csv_path.exists():
        csv_path.write_bytes(fetch(MMFOOD_CSV, timeout=300))
    by_group: dict[str, list[dict]] = {"Homemade food": [], "Restaurant food": []}
    with open(csv_path, encoding="utf-8") as f:
        for r in csv.DictReader(f):
            if r["food_type"] not in by_group:
                continue
            try:
                nut = json.loads(r["nutritional_profile"])
                kcal = float(nut["calories_kcal"])
                if not (50 <= kcal <= 1500) or float(r["food_prob"]) < 0.9:
                    continue
            except Exception:
                continue
            by_group[r["food_type"]].append(dict(r, _nut=nut, _kcal=kcal))
    rows = []
    rng = random.Random(SEED)
    for group, items in by_group.items():
        rng.shuffle(items)
        got = 0
        for r in items:
            if got >= n // 2:
                break
            url = r["image_url"]
            dest = out / "images" / Path(url).name
            try:
                ok = dest.exists() or save_image(fetch(url, timeout=30), dest)
            except Exception:
                ok = False
            if not ok:
                continue
            got += 1
            nut = r["_nut"]
            rows.append(dict(id=Path(url).stem, path=str(dest), kcal=r["_kcal"], mass_g=portion_grams(r["portion_size"]) or "",
                             protein_g=nut.get("protein_g", ""), carb_g=nut.get("carbohydrate_g", ""), fat_g=nut.get("fat_g", ""),
                             group=group.split()[0].lower(), label_quality="estimated"))
            print(f"\r{group}: {got}/{n // 2}", end="", flush=True)
        print()
    write_manifest(out, rows)


# --------------------------------------------------------------------------- Food Portion Benchmark
FPB_REPO = "issai/Food_Portion_Benchmark"


def build_fpb(bench: Path, n: int) -> None:
    """Validation split (its test labels are hidden). YOLO labels with a 6th column = weighed grams per item;
    the plate mass is the sum over items. Central Asian dishes, lab setting, phone and RealSense cameras."""
    from huggingface_hub import HfApi, hf_hub_download
    out = bench / "data" / "fpb"
    files = [f for f in HfApi().list_repo_files(FPB_REPO, repo_type="dataset") if f.startswith("FPB_Dataset/RGB/val/images/")]
    files.sort()
    random.Random(SEED).shuffle(files)
    rows = []
    for img_file in files:
        if len(rows) >= n:
            break
        label_file = img_file.replace("/images/", "/labels/").rsplit(".", 1)[0] + ".txt"
        try:
            lp = hf_hub_download(FPB_REPO, label_file, repo_type="dataset", local_dir=out / "raw")
            ip = hf_hub_download(FPB_REPO, img_file, repo_type="dataset", local_dir=out / "raw")
        except Exception:
            continue
        weights = []
        for line in Path(lp).read_text().splitlines():
            p = line.split()
            if len(p) >= 6:
                weights.append(float(p[5]))
        if not weights:
            continue
        rows.append(dict(id=Path(img_file).stem[:60], path=str(Path(ip)), kcal="", mass_g=sum(weights), protein_g="", carb_g="",
                         fat_g="", group=f"{len(weights)}_items", label_quality="weighed"))
        print(f"\r{len(rows)}/{n}", end="", flush=True)
    print()
    write_manifest(out, rows)


# --------------------------------------------------------------------------- HelloFresh (home-cooked dinners)
HF_REPO = "cemoss17/hellofresh_image_ingredients"


def build_hellofresh(bench: Path, n: int) -> None:
    """Test split of HelloFresh meal-kit recipes (Norway): one professional photo of a plated serving each.
    kcal / macros are HelloFresh's published per-serving nutrition (recipe-computed, not AI estimates and not
    weighed plates). The nutrient-gram VLM was trained on the train split only."""
    import pandas as pd
    out = bench / "data" / "hellofresh"
    # Plain HTTP: huggingface_hub's client crashed (native access violation) / hung on this repo on Windows.
    tree = json.loads(fetch(f"https://huggingface.co/api/datasets/{HF_REPO}/tree/main/data"))
    frames = []
    for item in tree:
        name = item["path"]
        if not name.startswith("data/test-"):
            continue
        local = out / "raw" / Path(name).name
        if not local.exists():
            local.parent.mkdir(parents=True, exist_ok=True)
            local.write_bytes(fetch(f"https://huggingface.co/datasets/{HF_REPO}/resolve/main/{name}", timeout=600))
        frames.append(pd.read_parquet(local))
    df = pd.concat(frames)
    rows = []
    for i, r in enumerate(df.itertuples()):
        nut = {x["nutrient"]["name"]: x["amount"] for x in r.nutrients}
        kcal = nut.get("Energy")
        if not kcal:
            continue
        dest = out / "images" / f"hf_test_{i:04d}.jpg"
        if not dest.exists() and not save_image(r.image["bytes"], dest):
            continue
        rows.append(dict(id=dest.stem, path=str(dest), kcal=kcal, mass_g="",
                         protein_g=nut.get("Protein", ""), carb_g=nut.get("Carbohydrate, by difference", ""),
                         fat_g=nut.get("Total lipid (fat)", ""), group="home_dinner", label_quality="recipe"))
    random.Random(SEED).shuffle(rows)
    write_manifest(out, rows[:n])


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--bench", required=True, type=Path)
    ap.add_argument("dataset", choices=["n5k", "mmfood", "fpb", "hellofresh"])
    ap.add_argument("--n", type=int, default=100)
    a = ap.parse_args()
    {"n5k": build_n5k, "mmfood": build_mmfood, "fpb": build_fpb, "hellofresh": build_hellofresh}[a.dataset](a.bench, a.n)
    sys.exit(0)
