# Insights, coach and widget (Phase 5)

Everything here is plain arithmetic and fixed rules over the local database: no model, no network. Every output is
a suggestion with its uncertainty, never a diagnosis. Code: `domain/Insights.kt` and `domain/Coach.kt` (pure,
tested in `InsightsTest.kt`), shown by `ui/insights/` and the home-screen widget in `widget/TodayWidget.kt`.

## Eaten vs burned (`Insights.calibrate`)
Over the last 28 complete days:
- **Logged balance** = mean of (food eaten − tracked burn) over days that have both. Burn uses
  `DailyMetric.trackedBurn()`, so Health Connect's resting-burn filler never counts.
- **Scale balance** = the least-squares slope of the weigh-ins (last one each day) × 7,700 kcal/kg, with a range of
  ±2 standard errors of the slope, and never narrower than ±150 kcal/day.
- **Maintenance** = average eaten − scale balance, shown as a range.
- **Verdict**: if the logged balance sits inside the scale range, the two agree. Otherwise the card says which way
  they disagree and the usual reasons: unlogged food or over-estimated burn (or the reverse).
- Needs 14 days with both food and tracked burn, 4 weigh-ins, and weigh-ins spanning 14 days.

Why a range and these words: wrist devices estimate energy expenditure poorly (no device under 20% error in
Shcherbina et al. 2017), and 7,700 kcal/kg is a rule of thumb that ignores body composition and dynamic adaptation
(Hall 2008; Hall & Chow 2013). Short windows are dominated by water and glycogen.

## Sleep and your mornings (`Insights.sleepLink`)
Over 90 days, each day's time asleep (sessions attributed to the wake-up day, naps included, as in Trends) is paired
with that day's resting HR and HRV. Nights are split at your own median; the card compares the mornings after the
shorter and the longer half.
- A difference is called **clear** only when it is bigger than 2 standard errors (Welch). Pearson r is computed too.
- Needs 14 nights with a morning reading and at least 5 on each side.
- "No clear difference" is an expected answer: in large wearable datasets the link between sleep duration and next-day
  HRV is small and differs between people (vendor analyses, not peer reviewed; see sources).

## Coach rules (`Coach.tips`)
Inputs: the last 7 complete days, with baselines = medians of the 28 days before (needing 7 days of data).
Highest priority first:

| Rule | Fires when | Priority |
|---|---|---|
| Log a few more days | food logged on fewer than 4 of 7 days (replaces the food rules) | 50 |
| Extra load | resting HR ≥ baseline + 3 bpm on each of the last 3 days, or HRV ≤ 85% of baseline on each | 48 |
| Short on sleep | 4+ nights recorded and the average is under 7 h | 45 |
| Protein ran low | under 90% of the protein target on 4+ logged days | 40 |
| Goal nudge | an activity goal not met, 2 days or fewer left in the week | 38 |
| Above calorie target | logged average more than 15% over target | 35 |
| Weight moving away from goal | trend rate ≥ 0.1 kg/week in the wrong direction (goal weight set) | 32 |
| Under calorie target | logged average more than 25% under target | 30 |
| Fewer steps | 7-day average under 70% of baseline | 20 |
| On track for weight goal | trend moving toward the goal at ≥ 0.1 kg/week | 10 |
| A steady week | nothing else (besides "on track") fired | 1 |

The recovery tip names common causes (short sleep, alcohol, hard training, stress, a cold) and says it is not a
diagnosis.

## Widget
`TodayWidget` is a plain `RemoteViews` widget (no Glance dependency, nothing new to download): calories eaten
against the target with a meter, protein, steps, and the coach's top tip. It reads only the local database. The
system refreshes it every 30 minutes; the app also refreshes it after food is added, edited or deleted and after
each sync. Its colours follow the phone's own dark mode (widget resources use `values-night`), not the in-app
Appearance setting. Tapping it opens the app.
