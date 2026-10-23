# Sources

Every link the user shares is documented here with its content. Newest entries go at the bottom. Accessed 2026-10-01 unless noted.

---

## User-provided links

### 1. hackyguru: "exiting whoop" (Zhoop)
- URL: https://x.com/hackyguru/status/2104611154559742444
- Fetched via mirror (x.com returned HTTP 402): https://api.fxtwitter.com/hackyguru/status/2104611154559742444
- Author: guru (@hackyguru), DevRel Lead at Logos Network, London. Posted 2026-09-28.
- Content: "exiting whoop after 18 months. kept the strap, ditched the subscription. it now runs fully local on zhoop (my fork of noop). no fluff metrics, my data stays on my phone. ai killed vendor lock-in." Two screenshots (https://pbs.twimg.com/media/HTUUsHHW0AA7YLK.jpg, https://pbs.twimg.com/media/HTUUsHDXkAA0-cP.jpg; not viewed). About 2.1M views, 9.9k likes, 6k bookmarks.
- Take-away: local-first, no vendor cloud, a deliberately small UI. Details are in the repo entries below.

### 2. levelsio: caltrack
- URL: https://x.com/levelsio/status/2102413190642516048
- Fetched via mirror: https://api.fxtwitter.com/levelsio/status/2102413190642516048
- Author: @levelsio (Pieter Levels).
- Content: "Yes I built it for myself, it started with a calorie tracker, kinda messy but nice: https://caltrack.levels.io. I log my food in Telegram to get calories and protein data. My workouts and sleep from WHOOP. My weight from my scale. Indoor air quality, temperature, humidity for [image]". It quotes @IAmPascio, who guessed it was vibe-coded.
- Follow-up link, the dashboard itself: https://caltrack.levels.io
  - Panels: calories vs. maintenance (7-day avg/median), protein vs. 150 g floor, macros, NOVA % unprocessed food (target 75% or more), steps vs. 7,000, weight trend and weekly loss/deficit, body-fat estimate calibrated against DEXA/InBody scans, sleep and recovery (WHOOP), and a "WHOOP calibration" panel that compares eaten calories to device-estimated burn over 14 days.
  - Sources shown: Apple Health (steps), WHOOP (sleep, recovery, HR), manual food entries with photos sent via Telegram, body scans.
  - UI: 30d/90d/6m/12m windows, daily/weekly/monthly toggles, a day log sortable by time, protein density or calories, green/amber/red status.
- Take-away: Telegram/chat logging with photos, the NOVA score, and the eaten-vs-burned calibration idea.

### 3. the2ndfloorguy: gym post
- URL: https://x.com/the2ndfloorguy/status/2100581067979915541
- Fetched via mirror: https://api.fxtwitter.com/the2ndfloorguy/status/2100581067979915541
- Author: Pankaj (@the2ndfloorguy), AI/hardware at AinaInterface, Bengaluru.
- Content: "lifting heavy and protein maxxing literally changes your soul". Two images (https://pbs.twimg.com/media/HSbBMjEasAAZyI9.jpg, https://pbs.twimg.com/media/HSbBJK6bQAA1YnI.jpg). Only the first was viewed, and only partly: a WHOOP-style recovery ring reading 80%. No build details in the tweet.
- Take-away: none for architecture. Same genre only (recovery-ring UI).

---

## Follow-up sources found while researching

### NOOP: offline WHOOP companion (upstream of Zhoop)
- URL: https://github.com/ryanbr/noop (other forks: https://github.com/app-noop/noop, https://github.com/muftiarfan/noop)
- Content: standalone offline app for WHOOP 4.0/5.0 straps over Bluetooth, with no account or subscription. Swift/SwiftUI (macOS/iOS), Kotlin/Compose (Android), SQLite via GRDB. Modules: WhoopProtocol (BLE frames, CRC), OuraProtocol, PolarProtocol, WhoopStore (schema), StrandAnalytics (metrics), StrandImport (WHOOP CSV, Apple Health XML, Cronometer/MacroFactor CSV).
- Metrics are computed on-device: HRV (RMSSD/SDNN, Task Force 1996), Recovery 0-100 (HRV z-score vs. personal baseline), Strain 0-21 (Karvonen %HRR + Edwards/Banister TRIMP), 4-class sleep staging, correlation engine.
- Optional AI coach with a user-supplied key (Anthropic, OpenAI, Ollama/LM Studio) that sends only a text summary of recent metrics.
- Licence: PolyForm Noncommercial 1.0.0 (personal use only, not OSI open source). Credits protocol work from johnmiddleton12/my-whoop and b-nnett/goose.
- Reliability: summarised from the README by an automated fetch. The code itself was not inspected.

### Zhoop repo
- URL: https://github.com/hackyguru/zhoop (also forks https://github.com/dekaru/zhoop and https://github.com/0xpratzyy/hoop)
- Content: Swift, iPhone-first fork of NOOP, with Apple Watch complications. Two tabs ("Today", "Sleep"). Calorie-deficit calculator with daily allowance, food log (kcal + optional protein), estimated weight from logged deficit, goal weight/date, 7-day deficit as fat-mass equivalent, +10% logging buffer, recent-food re-logging. The README gives no detail on photo handling. Same PolyForm Noncommercial licence.

### Google Fit / Fitbit shutdown and Google Health API
- URLs: https://sahha.ai/blog/google-fit-api-sunset-migration/, https://sahha.ai/blog/fitbit-api-sunset-migration/, https://www.thryve.health/blog/fitbit-shutdown-september-2026-migration-options, https://www.mindbowser.com/fetch-google-fit-data-continuously
- Content: Google Fit APIs end of service is late 2026. Health Connect (on-device, Android) is the replacement. The Fitbit Web API is replaced by the **Google Health API**: Google OAuth 2.0, scopes such as `googlehealth.activity_and_fitness.readonly`, 31 data types, read methods `list`/`reconcile`/`rollUp`/`dailyRollUp`, about 5-second intraday HR. Legacy tokens do not transfer, and re-authorisation is needed before 2026-10-30. Individual developers reportedly need a restricted-scope security review with a queue.
- Reliability: **vendor blogs, unverified against Google's official docs.** Verify before relying on it (PLAN.md Phase 0).

### WHOOP developer platform
- URL: https://developer.whoop.com/docs/introduction
- Content: OAuth 2.0 API with recovery, sleep, strain/cycle, workouts and body measurements, plus webhooks, pagination and rate limits. Only the intro page was read. Scopes, URLs, limits and personal-use policy are **unverified**. Check the OAuth and API reference pages (PLAN.md Phase 0).

---

## Research: Android + Health Connect (2026-10-22)

### Google Health API: About page (official)
- URL: https://developers.google.com/health/about (page last updated 2026-09-29)
- Content: the Fitbit Web API loses support on **2026-09-30** and is **turned off on 2026-10-30**. The Google Health API replaces it, uses Google OAuth 2.0, and "all Google Health API scopes are categorized as Restricted, necessitating a privacy and security review". There's no mention of personal use or a testing mode. Health Connect parity is planned for Q4 2026 / Q1 2027.
- Reliability: official Google documentation. It confirms the dates the earlier vendor blogs reported.
- Take-away: don't depend on the cloud API. Read Health Connect on the phone instead (PLAN.md v2).

### WHOOP and Fitbit → Health Connect
- URLs:
  - https://gadgetsandwearables.com/2023/07/14/whoop-health-connect-integration/
  - https://www.community.whoop.com/t/share-heart-rate-with-health-connect-apple-health/13681
  - https://support.google.com/fitbit/answer/14506680
  - https://9to5google.com/2024/04/02/fitbit-app-health-connect-2/
- Content:
  - WHOOP has synced both ways with Health Connect since 2023-07-11. It writes sleep, resting HR, respiratory rate, SpO2 and cardio workouts, and reads nutrition, weight and activities.
  - A forum post says WHOOP writes HR only during activities.
  - Fitbit writes into Health Connect, with some data types needing a Google account.
- Reliability: a 2023 news article, a forum post, and a Google help page. The WHOOP details are **unverified**.
- **Checked on the user's phone (2026-10-22):**
  - The Fitbit app 5.10.1 has Health Connect write permission for steps, sleep, HR, HRV, resting HR, weight, total calories, exercise and nutrition, but not active calories.
  - The first sync returned a year of data. Details are in PLAN.md risk 1.

### Health Connect developer docs
- URLs:
  - https://developer.android.com/health-and-fitness/guides/health-connect/overview
  - https://developer.android.com/guide/health-and-fitness/health-connect/plan/data-types
  - https://developer.android.com/codelabs/health-connect
- Content: an on-device store with per-data-type permissions. Sleep sessions carry stages. Aggregation is done per period.
- Reliability: official. We use `androidx.health.connect:connect-client` 1.1.0.

## Research: on-device food photo → calories (2026-10-22)

The user asked for a model fine-tuned specifically for image → calories, the most accurate one that's small enough to run on the phone.

### Calorie Lens (shalev396/calorie-lens)
- URL: https://huggingface.co/shalev396/calorie-lens. Code at https://github.com/shalev396/ml-lab/tree/main/calorie-lens, data at https://huggingface.co/datasets/shalev396/calorie-lens-data
- Content:
  - A DINOv2-B backbone (94.6M params, 379 MB fp32 safetensors) with 253 ingredient queries. Each query predicts whether that ingredient is present and its grams, and a frozen per-gram nutrition table turns the grams into kcal, macros, fiber, sugars and sodium.
  - It includes **NutriText**, a small text model that maps any food name to nutrients per 100 g, trained on about 470k USDA names.
  - Trained on Nutrition5k (cleaned), Finnish cafeteria plates (FlavoriaFoodWeight1700) and USDA FDC.
  - Reported test results:
    - Overhead photos: **16.0% kcal error (40.9 kcal MAE)**.
    - Side-angle photos: 17.9% (40.5 kcal).
    - Plates never seen in training: 16.3%.
  - Licence MIT, plus Apache-2.0 for the DINOv2 weights. Created 2026-10-10, about 6.4k downloads.
- Reliability: **self-reported** by a single author, not peer reviewed. It reports confidence intervals and a locked test split. **There's no evaluation on everyday home or restaurant photos.**
- Take-away: the leading on-device candidate. Its ingredient-and-grams output maps directly onto an editable result card.
- **Our measurements (2026-10-22, [food-model-benchmark.md](food-model-benchmark.md)):**
  - It reproduces its claim on Nutrition5k overhead: 39 kcal MAE.
  - It **fails to generalise**: 277 kcal (63%) on everyday MM-Food photos and 409 kcal (52%) on HelloFresh dinners.
  - Not chosen.

### CalorieK (Dralkh/CalorieK)
- URL: https://huggingface.co/Dralkh/CalorieK
- Content:
  - A SigLIP2-base backbone (93.7M params, 375 MB `.pt` pickle) with a regression head. It outputs only five numbers: kcal, mass, protein, carbs and fat.
  - On the Nutrition5k side-angle test it reports **56.3 kcal MAE (24.1%)**, against 77.0 for Food-R1 8B and 82.6 for Qwen3.5-9B.
  - **On everyday photos it does much worse:** on 499 MM-Food-100K images it reports **181 kcal MAE** (mean target 233 kcal) with only 35% of estimates within ±100 kcal. Raw fruit and vegetables fail.
  - Licence MIT.
- Reliability: self-reported, with 24 downloads. It cites a "Boba 0.8B" comparison model that we couldn't find on Hugging Face (HTTP 401). The weights are a pickle, so loading them can run code; load only with `weights_only=True`.
- Take-away: the most honest published estimate of how this class of model handles everyday food. Expect roughly ±150 kcal on home meals.
- **Our measurements (2026-10-22):**
  - **Best on every everyday set:** 158 kcal MAE (36%) on MM-Food and 191 kcal (24%) on HelloFresh.
  - It underestimates large meals by about 170 kcal.
  - ONNX fp32 export is exact (375 MB); dynamic int8 costs 19–38 kcal of MAE.
  - The repo has no inference code; we rebuilt it from the card. The pickle is a plain state_dict and loads with `weights_only=True`.
  - **Chosen for the phone.**

### CalorieCLIP (jc-builds/CalorieCLIP)
- URL: https://huggingface.co/jc-builds/CalorieCLIP
- Content: CLIP ViT-B/32 with a regression head (151M params). It claims 51.4 kcal MAE, but its example errors of 0–6 kcal suggest evaluation leakage. On CalorieK's strict protocol it scored 126 kcal MAE on Nutrition5k and 225 on MM-Food.
- Take-away: rejected.

### Papers and benchmarks
- Food-R1 (8B food VLM trained with reinforcement learning, CalorieBench-80K): https://arxiv.org/abs/2606.04986. Too large to run on the phone.
- CalorieLLaVA (LLaVA fine-tuned on Nutrition5k): http://img.cs.uec.ac.jp/e/pub/conf24/241201tanabe_0.pdf
- Benchmark of 10 vision-language models for dietary assessment (bioRxiv 2026): https://www.biorxiv.org/content/10.64898/2026.07.26.740845v1. The best was Gemini 3.0 Flash (cloud), with 80.7 kcal MAE.
- Interactive VLM pipeline (CVPR 2026 workshop): https://openaccess.thecvf.com/content/CVPR2026W/MTF/papers/Bhatambarekar_An_Agentic_Vision-Language_Pipeline_for_Interactive_Nutritional_Estimation_from_Food_CVPRW_2026_paper.pdf. Asking the user about ingredients and portion size cuts the error substantially.
- Nutrition5k (Google, 2021): https://arxiv.org/abs/2103.03375. Its RGB baseline is 26.1% error, and the best published RGB-only result is about 14.1% (per Calorie Lens).
- Reliability: arXiv and bioRxiv preprints. The figures above come from search summaries; only the abstracts were read.
- Take-away: every small specialist model is trained on cafeteria plates (US and Finnish), so accuracy on home food is unknown, and probably much worse. The app has to make the estimate easy to correct, and asking about portion size helps.

## Benchmark sources (2026-10-22/23)

Found while running the comparison in [food-model-benchmark.md](food-model-benchmark.md). The user asked to check for better models and to look on Kaggle for a test set.

### More model candidates
- **nutrient-gram-qwen-3-vl-2b**:
  - URLs:
    - https://huggingface.co/cemoss17/nutrient-gram-qwen-3-vl-2b
    - GGUF: https://huggingface.co/mradermacher/nutrient-gram-qwen-3-vl-2b-GGUF
  - Content:
    - Qwen3-VL-2B fine-tuned with TRL SFT. The card is an auto-generated template with no evaluation and no licence.
    - Training data, inferred from the author's only food dataset: https://huggingface.co/datasets/cemoss17/hellofresh_image_ingredients
      - 921 / 132 / 263 Norwegian HelloFresh photos.
      - Ingredient grams were **generated by another AI** (`confidence` and `num_generations` fields).
      - The recipe text was given as an input hint.
  - Measured: worst on everyday photos (264–604 kcal MAE). Rejected.
- **Qwen3-VL-2B-Instruct GGUF (official)**:
  - URL: https://huggingface.co/Qwen/Qwen3-VL-2B-Instruct-GGUF
  - Content: Apache-2.0 base VLM, used as an un-fine-tuned control.
  - Measured:
    - Best at portion *weight* on the multi-dish Food Portion Benchmark (265 g MAE).
    - Poor on kcal (316 / 374 kcal MAE), with large outliers.
    - About 8% invalid JSON replies.
- **qwen3vl-food-lora**:
  - URLs:
    - https://huggingface.co/hungvtm/qwen3vl-food-lora
    - Write-up: https://pub.towardsai.net/building-a-food-nutrition-estimation-ai-an-end-to-end-guide-fcbad620266e
  - Content: a Qwen3-VL-4B LoRA trained on MM-Food-100K.
  - Not tested: our test set is part of its training data, and at about 2.5 GB it's too heavy for the phone.
- **Food-R1 GGUF** (an unofficial community conversion):
  - URL: https://huggingface.co/AKMESSI/Food-R1-GGUF
  - Content: 8B, at least 5 GB at Q4 plus a 1.1 GB projector.
  - Not tested: too big for a phone, and trained on data derived from MM-Food-100K.
- **CaLoRAify** (arXiv 2412.09936): https://arxiv.org/abs/2412.09936. A LoRA VLM with retrieval over a nutrition database; a large model. Background only.
- **MeLL-cal** (Journal of Food Quality, 2025): https://onlinelibrary.wiley.com/doi/10.1155/jfq/7044178. A 2.3K-parameter calorie model evaluated on MenuMatch/Calo world. We found no weights.
- **Benchmark of large multimodal models on nutrition, with metadata** (arXiv 2507.07048): https://arxiv.org/abs/2507.07048. Giving the model context (metadata) cut calorie MAE by 21–75 kcal. This supports asking the user a follow-up question.
- **Benchmarking foundation-model dietary estimates** (PMC): https://pmc.ncbi.nlm.nih.gov/articles/PMC13401436/. Background only; only the search snippet was read.

### Test datasets
- **MM-Food-100K**:
  - URLs:
    - https://huggingface.co/datasets/Humanbased-AI/MM-Food-100K (CSV of image URLs on file.b18a.io, licence OpenRAIL)
    - Paper: https://arxiv.org/abs/2508.10429
  - Content: 100K user-submitted phone photos with dish name, type, ingredients, portion, kcal and macros. **Labels are AI or crowd estimates**; for example, a bunch of bananas is labelled 1,050 kcal.
  - Used: 300 homemade and restaurant photos.
- **HelloFresh image ingredients**: https://huggingface.co/datasets/cemoss17/hellofresh_image_ingredients. Its test split is 263 plated dinners with HelloFresh's published per-serving nutrition. Licence not stated. Used for evaluation only.
- **Food Portion Benchmark (ISSAI)**:
  - URLs:
    - https://huggingface.co/datasets/issai/Food_Portion_Benchmark (CC BY-NC 4.0)
    - Paper: IEEE Access 2025, DOI 10.1109/access.2025.3603287
  - Content: 14,083 photos of 138 Central Asian dishes with weighed grams per item. The test labels are hidden.
  - Used: 200 validation photos.
- **Nutrition5k (Google)**: https://storage.googleapis.com/nutrition5k_dataset/nutrition5k_dataset (CC BY 4.0). The official test split with weighed labels. Used: 100 overhead photos.
- **NutritionVerse-Real (Kaggle)**:
  - URLs:
    - https://www.kaggle.com/datasets/nutritionverse/nutritionverse-real (CC BY-NC-SA 4.0, 1.1 GB)
    - Paper: https://arxiv.org/abs/2401.08598
  - Content: 889 iPhone 13 Pro Max photos of 251 dishes (45 food types), every ingredient weighed, with segmentation masks. **The best match for our use case.**
  - Not used yet: downloading needs a Kaggle account token. The anonymous API returned 404, and there's no other mirror.
  - Related: https://arxiv.org/abs/2309.07704
- **Nutricheck (Kaggle)**: https://www.kaggle.com/datasets/dwiiyy/nutricheck-image-dataset (Apache-2.0). 53 classes of Indonesian food with one FatSecret nutrition row per class. It has no per-photo portions, so it can't test calories. Rejected.
- **Kaggle Nutrition5k mirrors**: for example https://www.kaggle.com/datasets/gillesokhin/nutrition5k-dataset. The same data as the official bucket, so not needed.

### Tools
- **llama.cpp b11815** (Vulkan build for Windows): https://github.com/ggml-org/llama.cpp/releases/tag/b11815. Runs the GGUF VLMs on the GTX 1050 Ti. The same engine runs on Android.
- **ONNX Runtime / onnxconverter-common**: https://onnxruntime.ai. Used to export CalorieK to fp32 and int8. The fp16 conversion overflowed (every output was ∞).
