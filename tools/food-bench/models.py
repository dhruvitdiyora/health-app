"""Wrappers that give every candidate the same interface: predict(image_path) -> dict of
kcal, mass_g, protein_g, carb_g, fat_g (None when a model doesn't estimate one), plus free-text detail.

Third-party code is loaded deliberately and narrowly:
  * Calorie Lens: its model.py (reviewed 2026-10-22: no network at inference, no exec, no file writes)
    is imported from that exact file path; weights are safetensors.
  * CalorieK: the checkpoint is a pickle. Its globals were scanned statically first (only OrderedDict
    and torch tensor rebuilds), and it is loaded with weights_only=True so nothing else could execute.
    The repo ships no inference code; the head below follows its model card.
"""
from __future__ import annotations

import importlib.util
import time
from pathlib import Path

import torch
import torch.nn as nn
import torch.nn.functional as F
from PIL import Image

torch.set_num_threads(max(1, torch.get_num_threads()))


class CalorieLens:
    name = "calorie-lens"

    def __init__(self, model_dir: Path):
        spec = importlib.util.spec_from_file_location("calorie_lens_model", Path(model_dir) / "model.py")
        mod = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(mod)
        self.predictor = mod.load(model_dir, device="cpu")

    def predict(self, path: str) -> dict:
        r = self.predictor.predict(path)
        t, d = r["totals"], r["direct_estimate"]
        return dict(kcal=t["kcal"], mass_g=t["mass_g"], protein_g=t["protein_g"], carb_g=t["carb_g"], fat_g=t["fat_g"],
                    detail="; ".join(f"{i['name']} {i['grams']:.0f}g" for i in r["ingredients"]),
                    alt_kcal=d["kcal"])        # the direct totals head, before the ingredient readout


class _NutritionHead(nn.Module):
    """From the CalorieK model card: LayerNorm -> 768 -> 256 -> 5, softplus outputs (kcal, mass, P, C, F)."""

    def __init__(self, input_dim: int = 768):
        super().__init__()
        self.net = nn.Sequential(
            nn.LayerNorm(input_dim), nn.Linear(input_dim, 768), nn.GELU(), nn.Dropout(.15),
            nn.Linear(768, 256), nn.GELU(), nn.Dropout(.10), nn.Linear(256, 5))

    def forward(self, x):
        return F.softplus(self.net(x))


class CalorieK:
    name = "caloriek"
    BASE = "google/siglip2-base-patch16-224"

    def __init__(self, model_dir: Path):
        from transformers import AutoConfig, AutoImageProcessor, SiglipVisionModel
        ck = torch.load(Path(model_dir) / "caloriek.pt", map_location="cpu", weights_only=True)
        cfg = AutoConfig.from_pretrained(self.BASE)            # config + preprocessor only; no base weights needed
        self.vision = SiglipVisionModel(getattr(cfg, "vision_config", cfg))
        # transformers 5 flattened SiglipVisionModel (no .vision_model); older versions nest it.
        target = getattr(self.vision, "vision_model", self.vision)
        target.load_state_dict(ck["vision_state"], strict=True)
        self.head = _NutritionHead()
        self.head.load_state_dict(ck["head_state"], strict=True)
        self.vision.eval(); self.head.eval()
        self.processor = AutoImageProcessor.from_pretrained(self.BASE)

    @torch.inference_mode()
    def predict(self, path: str) -> dict:
        with Image.open(path) as im:
            pixels = self.processor(images=im.convert("RGB"), return_tensors="pt").pixel_values
        out = self.vision(pixel_values=pixels)
        feats = out.pooler_output if out.pooler_output is not None else out.last_hidden_state[:, 0]
        kcal, mass, protein, carb, fat = self.head(feats.float())[0].tolist()
        return dict(kcal=kcal, mass_g=mass, protein_g=protein, carb_g=carb, fat_g=fat, detail="")


REGISTRY = {"calorie-lens": (CalorieLens, "calorie-lens"), "caloriek": (CalorieK, "caloriek")}


def load(name: str, bench: Path):
    cls, folder = REGISTRY[name]
    t = time.time()
    m = cls(bench / "models" / folder)
    print(f"loaded {name} in {time.time() - t:.1f}s")
    return m
