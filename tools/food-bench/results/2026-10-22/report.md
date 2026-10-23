# Food photo -> calories: benchmark results

## mmfood

MM-Food-100K: real phone photos, homemade + restaurant (labels: AI/crowd estimates)

Paired on **256** images (all models answered).

| model | kcal MAE | median AE | % error (PMAE) | bias | within ±100 kcal | within ±25% | errors | s/img (PC CPU) |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| calorie-lens | 277 | 217 | 63% | +63 | 19% | 21% | 0 | 4.03 |
| calorie-lens · alt | 241 | 205 | 54% | +81 | 29% | 29% |  | 4.03 |
| caloriek-onnx-fp32 | 158 | 142 | 36% | -19 | 36% | 39% | 0 | 0.25 |
| caloriek-onnx-int8 | 177 | 186 | 40% | +78 | 30% | 35% | 0 | 0.18 |
| caloriek | 158 | 142 | 36% | -19 | 36% | 39% | 0 | 0.46 |
| nutrient-gram-2b | 351 | 288 | 80% | -351 | 7% | 1% | 0 | 4.76 |
| nutrient-gram-2b · alt | 264 | 215 | 60% | -257 | 24% | 6% |  | 4.76 |
| qwen3vl-2b | 316 | 150 | 71% | +146 | 48% | 55% | 21 | 5.21 |
| qwen3vl-2b · alt | 422 | 265 | 96% | +239 | 21% | 16% |  | 5.21 |

Best: **caloriek-onnx-fp32**. Versus runner-up caloriek: MAE difference -0 kcal, 95% CI [-0, +0] → NOT a clear difference (CI includes 0).

By group (kcal MAE):

| model | homemade (n=134) | restaurant (n=122) |
|---|---:|---:|
| calorie-lens | 279 | 275 |
| calorie-lens · alt | 242 | 240 |
| caloriek-onnx-fp32 | 136 | 182 |
| caloriek-onnx-int8 | 185 | 167 |
| caloriek | 136 | 182 |
| nutrient-gram-2b | 279 | 431 |
| nutrient-gram-2b · alt | 199 | 336 |
| qwen3vl-2b | 286 | 348 |
| qwen3vl-2b · alt | 344 | 509 |

Macro MAE (g):

| model | protein | carb | fat |
|---|---:|---:|---:|
| calorie-lens | 25.9 | 25.1 | 13.6 |
| caloriek-onnx-fp32 | 15.3 | 21.6 | 9.9 |
| caloriek-onnx-int8 | 21.3 | 21.7 | 11.7 |
| caloriek | 15.4 | 21.6 | 9.9 |
| nutrient-gram-2b | 22.5 | 27.3 | 11.0 |
| qwen3vl-2b | 11.3 | 57.0 | 13.9 |

Portion weight, paired on 268 images with a weight label:

| model | mass MAE (g) | % error | bias (g) |
|---|---:|---:|---:|
| calorie-lens | 170 | 45% | +4 |
| caloriek-onnx-fp32 | 120 | 32% | -64 |
| caloriek-onnx-int8 | 102 | 27% | +22 |
| caloriek | 120 | 32% | -64 |
| nutrient-gram-2b | 229 | 61% | -225 |
| qwen3vl-2b | 193 | 52% | -12 |

## hellofresh

HelloFresh test split: plated home-cooked dinners (labels: published per-serving nutrition)

Paired on **239** images (all models answered).

