"""Run a small vision-language model (GGUF, via llama.cpp's llama-server) over a test set.

    python -I vlm_run.py --bench <dir> --dataset mmfood --name qwen3vl-2b \
        --model <gguf> --mmproj <mmproj gguf> [--limit 150]

Every image gets the same prompt at temperature 0. The reply is parsed for its own kcal/macros AND for
an ingredient list with grams; those grams are also turned into kcal with Calorie Lens's NutriText
(food name -> nutrients per 100 g), reported as alt_kcal. Output: <bench>/out/pred_<dataset>_<name>.csv,
the same columns as run.py, so report.py treats VLMs and specialist models alike.
"""
from __future__ import annotations

import argparse
import base64
import csv
import importlib.util
import json
import re
import subprocess
import sys
import time
import urllib.request
from pathlib import Path

PROMPT = (
    "Estimate the nutrition of the food in this photo. List each food you see with its weight in grams. "
    "Reply with JSON only, no other text, in exactly this form: "
    '{"items": [{"name": "white rice, cooked", "grams": 150}], "total_grams": 0, '
    '"kcal": 0, "protein_g": 0, "carb_g": 0, "fat_g": 0}'
)
OUT_FIELDS = ["id", "kcal", "mass_g", "protein_g", "carb_g", "fat_g", "alt_kcal", "seconds", "detail", "error"]
PORT = 8089


def post(path: str, body: dict, timeout: int = 300) -> dict:
    req = urllib.request.Request(f"http://127.0.0.1:{PORT}{path}", data=json.dumps(body).encode(),
                                 headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return json.loads(r.read())


def wait_ready(proc: subprocess.Popen, timeout: int = 300) -> None:
    t = time.time()
    while time.time() - t < timeout:
        if proc.poll() is not None:
            raise RuntimeError("llama-server exited during start-up")
        try:
            with urllib.request.urlopen(f"http://127.0.0.1:{PORT}/health", timeout=5) as r:
                if r.status == 200:
                    return
        except Exception:
            time.sleep(2)
    raise TimeoutError("llama-server did not become ready")


MAX_SIDE = 768   # an app would downscale too; full-size phone photos become 4k+ image tokens


def encode_image(path: str) -> str:
    """Longest side scaled to MAX_SIDE (never up), RGB JPEG q90, base64."""
    import io
    from PIL import Image
    with Image.open(path) as im:
        im = im.convert("RGB")
        im.thumbnail((MAX_SIDE, MAX_SIDE), Image.BICUBIC)
        buf = io.BytesIO()
        im.save(buf, "JPEG", quality=90)
    return base64.b64encode(buf.getvalue()).decode()


def find_items(obj) -> list[tuple[str, float]]:
    """Every {"name": ..., "grams": ...} anywhere in the parsed reply (models nest them differently)."""
    found = []
    if isinstance(obj, dict):
        if "name" in obj and isinstance(obj.get("grams"), (int, float)) and obj["grams"] > 0:
            found.append((str(obj["name"]), float(obj["grams"])))
        for v in obj.values():
            found += find_items(v)
    elif isinstance(obj, list):
        for v in obj:
            found += find_items(v)
    return found


def parse_reply(text: str) -> dict:
    m = re.search(r"\{.*\}", text, re.S)
    if not m:
        raise ValueError("no JSON in reply")
    data = json.loads(m.group(0))
    num = lambda k: float(data[k]) if isinstance(data, dict) and isinstance(data.get(k), (int, float)) and data[k] > 0 else None
    items = find_items(data)
    mass = num("total_grams") or (sum(g for _, g in items) if items else None)
    return dict(kcal=num("kcal"), mass_g=mass, protein_g=num("protein_g"), carb_g=num("carb_g"), fat_g=num("fat_g"), items=items)


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--bench", required=True, type=Path)
    ap.add_argument("--dataset", required=True)
    ap.add_argument("--name", required=True)
    ap.add_argument("--model", required=True)
    ap.add_argument("--mmproj", required=True)
    ap.add_argument("--limit", type=int, default=0, help="first N rows of the manifest (the manifest order is already random)")
    ap.add_argument("--gpu-layers", default="99")
    ap.add_argument("--device", default="Vulkan0", help="llama.cpp device; 'none' for CPU only")
    a = ap.parse_args()

    with open(a.bench / "data" / a.dataset / "manifest.csv", encoding="utf-8") as f:
        rows = list(csv.DictReader(f))
    if a.limit:
        rows = rows[:a.limit]
    out_path = a.bench / "out" / f"pred_{a.dataset}_{a.name}.csv"
    done = set()
    if out_path.exists():
        with open(out_path, encoding="utf-8") as f:
            done = {r["id"] for r in csv.DictReader(f)}
    todo = [r for r in rows if r["id"] not in done]
    if not todo:
        print("already complete"); return

    # NutriText (from the reviewed Calorie Lens model.py) turns item grams into kcal.
    spec = importlib.util.spec_from_file_location("calorie_lens_model", a.bench / "models" / "calorie-lens" / "model.py")
    cl = importlib.util.module_from_spec(spec); spec.loader.exec_module(cl)
    lens = cl.load(a.bench / "models" / "calorie-lens", device="cpu")

    server = a.bench / "tools" / "llama" / "llama-server.exe"
    log = open(a.bench / "out" / f"server_{a.name}.log", "w", encoding="utf-8")
    proc = subprocess.Popen([str(server), "-m", a.model, "--mmproj", a.mmproj, "--port", str(PORT), "--host", "127.0.0.1",
                             "-ngl", a.gpu_layers, "--device", a.device, "-np", "1", "-c", "4096", "--temp", "0",
                             "--no-webui", "-lv", "4"], stdout=log, stderr=log)
    try:
        wait_ready(proc)
        new_file = not out_path.exists()
        with open(out_path, "a", newline="", encoding="utf-8") as f:
            w = csv.DictWriter(f, fieldnames=OUT_FIELDS, extrasaction="ignore")
            if new_file:
                w.writeheader()
            for i, r in enumerate(todo, 1):
                t = time.time()
                try:
                    b64, mime = encode_image(r["path"]), "image/jpeg"
                    reply = post("/v1/chat/completions", {
                        "messages": [{"role": "user", "content": [
                            {"type": "image_url", "image_url": {"url": f"data:{mime};base64,{b64}"}},
                            {"type": "text", "text": PROMPT}]}],
                        "temperature": 0, "max_tokens": 600})
                    text = reply["choices"][0]["message"]["content"]
                    p = parse_reply(text)
                    alt = lens.calculate(p["items"])["totals"]["kcal"] if p["items"] else None
                    w.writerow(dict(id=r["id"], kcal=p["kcal"], mass_g=p["mass_g"], protein_g=p["protein_g"], carb_g=p["carb_g"],
                                    fat_g=p["fat_g"], alt_kcal=alt, seconds=round(time.time() - t, 2),
                                    detail="; ".join(f"{n} {g:.0f}g" for n, g in p["items"])[:500], error=""))
                except Exception as e:
                    w.writerow(dict(id=r["id"], seconds=round(time.time() - t, 2), error=repr(e)[:200]))
                f.flush()
                print(f"\r{a.name} {a.dataset}: {i}/{len(todo)}", end="", flush=True)
        print()
    finally:
        proc.terminate()
        proc.wait(timeout=30)
        log.close()


if __name__ == "__main__":
    sys.exit(main())
