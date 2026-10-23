"""Score every pred_<dataset>_<model>.csv against its manifest and write <bench>/out/report.md.

For each dataset all models are compared on the SAME images (those every model answered), so numbers in
one table are paired. "<model> · alt" rows are a model's alternative readout (Calorie Lens's direct
totals head; for VLMs, kcal computed from their ingredient grams with NutriText).

    python -I report.py --bench <dir>
"""
from __future__ import annotations

import argparse
import csv
import random
import statistics
import sys
from pathlib import Path

DATASETS = {
    "mmfood": "MM-Food-100K: real phone photos, homemade + restaurant (labels: AI/crowd estimates)",
    "hellofresh": "HelloFresh test split: plated home-cooked dinners (labels: published per-serving nutrition)",
    "fpb": "Food Portion Benchmark val: Central Asian dishes (labels: weighed grams, no kcal)",
    "n5k": "Nutrition5k test, overhead photos: US cafeteria (labels: weighed) - sanity check vs published numbers",
}


def f(x):
    try:
        v = float(x)
        return v if v == v else None
    except (TypeError, ValueError):
        return None


def load_csv(p: Path) -> list[dict]:
    with open(p, encoding="utf-8") as fh:
        return list(csv.DictReader(fh))


def kcal_metrics(pairs: list[tuple[float, float]]) -> dict:
    err = [p - t for t, p in pairs]
    ae = [abs(e) for e in err]
    mean_t = statistics.mean(t for t, _ in pairs)
    return dict(n=len(pairs), mae=statistics.mean(ae), medae=statistics.median(ae), pmae=100 * statistics.mean(ae) / mean_t,
                bias=statistics.mean(err), w100=100 * sum(a <= 100 for a in ae) / len(ae),
                w25=100 * sum(a <= 0.25 * t for a, (t, _) in zip(ae, pairs)) / len(ae))


