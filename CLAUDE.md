# Health: Agent Guide

Health is a personal, local-first Android health app. It has three parts:
- A food and calorie log, including an **on-device food-photo calorie estimate** (CalorieK, run with ONNX Runtime).
- Sleep, activity, heart rate and weight read from **Health Connect**, which is fed by Fitbit, WHOOP, Strava, Hevy and the phone itself.
- Trends that put the two side by side.

The app is Kotlin + Jetpack Compose in [android/](android/). Research and the model benchmark live in [docs/](docs/) and [tools/food-bench/](tools/food-bench/).

**This file is a router, not a manual.** Read the linked topic doc when a task touches its area. Everything under **Hard Constraints** is non-negotiable.

## Hard Constraints (never violate)
1. **Nothing leaves the phone.**
   - The app has **no network access**: the manifest strips `INTERNET` and `ACCESS_NETWORK_STATE`, which ONNX Runtime asks for.
   - Don't add analytics, crash reporting, remote config or any network call.
   - Don't add a dependency that needs the network without the user's explicit approval.
   - Before a release, check the merged manifest: `android/build/intermediates/merged_manifest/debug/.../AndroidManifest.xml` must contain no `INTERNET`.
2. **AI estimates are suggestions, never facts.**
   - Show a photo estimate as a **range**, with the portion question and editable fields.
   - **Never auto-save it.** The user names the food and taps Save.
   - Don't present a single number as precise. CalorieK is off by about 25–35% per meal and underestimates big meals ([docs/food-model-benchmark.md](docs/food-model-benchmark.md)).
3. **Never write test or sample data into the user's real Health Connect**, and leave nothing behind on their phone.
   - Debug tools that write records were removed on purpose; don't bring them back.
   - On-device tests create entries named `TEST …` and **must delete them** afterwards (check with `run-as … databases/health.db`).
4. **Calories burned from Health Connect are not all real tracking.** Health Connect fills days that have no device data with a resting-burn estimate under the source `android`. Use `DailyMetric.trackedBurn()` (in `ui/trends/TrendsViewModel.kt`) for anything averaged or compared.
5. **Never commit model weights, datasets or personal data.**
   - Keep `.onnx`, `.gguf`, `.pt` and `.safetensors` files out of git; they're git-ignored.
   - No absolute paths, usernames, emails or health records in code, tests, docs or fixtures.
6. **Room schema changes need a migration.** The schema is exported to `android/schemas/`. Bump `AppDatabase.version` and add a `Migration`; never use a destructive fallback, because the database is the user's only copy of their food log.
7. **Plan before building.** Don't start a new phase in [PLAN.md](PLAN.md) until the user approves it. Confirm before any outward-facing action: pushing, publishing, or sending data to an external service.

## Document every link the user gives
Whenever the user shares a link (a tweet, repo, article, docs, anything):
1. Fetch and read it. If it's blocked, use a mirror or search, and say so.
2. Add an entry to [docs/sources.md](docs/sources.md) with:
   - the **original URL**
   - the date accessed
   - the author and title
   - a **summary of its content**
   - **what we take from it** for this project
3. Include any follow-up links you opened because of it, as their own entries or sub-bullets, with their URLs.
4. If the content changes the plan, update [PLAN.md](PLAN.md) too.
5. Mark unverified claims as unverified, and note how reliable the source is (for example, vendor blog vs. official docs).

Do this in the same turn the link is received, without waiting to be asked.

## Verification: Definition of Done
Run **`./init.sh`** before claiming a change is done. It's the same gate CI runs: assemble the debug APK and run the unit tests.

| Change | Also do |
|---|---|
| Kotlin / logic | `./init.sh` |
| UI or runtime behaviour | Check it on the device (below) in **both light and dark** themes |
| Photo model / preprocessing | Run `BenchActivity` on the phone and compare with the PC results ([docs/photo-estimates.md](docs/photo-estimates.md)) |
| Health Connect reading | Sync on the device, then inspect the local database |
| Room schema | Add a migration and test the upgrade from the previous version |

**Checking on the device (Windows, Git Bash):**
- Use `$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe`.
- Prefix adb commands with `MSYS_NO_PATHCONV=1`; otherwise `/sdcard/...` gets rewritten into a Windows path.
- Read and write the app's private files with `adb exec-out run-as dev.healthapp …` and `adb exec-in run-as dev.healthapp sh -c 'cat > …' < file`. Files pushed into `Android/data/` belong to the shell user, and the app can't read them.
- Don't drive the camera or the photo picker while taking screenshots, because they would expose the user's gallery. Test photo flows by sharing a staged image through the app's own FileProvider (`content://dev.healthapp.files/photos/…`).
- Ask before using the phone if the user may be using it for something else.

## Architecture at a glance
- **Stack:**
  - Kotlin 2.3, Compose and Material 3, AGP 8.13, minSdk 26 / targetSdk 36.
  - Room with KSP, DataStore, and Health Connect client 1.1.0.
  - ONNX Runtime Android 1.31.
- **DI and layers:** a hand-rolled `AppContainer` (no Hilt). One ViewModel per screen exposes a single `StateFlow` built with `combine`. DAOs return `Flow`s.
- **Single module:** [android/](android/) is the application module; there's no `:app`.
- **UI:**
  - A fixed, validated palette in `ui/theme/Theme.kt`, based on the reference palette from the dataviz skill. No dynamic colour, because chart colours must stay stable.
  - Cards are a surface with a hairline border.
  - Charts come from the in-house `TimeChart`: one axis, a tap/drag readout, and reference lines.
- **Versioning:** `versionName` / `versionCode` live in `android/build.gradle.kts`, using SemVer.

## Topic docs: read when the task touches the area
| Area | Doc | Read when |
|---|---|---|
| Plan, phases, open questions | [PLAN.md](PLAN.md) | Scoping any feature |
| Code structure and data flow | [docs/architecture.md](docs/architecture.md) | Adding screens, DAOs, sync, or DI changes |
| Visual design and charts | [docs/design.md](docs/design.md) | Any UI or chart work |
| Photo calorie estimates | [docs/photo-estimates.md](docs/photo-estimates.md) | Touching `ml/`, the photo flow, or model installation |
| Model benchmark | [docs/food-model-benchmark.md](docs/food-model-benchmark.md), [tools/food-bench/README.md](tools/food-bench/README.md) | Evaluating or replacing the food model |
| Research sources | [docs/sources.md](docs/sources.md) | Before citing a claim, and whenever the user shares a link |
| Privacy promises | [PRIVACY.md](PRIVACY.md) | Any change that touches data, permissions or dependencies |

## Commits
- Use Conventional Commits; see [.gitmessage](.gitmessage).
- Scopes: `ui`, `health`, `photo`, `db`, `trends`, `settings`, `bench`, `build`, `docs`.
- Keep the version untouched unless you're making a release.
