# Photo calorie estimates

Why CalorieK and how well it does: [food-model-benchmark.md](food-model-benchmark.md). This page is how it is
wired and how to change it safely.

## The model
- **CalorieK** ([Dralkh/CalorieK](https://huggingface.co/Dralkh/CalorieK), MIT; SigLIP2-base, Apache-2.0):
  one RGB photo → `kcal, mass_g, protein_g, carb_g, fat_g`.
- Shipped as **ONNX fp32** (`caloriek_fp32.onnx`, 375 MB, SHA-256
  `d0063804198d72935cb1bb39c315416513d5e0df70a1aad90a85e0ae7ec104f9` for the 2026-10-22 export). Not in the APK
  and not in git.
- Produce it on a PC with `tools/food-bench/export_onnx.py` (see that folder's README).
- Do **not** ship the dynamic int8 (−19…−38 kcal MAE) or a naive fp16 conversion (every output is ∞).

## Installing it on a phone (until there is an in-app download)
```bash
MSYS_NO_PATHCONV=1 adb exec-in run-as dev.healthapp sh -c 'mkdir -p files/models && cat > files/models/caloriek_fp32.onnx' < caloriek_fp32.onnx
```
Settings → *Photo estimates* shows whether it is installed. Uninstalling the app deletes it.

## The preprocessing contract
Must match SigLIP's processor, which the model was trained with:
1. Decode with EXIF orientation applied (`ImageDecoder`), sub-sampled so the short side stays ≥ 448 px.
2. Halve repeatedly (filtered) while a side is ≥ 448 px — approximates PIL's antialiased bilinear resize.
3. Squash to **224 × 224** (aspect not kept), bilinear.
4. `(x / 255 − 0.5) / 0.5` per channel, **CHW**, float32, input name from the session, shape `(1, 3, 224, 224)`.

Changing any step changes the numbers. After a change, re-run the phone benchmark below and compare.

## The range and the portion question
- Range half-width = `max(119 kcal, 30% of the estimate)`: 119 is CalorieK's own 90% calibration radius (cafeteria
  test set); 30% ≈ the error we measured on everyday photos.
- Portion chips multiply kcal, macros and the range: Smaller ×0.75, As estimated ×1, Bigger ×1.25, Much bigger
  ×1.5. "Bigger" options exist because the model under-estimates large meals (≈ −170 kcal on 770 kcal dinners).
- Values are shown as whole numbers. The user must name the entry; nothing is saved automatically.

## On-device benchmark (debug builds)
```bash
# copy test images into private storage, then:
MSYS_NO_PATHCONV=1 adb shell am start -n dev.healthapp/.BenchActivity [--ez xnnpack true]
adb logcat -s CalorieBench
MSYS_NO_PATHCONV=1 adb exec-out run-as dev.healthapp cat files/bench/results.csv > phone_results.csv
```
2026-10-23, Pixel 9: session load 2.3 s, **0.55 s per photo** (ORT CPU, 4 threads; XNNPACK was 1.9 s), decode +
resize 16 ms. Phone vs PC MAE: 132 vs 131 kcal (MM-Food), 200 vs 197 (HelloFresh); per-image difference median
8–17 kcal.

## Ideas not done yet
- In-app model download (would need the `INTERNET` permission back — a privacy decision for the user).
- Smaller model: mixed-precision fp16 (keep LayerNorm/softmax fp32) or calibrated static int8.
- Learn a personal correction factor from the user's edits.