def boot_diff(a: list[float], b: list[float], n: int = 2000) -> tuple[float, float]:
    """95% CI of mean(a) - mean(b) for paired absolute errors."""
    rng = random.Random(0)
    idx = range(len(a))
    diffs = []
    for _ in range(n):
        s = [rng.choice(idx) for _ in idx]
        diffs.append(sum(a[i] - b[i] for i in s) / len(s))
    diffs.sort()
    return diffs[int(0.025 * n)], diffs[int(0.975 * n)]


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--bench", required=True, type=Path)
    a = ap.parse_args()
    out = a.bench / "out"
    lines = ["# Food photo -> calories: benchmark results", ""]
    for ds, desc in DATASETS.items():
        man_p = a.bench / "data" / ds / "manifest.csv"
        preds = sorted(out.glob(f"pred_{ds}_*.csv"))
        if not man_p.exists() or not preds:
            continue
        truth = {r["id"]: r for r in load_csv(man_p)}
        variants: dict[str, dict[str, dict]] = {}
        latency: dict[str, float] = {}
        errors: dict[str, int] = {}
        for p in preds:
            name = p.stem[len(f"pred_{ds}_"):]
            rows = load_csv(p)
            errors[name] = sum(1 for r in rows if r.get("error"))
            ok = [r for r in rows if not r.get("error")]
            secs = [f(r["seconds"]) for r in ok if f(r["seconds"]) is not None]
            latency[name] = statistics.median(secs) if secs else float("nan")
            variants[name] = {r["id"]: dict(kcal=f(r["kcal"]), mass=f(r["mass_g"]), protein=f(r["protein_g"]),
                                            carb=f(r["carb_g"]), fat=f(r["fat_g"])) for r in ok}
            alt = {r["id"]: dict(kcal=f(r["alt_kcal"])) for r in ok if f(r.get("alt_kcal")) is not None}
            if alt:
                variants[f"{name} · alt"] = alt

        lines += [f"## {ds}", "", desc, ""]
        has_kcal = any(f(r["kcal"]) is not None for r in truth.values())
        if has_kcal:
            # paired subset: images where every variant produced a kcal (VLM runs may cover a subset)
            common = set(truth)
            for v in variants.values():
                common &= {i for i, x in v.items() if x["kcal"] is not None}
            common = sorted(common)
            lines += [f"Paired on **{len(common)}** images (all models answered).", "",
                      "| model | kcal MAE | median AE | % error (PMAE) | bias | within ±100 kcal | within ±25% | errors | s/img (PC CPU) |",
                      "|---|---:|---:|---:|---:|---:|---:|---:|---:|"]
            ranked = []
            for name, v in variants.items():
                m = kcal_metrics([(f(truth[i]["kcal"]), v[i]["kcal"]) for i in common])
                base = name.split(" · ")[0]
                ranked.append((m["mae"], name))
                lines.append(f"| {name} | {m['mae']:.0f} | {m['medae']:.0f} | {m['pmae']:.0f}% | {m['bias']:+.0f} | {m['w100']:.0f}% | "
                             f"{m['w25']:.0f}% | {errors.get(base, 0) if ' · ' not in name else ''} | "
                             f"{latency.get(base, float('nan')):.2f} |")
            ranked.sort()
            if len(ranked) >= 2:
                (m1, n1), (m2, n2) = ranked[0], ranked[1]
                e1 = [abs(variants[n1][i]["kcal"] - f(truth[i]["kcal"])) for i in common]
                e2 = [abs(variants[n2][i]["kcal"] - f(truth[i]["kcal"])) for i in common]
                lo, hi = boot_diff(e1, e2)
                verdict = "a real difference" if hi < 0 else "NOT a clear difference (CI includes 0)"
                lines += ["", f"Best: **{n1}**. Versus runner-up {n2}: MAE difference {m1 - m2:+.0f} kcal, "
                              f"95% CI [{lo:+.0f}, {hi:+.0f}] → {verdict}."]
            # per group
            groups = sorted({truth[i]["group"] for i in common})
            if len(groups) > 1:
                lines += ["", "By group (kcal MAE):", "", "| model | " + " | ".join(f"{g} (n={sum(truth[i]['group'] == g for i in common)})" for g in groups) + " |",
                          "|---|" + "---:|" * len(groups)]
                for name, v in variants.items():
                    cells = []
                    for g in groups:
                        ids = [i for i in common if truth[i]["group"] == g]
                        cells.append(f"{kcal_metrics([(f(truth[i]['kcal']), v[i]['kcal']) for i in ids])['mae']:.0f}")
                    lines.append(f"| {name} | " + " | ".join(cells) + " |")
            # macros
            mac = [k for k in ("protein", "carb", "fat") if any(f(truth[i][f"{k}_g"]) is not None for i in common)]
            if mac:
                lines += ["", "Macro MAE (g):", "", "| model | " + " | ".join(mac) + " |", "|---|" + "---:|" * len(mac)]
                for name, v in variants.items():
                    if " · " in name:
                        continue
                    cells = []
                    for k in mac:
                        pr = [(f(truth[i][f"{k}_g"]), v[i][k]) for i in common if f(truth[i][f"{k}_g"]) is not None and v[i][k] is not None]
                        cells.append(f"{statistics.mean(abs(p - t) for t, p in pr):.1f}" if pr else "–")
                    lines.append(f"| {name} | " + " | ".join(cells) + " |")
        # mass
        if any(f(r["mass_g"]) is not None for r in truth.values()):
            common = set(i for i, r in truth.items() if f(r["mass_g"]) is not None)
            names = [n for n in variants if " · " not in n]
            for n in names:
                common &= {i for i, x in variants[n].items() if x["mass"] is not None}
            common = sorted(common)
            if common:
                lines += ["", f"Portion weight, paired on {len(common)} images with a weight label:", "",
                          "| model | mass MAE (g) | % error | bias (g) |", "|---|---:|---:|---:|"]
                mean_t = statistics.mean(f(truth[i]["mass_g"]) for i in common)
                for n in names:
                    e = [variants[n][i]["mass"] - f(truth[i]["mass_g"]) for i in common]
                    mae = statistics.mean(abs(x) for x in e)
                    lines.append(f"| {n} | {mae:.0f} | {100 * mae / mean_t:.0f}% | {statistics.mean(e):+.0f} |")
        lines.append("")
    (out / "report.md").write_text("\n".join(lines), encoding="utf-8")
    sys.stdout.reconfigure(encoding="utf-8")       # Windows consoles default to cp1252
    print("\n".join(lines))


if __name__ == "__main__":
    main()