| model | kcal MAE | median AE | % error (PMAE) | bias | within ±100 kcal | within ±25% | errors | s/img (PC CPU) |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| calorie-lens | 409 | 384 | 52% | -407 | 5% | 11% | 0 | 3.29 |
| calorie-lens · alt | 338 | 316 | 43% | -333 | 9% | 20% |  | 3.29 |
| caloriek-onnx-fp32 | 191 | 145 | 24% | -166 | 39% | 60% | 0 | 0.19 |
| caloriek-onnx-int8 | 229 | 184 | 29% | -222 | 33% | 51% | 0 | 0.15 |
| caloriek | 191 | 145 | 24% | -166 | 39% | 60% | 0 | 0.45 |
| nutrient-gram-2b | 723 | 689 | 92% | -723 | 0% | 0% | 0 | 2.84 |
| nutrient-gram-2b · alt | 604 | 581 | 77% | -604 | 0% | 0% |  | 2.84 |
| qwen3vl-2b | 374 | 323 | 47% | -335 | 4% | 11% | 23 | 3.87 |
| qwen3vl-2b · alt | 454 | 348 | 58% | +122 | 16% | 31% |  | 3.87 |

Best: **caloriek**. Versus runner-up caloriek-onnx-fp32: MAE difference -0 kcal, 95% CI [-0, +0] → NOT a clear difference (CI includes 0).

Macro MAE (g):

| model | protein | carb | fat |
|---|---:|---:|---:|
| calorie-lens | 18.7 | 39.5 | 21.3 |
| caloriek-onnx-fp32 | 17.0 | 41.3 | 11.6 |
| caloriek-onnx-int8 | 14.4 | 41.2 | 12.0 |
| caloriek | 17.0 | 41.3 | 11.6 |
| nutrient-gram-2b | 19.4 | 37.4 | 28.1 |
| qwen3vl-2b | 18.9 | 44.1 | 17.8 |

## fpb

Food Portion Benchmark val: Central Asian dishes (labels: weighed grams, no kcal)


Portion weight, paired on 194 images with a weight label:

| model | mass MAE (g) | % error | bias (g) |
|---|---:|---:|---:|
| calorie-lens | 465 | 89% | -465 |
| caloriek | 367 | 70% | -352 |
| nutrient-gram-2b | 390 | 75% | -371 |
| qwen3vl-2b | 265 | 51% | -179 |

## n5k

Nutrition5k test, overhead photos: US cafeteria (labels: weighed) - sanity check vs published numbers

Paired on **88** images (all models answered).

| model | kcal MAE | median AE | % error (PMAE) | bias | within ±100 kcal | within ±25% | errors | s/img (PC CPU) |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| calorie-lens | 39 | 19 | 17% | -12 | 91% | 75% | 0 | 2.71 |
| calorie-lens · alt | 36 | 19 | 16% | -10 | 94% | 80% |  | 2.71 |
| caloriek | 91 | 47 | 40% | -84 | 64% | 33% | 0 | 0.31 |
| nutrient-gram-2b | 196 | 173 | 86% | -195 | 44% | 3% | 0 | 3.80 |
| nutrient-gram-2b · alt | 147 | 118 | 65% | -42 | 35% | 15% |  | 3.80 |
| qwen3vl-2b | 193 | 131 | 85% | +141 | 40% | 20% | 5 | 4.00 |
| qwen3vl-2b · alt | 286 | 154 | 126% | +244 | 40% | 18% |  | 4.00 |

Best: **calorie-lens · alt**. Versus runner-up calorie-lens: MAE difference -2 kcal, 95% CI [-8, +4] → NOT a clear difference (CI includes 0).

Macro MAE (g):

| model | protein | carb | fat |
|---|---:|---:|---:|
| calorie-lens | 3.1 | 4.3 | 3.2 |
| caloriek | 7.1 | 7.9 | 6.1 |
| nutrient-gram-2b | 10.1 | 17.0 | 9.1 |
| qwen3vl-2b | 9.9 | 60.6 | 10.0 |

Portion weight, paired on 95 images with a weight label:

| model | mass MAE (g) | % error | bias (g) |
|---|---:|---:|---:|
| calorie-lens | 20 | 11% | +2 |
| caloriek | 69 | 39% | -48 |
| nutrient-gram-2b | 93 | 53% | -29 |
| qwen3vl-2b | 150 | 85% | +119 |
