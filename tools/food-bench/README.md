# food-bench

These scripts compare on-device food-photo → calorie models on the same images. Results and conclusions are in [docs/food-model-benchmark.md](../../docs/food-model-benchmark.md).

Downloaded models and data go in a separate work folder, the "bench" (it held 6.2 GB in the 2026-10-22 run). Untrusted downloads never sit next to these scripts.

## Setup (Windows, run on 2026-10-22)

```bash
uv venv --python 3.12 <bench>/venv
uv pip install --python <bench>/venv/Scripts/python.exe torch torchvision --index-url https://download.pytorch.org/whl/cpu
uv pip install --python <bench>/venv/Scripts/python.exe "transformers>=4.57" safetensors pillow pandas numpy huggingface_hub pyyaml pyarrow
```

The 2026-10-22 run used torch 2.14.1+cpu and transformers 5.20.2.

**Models:**
- Put each model in `<bench>/models/<name>/`:
  - `shalev396/calorie-lens` goes in `calorie-lens`.
  - `Dralkh/CalorieK` goes in `caloriek`.
- For the vision-language models, put the GGUFs in `qwen3vl-2b/` and `nutrient-gram-2b/`.
- Unpack llama.cpp's `llama-bXXXX-bin-win-vulkan-x64.zip` into `<bench>/tools/llama/`. The run used b11815.

**Safety:**
- Read each model's code before running it.
- Calorie Lens's `model.py` is loaded by file path only.
- CalorieK's pickle is loaded with `weights_only=True`. A static scan showed its only globals are `OrderedDict` and torch tensor rebuilds.

## Run

Always run with `python -I`, which keeps the bench folder off `sys.path`.

```bash
python -I datasets.py --bench <bench> mmfood --n 300
python -I datasets.py --bench <bench> hellofresh --n 263
python -I datasets.py --bench <bench> fpb --n 200
python -I datasets.py --bench <bench> n5k --n 100
python -I run.py --bench <bench> --dataset mmfood --models calorie-lens caloriek
python -I vlm_run.py --bench <bench> --dataset mmfood --name qwen3vl-2b --model <gguf> --mmproj <mmproj> --limit 150
python -I report.py --bench <bench>
```

All sampling uses a fixed seed (2026), so the same commands pick the same images. Each run resumes where it stopped.

## Results in this repo
`results/2026-10-22/` holds our own outputs: per-model prediction CSVs, `report.md` and model `sha256.txt`. The
test-set manifests (which copy each dataset's labels) are **not** committed — some source datasets are
non-commercial or unlicensed. Rebuild them with `datasets.py` (same seed → same images) before re-running
`report.py`.

## Files

| File | Purpose |
|---|---|
| `datasets.py` | Downloads a fixed sample of each test set and writes `manifest.csv` (path plus labels) |
| `models.py` | Gives the specialist models one shared interface |
| `run.py` | Runs the specialist models and writes `out/pred_<dataset>_<model>.csv` |
| `vlm_run.py` | Runs a GGUF vision-language model through llama-server with one fixed prompt. It also turns the model's ingredient grams into kcal using NutriText |
| `report.py` | Compares all models on the same images, adds bootstrap CIs, and writes `out/report.md` |
