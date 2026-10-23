# Health

A personal, local-first Android app that puts **what you eat** next to **what your wearables measure** — and keeps
all of it on your phone.

- **Today** — calories eaten vs. your target and vs. calories burned, protein/carbs/fat, steps, workouts,
  last night's sleep with stages, resting heart rate and HRV against your 7-day average, weight.
- **Log food** — by hand, by re-logging a recent food with a portion size, or **from a photo**: an on-device model
  estimates calories and macros in about half a second and shows a range; you set the portion, name it and save.
- **Trends** — 7 days to 1 year: eaten vs. burned, sleep, steps, resting HR, HRV and weight with a smoothed trend.
- **Health Connect** — reads Fitbit, WHOOP, Strava, Hevy and the phone's own step counting; optionally shares the
  food you log back so other apps can use it.
- **Private by construction** — no account, no server, and **no internet permission**. Export everything as CSV.

> Personal project, not medical software. Photo estimates are rough (typically ±25–35% per meal and low on big
> meals); treat every number as a guide.

## Build and install

Requirements: Android SDK (API 36), JDK 21. Phone: Android 8+ (Health Connect is built in on Android 14+).

```bash
./init.sh                                   # build + unit tests (the same gate CI runs)
adb install -r android/build/outputs/apk/debug/health-app-debug.apk
```

Open the app → **Connect Health Connect** and allow the data types you want (include past data for a year of
history).

### Photo estimates (optional)
The 375 MB model isn't bundled. Export it with [tools/food-bench](tools/food-bench/README.md) and copy it into the
app's private storage — see [docs/photo-estimates.md](docs/photo-estimates.md). Without it the photo buttons explain
what's missing; everything else works.

## Documentation
| | |
|---|---|
| [PLAN.md](PLAN.md) | Goals, decisions, phases and status |
| [docs/architecture.md](docs/architecture.md) | Code layout and data flow |
| [docs/design.md](docs/design.md) | Colours, components, chart rules |
| [docs/photo-estimates.md](docs/photo-estimates.md) | The on-device food model: install, preprocessing, range |
| [docs/food-model-benchmark.md](docs/food-model-benchmark.md) | How the food model was chosen (4 models × 4 test sets) |
| [docs/sources.md](docs/sources.md) | Every source used, with what we took from it |
| [PRIVACY.md](PRIVACY.md) · [SECURITY.md](SECURITY.md) · [CONTRIBUTING.md](CONTRIBUTING.md) | |
| [CLAUDE.md](CLAUDE.md) | Guide for coding agents (hard constraints, verification) |

## License
[GNU AGPL-3.0](LICENSE). Third-party models and datasets keep their own licences (CalorieK: MIT; SigLIP2: Apache-2.0;
benchmark datasets: see [docs/food-model-benchmark.md](docs/food-model-benchmark.md)) and are not redistributed here.

## Credits
- [CalorieK](https://huggingface.co/Dralkh/CalorieK) (MIT) on [SigLIP2](https://huggingface.co/google/siglip2-base-patch16-224) (Apache-2.0)
- [ONNX Runtime](https://onnxruntime.ai), [Health Connect](https://developer.android.com/health-and-fitness/guides/health-connect), Jetpack Compose, Room
- Ideas from [caltrack](https://caltrack.levels.io) and [NOOP](https://github.com/ryanbr/noop) — see [docs/sources.md](docs/sources.md)
