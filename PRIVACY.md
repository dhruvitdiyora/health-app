# Privacy

**Last updated: 23 October 2026**

Health is a personal app with no account and no server. Your data stays on your phone.

## No network access
The app does not have the Android `INTERNET` permission, so it **cannot** send anything anywhere: not your food
log, not your Health Connect data, not your photos. There is no analytics, crash reporting or advertising.

## What it reads
From **Health Connect**, only the types you allow: steps, active and total calories burned, sleep, resting heart
rate, heart rate variability, weight, and workouts — plus, if you allow it, data older than 30 days. These come
from apps such as Fitbit, WHOOP, Strava or Hevy that you have connected to Health Connect yourself.

## What it writes
- A local database on your phone (food you log, weights you enter, and a copy of the Health Connect data above so
  charts are fast and history survives).
- **Only if you turn on "Share food with Health Connect"**: the food you log, as nutrition records, so apps you
  allow (for example WHOOP) can see calories eaten. Turning it off stops new writes.

## Photos
A meal photo is estimated **on the phone** by a model stored in the app's private storage. Camera photos are kept
in the app's cache only; photos you pick or share are read once and not copied into the database. Photos are never
uploaded.

## Backups, export and deletion
- Android cloud backup is **disabled** for this app (`allowBackup="false"`), so its data isn't copied to Google Drive.
- Settings → *Export all data* writes a zip of CSV files to a place you choose.
- Uninstalling the app deletes its database, cache and model. Data in Health Connect is managed by Health Connect.

## Permissions
| Permission | Why |
|---|---|
| `health.READ_*` (steps, calories, sleep, resting HR, HRV, weight, exercise, history) | Show your wearable data |
| `health.WRITE_NUTRITION` | Only for the optional "Share food with Health Connect" |

Camera and photo access use the system camera app and photo picker, which need no permission.
