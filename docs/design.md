# Design

Quiet, data-first, dark-mode friendly. The data is the only loud thing on screen.

## Colour
All tokens live in `ui/theme/Theme.kt`. Values come from the dataviz skill's validated reference palette
(colour-blind checks pass for the first three categorical slots in both modes).

| Role | Light | Dark | Used for |
|---|---|---|---|
| Slot 1 blue | `#2A78D6` | `#3987E5` | protein, eaten kcal, weigh-ins, primary actions |
| Slot 2 orange | `#EB6834` | `#D95926` | carbs, burned kcal, weight trend, resting HR |
| Slot 3 aqua | `#1BAF7A` | `#199E70` | fat, HRV |
| Sleep (ordinal blue) | 600 / 450 / 250 + grey | 500 / 350 / 200 + grey | deep / REM / light / awake |
| Surfaces | `#FCFCFB` on `#F4F3F0` | `#1A1A19` on `#0D0D0D` | cards on page |

- **No dynamic colour** (Material You): series colours must mean the same thing everywhere.
- **Colour follows the entity**, never its position. Protein is always blue.
- **Text never wears a series colour** — values and labels use on-surface / muted ink; a dot, bar or line key
  next to the text carries identity.
- Status colours (good/critical) only with an icon or arrow, never alone.

## Components (`ui/components/`)
- `SectionCard` / `StatTile`: surface + 1 dp hairline ring (`viz.border`), 16 dp radius, no shadows.
- `Meter`: 8 dp track in a recessive step, fill in the series colour; past target shows a surface notch.
- `ProgressRing`: the calorie hero; the centre number is the only display-size figure on Today.
- `StageBar`: stacked sleep stages with 2 dp surface gaps between segments.
- `TimeChart`: daily series with **one y-axis** (never dual-axis — different units get separate charts), bars
  ≤ 24 dp with 4 dp rounded tops on a square base, 2 dp lines, 8 dp dots with a 2 dp surface ring, hairline
  gridlines, a reference line whose label sits on a surface pill, and a tap/drag crosshair whose readout lists
  every series ("values lead, labels follow").

## Copy
- Plain, short, sentence case. Say what a number is and where it came from ("Phone, Fitbit, Strava").
- Estimates say so: "≈ 637 kcal · likely 446–829", "A rough estimate: check it".
- Missing data is "–" or "No data", never 0.

## Check both themes
Every UI change is checked in light and dark on the device before it's done.
