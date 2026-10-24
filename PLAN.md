# Health App: Plan (v2, Android-first)

Status: **Phase 1 is built** as a native Android app in [android/](android/) and runs on the user's Pixel 9 (Android 17) against their real Health Connect data. On 2026-10-22 the user asked for "something good for android" and approved building overnight.

## Goal
One personal, local-first health app that combines:
1. Calorie/macro tracking, with food logged by hand now and later via chat (text + photos)
2. Sleep, recovery and activity from WHOOP and Fitbit / Google Health
3. Weight, steps, and a custom activity builder/tracker
4. Cross-source insights (e.g. calories eaten vs. calories burned, sleep vs. recovery)

## What we learned from the references
| Source | Idea we borrow |
|---|---|
| levelsio caltrack | Log food by chat with a photo, get kcal + protein + NOVA score. Many sources in one dashboard. "WHOOP calibration": eaten vs. device-estimated burn over 14 days. |
| hackyguru Zhoop / NOOP | Local-first: all data in SQLite, no account. Keep the UI small (Today + Sleep). The AI coach is opt-in and gets only text summaries, never raw data. Compute metrics from published formulas. |
| the2ndfloorguy | No build info. Same genre only (recovery ring UI). |

## Decisions made
- **Platform: native Android app (Kotlin + Jetpack Compose).** This replaces "web dashboard first" (decided 2026-10-22, see below). A desktop/web view can come later from an export.
- **Device data comes from Health Connect on the phone**, not from cloud APIs. The Fitbit app and the WHOOP app both write into Health Connect, and we read it on-device. This means:
  - No Google Health API restricted-scope review (Google's own docs say **all** its scopes are Restricted and need a privacy and security review).
  - The Fitbit Web API turning off on **2026-10-30** (confirmed on Google's own page) doesn't affect us.
  - No WHOOP OAuth app is needed for v1. The WHOOP cloud API stays an option for recovery/strain scores, which WHOOP does not appear to write to Health Connect (unverified).
- Data sources can each be toggled: Health Connect permissions are per data type and per app.
- Food-photo AI: provider-agnostic interface, model chosen later.
- Storage: local SQLite (Room) on the phone. Nothing leaves the device except an AI call the user turns on.

### Why Android + Health Connect (2026-10-22)
| Option | Verdict |
|---|---|
| Web app on the PC, phone over LAN | Only works at home, and still needs the Google Health API review for Fitbit. |
| PWA | Can't read Health Connect, so we'd be back to cloud APIs. |
| **Native Android + Health Connect** | Reads Fitbit + WHOOP on-device with no reviews, works offline, and has camera and photos built in. Android SDK + JDK 21 are already installed on this PC. **Chosen.** |

## Architecture (Android)
- Single Gradle module at [android/](android/), the same layout as the user's other Compose apps (AGP 8.13, Kotlin 2.3, Compose BOM, Room + KSP).
- **DB (Room)**: `food_entry`, `weight_entry`, `daily_metric` (one row per day synced from Health Connect: steps, active/total kcal burned, sleep, resting HR, HRV, exercise minutes), `sleep_session`.
- **Settings (DataStore)**: kcal/protein/carb/fat targets, goal weight, height, and whether to write food to Health Connect.
- **Health Connect sync**: on app open and on pull-to-refresh, read the last 30 days (back to 365 with the history permission), then upsert into `daily_metric`, `sleep_session` and `weight_entry`. Each record keeps its source app (e.g. WHOOP vs. Fitbit).
- **Screens**: Today, Food, Trends, Settings.
- **AI provider interface (Phase 2)**: `analyzeFood(image?, text?) -> { items[], kcal, protein, carbs, fat, nova, confidence }`. The user reviews and edits the result before saving (never auto-save).
- **Insights**: pure functions over the DB (7-day averages, eaten vs. burned, sleep/recovery).

## Risks
1. **What WHOOP/Fitbit actually write to Health Connect.** Third-party reports say WHOOP writes sleep, resting HR, respiratory rate, SpO2 and cardio workouts, but not recovery/strain scores (unverified, 2023 article). *Mitigation:* the app shows which source app each metric came from.
   - **Checked on the user's Pixel 9 (Android 17), 2026-10-22, first sync of 365 days:**
     - Fitbit (`com.fitbit.FitbitMobile` 5.10.1) supplied sleep with stages (58 sessions since 31 Aug), resting HR (53 days) and HRV (48 days), plus steps and weight.
     - Strava and Hevy add workouts, and Hevy also adds weight.
     - The phone counts steps itself (`com.android.healthconnect.phone.*`).
   - **Total calories burned has a value on every day**, even with no device data, because Health Connect fills the gaps with a resting-burn estimate (source `android`). Trends must not read such days as tracked activity.
   - The WHOOP app isn't installed on that phone, so nothing from WHOOP has been verified yet.
   - Open question: whether workout minutes double-count when Fitbit and Strava both record the same session.
2. **WHOOP recovery/strain** may need the WHOOP cloud API later (OAuth app, membership).
3. **Food-photo accuracy.** Estimates are rough. Mitigations: an editable result, a confidence score, and quick re-logging of recent foods.
4. **Privacy.** All data stays on the phone. Backups and export are the user's choice.

## Phases
- ~~Phase 0: verify Google Health API access~~ → resolved by using Health Connect (see above). Still open: choose the AI model.
- **Phase 1: Android core (done 2026-10-22).** Food log (manual + recent foods), daily targets, weight log, Health Connect read (steps, calories burned, sleep, resting HR, HRV, weight, workouts), Today, Trends, Settings, CSV export.
  - Checked on the phone: logging and editing food, the sync, every Trends chart and its tap readout, and Settings with target suggestions.
  - Not yet run on the phone: CSV export, and sharing food to Health Connect, which needs the user's OK.
  - The debug-only sample-data writer was removed, so test data can never end up in real Health Connect.
- **Phase 2: Food photos, on-device (model chosen 2026-10-23, build not started).** The user chose a calorie-specific model that runs on the phone, so nothing is sent anywhere and there's no API key.
  - **Model: CalorieK, ONNX fp32 (375 MB).** We benchmarked 4 models on 4 test sets ([docs/food-model-benchmark.md](docs/food-model-benchmark.md)).
    - It was best on every everyday-food test: 158 kcal MAE on MM-Food phone photos and 191 on HelloFresh dinners.
    - It takes 0.2 s per photo on the PC's CPU.
    - Calorie Lens only wins on cafeteria trays; the 2B VLMs were worse and 1.5 GB.
  - **Known limits:**
    - It's off by about 25–35% per meal and underestimates big meals by about 170 kcal.
    - It gives no food names.
    - Plain int8 loses accuracy, and naive fp16 overflows.
  - **Status (2026-10-23):**
    - **Done:** it runs on the Pixel 9 in **0.55 s per photo** (ORT CPU; XNNPACK is slower), and the results match the PC.
    - **Built:**
      - In Log food: Take photo, Choose photo, and "Share to Health" from the gallery.
      - The estimate is shown as a range (±max(119 kcal, 30%)).
      - A portion question (Smaller / As estimated / Bigger / Much bigger) scales kcal and macros.
      - You name the food and edit anything before saving; nothing is saved automatically.
      - A "Photo estimates" card in Settings shows whether the model is installed.
    - **Model delivery for now:** adb into the app's private storage (`files/models/`). An in-app download comes later.
    - **Verified on the phone (2026-10-23):** I shared a HelloFresh taco photo (true 1,026 kcal) into the app.
      - The sheet opened and showed ≈637 kcal (446–829) in 0.3–0.4 s, an underestimate as the benchmark predicted.
      - "Much bigger" changed it to 956 (669–1,243).
      - Naming the entry enabled Save, the entry appeared under Snack, and Delete removed it.
      - Values are shown as whole numbers, and the portion chips scroll.
    - **Known gaps:**
      - A share *delivered to an already-running instance* (adb `am start`) never reached the app. A share sheet starts a new instance (tested: works), so this matters only for odd launchers.
      - The camera and the photo picker weren't driven in testing, to avoid showing personal gallery photos. They use the standard Android contracts.
      - The auto-picked meal at 4–5 pm is "Snack".
  - **Later:** learn a personal correction factor from the user's edits, shrink the model (mixed fp16 or calibrated int8), and add an in-app model download.
  - **Optional:** test on NutritionVerse-Real (weighed real meals, Kaggle) once the user provides a Kaggle token.
- **Phase 3: WHOOP without a subscription → separate app and repo (proposal 2026-10-24).** It would read the strap over Bluetooth, compute recovery, strain and sleep on the phone, and write the results to Health Connect, where this app already reads them. See [docs/strap-companion-proposal.md](docs/strap-companion-proposal.md). This waits on the user's answers about strap model, pairing and name.
- **Phase 4: Activities.** Custom activity builder (types, goals, streaks) fed by Health Connect exercise sessions.
- **Phase 5: Insights + optional AI coach.** Eaten vs. burned calibration, sleep vs. recovery. Also a home-screen widget.

## Open questions for you
1. Calorie goal: weight loss, maintenance or gain? Any protein floor? (Defaults for now: 2,200 kcal and 140 g protein, editable in Settings.)
2. Do you want a Telegram bot for logging as well, or just the in-app chat?
3. ~~Which AI provider for food photos?~~ Answered: an on-device, calorie-specific model, so no API key. CalorieK was chosen by benchmark.
4. ~~Phone model?~~ Answered: Pixel 9, Android 17.
