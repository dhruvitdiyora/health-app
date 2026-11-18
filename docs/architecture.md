# Architecture

## Layout

```
android/                          single Gradle module (the app); no :app subproject
  src/main/java/dev/healthapp/
    HealthApp.kt, AppContainer.kt   Application + hand-rolled DI (db, settings, health, sync, food, calories)
    MainActivity.kt                 Compose host; receives "Share to Health" images
    PrivacyActivity.kt              shown by Health Connect ("why does this app need…")
    data/db/                        Room: entities, DAOs, AppDatabase (v2, schema exported to android/schemas/)
    data/SettingsStore.kt           DataStore: targets, goal weight, share-food flag, last sync
    data/Export.kt                  zip of CSVs (food, weight, daily, sleep)
    data/health/                    Health Connect: permissions, reads, nutrition write, sync into Room
    domain/HealthMath.kt            pure functions (sleep stages, weight trend, balance), unit tested
    domain/Insights.kt, Coach.kt    eaten-vs-burned calibration, sleep vs mornings, coach rules (pure, tested)
    widget/TodayWidget.kt           home-screen widget (RemoteViews), refreshed after food changes and syncs
    domain/Activities.kt            workout categories, de-duplication, weekly goals and streaks (pure, tested)
    ml/CalorieEstimator.kt          CalorieK ONNX session, decode + preprocess, PhotoEstimate (with range)
    ui/                             HealthAppUi (tabs) + today/, activities/, insights/, trends/, settings/, components/, theme/
  src/debug/                        BenchActivity: on-device model benchmark (debug builds only)
  src/test/                         JVM unit tests (HealthMathTest, ActivitiesTest, InsightsTest) + Robolectric MigrationTest
tools/food-bench/                   PC scripts that benchmarked food-photo models (Python)
docs/                               plan support, research sources, benchmark, design notes
```

## Data flow

```
Fitbit / WHOOP / Strava / Hevy / phone ──► Health Connect (on device)
                                               │  HealthSync.sync(days): aggregate per day,
                                               │  sleep sessions, weight, HRV → Room
                                               ▼
User ─► Log food sheet ─► FoodRepository ─► Room ◄── DAOs (Flow) ◄── ViewModels (combine → StateFlow) ◄── Compose
            │ photo                                (optional) NutritionRecord ─► Health Connect
            ▼
       CalorieEstimator (ONNX, files/models/caloriek_fp32.onnx) ─► PhotoEstimate (range) ─► editable fields
```

- **Room is the app's store**; Health Connect is the source of truth for device data. `SyncController.sync()` runs on
  resume (at most every 5 min, `force` for pull-to-refresh): a full backfill at most every 20 h (365 days with the
  history permission, else 30), otherwise the last 7 days. Sleep and synced weights are *replaced* within the window
  so deletions in Health Connect propagate; manual weights are never touched.
- **Days are ISO strings** (`yyyy-MM-dd`, local time). Sleep belongs to the day you woke up.
- **One aggregate call per sync** (`aggregateGroupByPeriod`, 1-day slices) only asks for metrics whose read permission
  is granted (an ungranted metric throws). `DailyMetric.sources` lists every app that contributed to that day.
- **Food sharing to Health Connect** is opt-in (Settings) and needs `WRITE_NUTRITION`; `FoodRepository` mirrors
  add/update/delete and keeps `hcRecordId`.

## State and UI
- One ViewModel per tab (`TodayViewModel`, `ActivitiesViewModel`, `InsightsViewModel`, `TrendsViewModel`, `SettingsViewModel`), created with `viewModelFactory`
  and `context.container`. Each exposes one `StateFlow` built by `combine` + `flatMapLatest` on the selected day/range.
- Permission launchers live in `HealthAppUi` (read permissions) and `SettingsScreen` (nutrition write).
- The photo flow is entirely inside `FoodSheet`: camera (`TakePicture` into `cache/photos/` via the `…files`
  FileProvider), picker (`PickVisualMedia`), or a shared image (`MainActivity` → `TodayScreen` → `FoodSheet`).

## Activities (Phase 4)
- **Sync**: `HealthSync` copies every `ExerciseSessionRecord` in the window into `exercise_session` (day = the day it
  started), replacing the window so deletions in Health Connect propagate. Every app's copy is stored as-is.
- **De-duplication** happens when reading (`Activities.dedupe`): two sessions are the same workout when they overlap
  by at least 50% of the shorter one. The longest copy is kept (a watch usually starts before a phone app and stops
  after it) and the others are listed as "also recorded by". Back-to-back sessions (a run then a cool-down walk)
  overlap little and stay separate. This answers the Fitbit/Strava double-count risk in PLAN.md for workouts;
  daily step and calorie totals were already de-duplicated by Health Connect's own aggregation.
- **Goals** (`activity_goal`): per category or any activity, sessions or minutes per week. Weeks run Monday to Sunday.
  The streak counts weeks in a row the goal was met, ending with this week if it is already met, otherwise last week:
  the unfinished week never breaks a streak. Weeks before the first stored session are unknown, not missed.
- **Schema**: v1 → v2 adds the two tables (`AppDatabase.MIGRATION_1_2`); `MigrationTest` builds a v1 database from
  `schemas/…/1.json`, fills it, and opens it with the migration (Room validates the result). The schemas are added to
  **debug** assets only, because Robolectric reads them from the merged assets; Room's `MigrationTestHelper` can't
  open files by path on Windows.

## Insights (Phase 5)
See [insights.md](insights.md). `insightsFlow(db, settings, today)` builds the whole tab from Room and is reused by
the widget, so both always agree.

## Things that bit us (keep)
- Android 17 / Pixel 9 uses 16 KB pages: native libs must be 16 KB aligned (DataStore ≥ 1.3, graphics-path 1.1,
  ORT 1.31 are). Check with `zipalign -c -P 16 -v 4 <apk>`.
- Files adb pushes into `Android/data/<pkg>/` belong to `shell` and are unreadable by the app — use private storage
  and `run-as`.
- A SEND intent delivered to an already-running instance via `adb am start` never reached the app (neither
  `onNewIntent` nor `addOnNewIntentListener`); real share sheets start a new instance, which works.
- ONNX Runtime's manifest requests `INTERNET`; it is removed in ours (`tools:node="remove"`).
