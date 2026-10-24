# Proposal: Strapless, multi-device recovery, strain and sleep without subscriptions

**Status: proposal, 2026-10-24. Nothing is built.**

**Name:** Strapless (separate app and repo).

**Integration:** self-contained first. It reads data from other sources but doesn't write anything back to Health Connect yet.

## Revised after the user's answers (2026-10-24)
**The user's device is a Fitbit Air, not a WHOOP.** They want one app that works across many devices.

What that means:
- **Fitbit can't be read directly.** Its Bluetooth link is end-to-end encrypted (it took a hardware attack to read it, arXiv 1706.09165), and Gadgetbridge supports no Fitbit devices. Its data reaches the phone through the Google Health app and then **Health Connect**, which already works in the Health app.
- **The Fitbit Air needs no subscription for its core data.** Google Health Premium only adds AI coaching and deeper insights. Strapless's value is computing **WHOOP-style recovery, strain and sleep scores itself, for any device**.

**Device sources, in order:**
1. **Health Connect,** for any device whose app writes to it: the Fitbit Air today, plus Pixel Watch, Samsung, Garmin, Oura and others. This is v0, and it can be tested now.
2. **Standard Bluetooth heart-rate straps** (chest straps, and bands that broadcast heart rate). This gives live heart rate plus R-R intervals, so true HRV. It needs one such device to test with.
3. **Direct WHOOP 4.0 / 5.0 drivers,** following the research below. These wait until a strap is available to test on.
4. **Possibly importing Gadgetbridge's database export,** covering Amazfit, Xiaomi, Garmin, Polar and others with no BLE work of our own. This is unverified and needs checking.

The WHOOP-specific research below still applies to source 3.

## Original WHOOP-focused research
The user first asked for a NOOP-style alternative that works **without a WHOOP subscription**, built as a **new app in its own repo**. This section records that research. Sources are in [sources.md](sources.md#whoop-strap-without-a-subscription-2026-10-24).

## Why a separate app
- **It does one job:** talk to the strap, keep the raw data, and compute metrics.
- **It writes standard records to Health Connect:** heart rate, HRV, resting heart rate, sleep sessions with stages, and exercise. This Health app already reads all of those, so the two connect with no code changes here, and any other app can use the data too.
- **Bluetooth needs permissions** (scan and connect, plus a foreground service while syncing) that the Health app shouldn't carry.

## What exists today, and what we may reuse
| Project | Platforms | Straps | Licence | Can we reuse code? |
|---|---|---|---|---|
| [NOOP](https://github.com/ryanbr/noop) (Zhoop is a fork) | iOS, macOS, **Android** | 4.0 full (offload validated); 5.0/MG live heart rate only on Android | PolyForm Noncommercial 1.0.0 | **No**: not compatible with AGPL. Its **protocol docs** are the best reference, and protocol facts may be re-implemented |
| [my-whoop](https://github.com/johnmiddleton12/my-whoop) | iOS plus a server | 4.0 | none (all rights reserved) | **No**. Read `FINDINGS.md` for facts |
| [goose](https://github.com/b-nnett/goose) | iOS (Rust core) | 5.0 | none | **No** |
| [whoop-reader](https://github.com/christianmeurer/whoop-reader) | Python | 4.0 | **MIT** | **Yes**, with attribution |
| [Gadgetbridge](https://codeberg.org/Freeyourgadget/Gadgetbridge) | Android | no WHOOP support (request #5731) | **AGPL-3.0** | **Yes** (same licence), for Bluetooth patterns. Alternative: contribute WHOOP support there |

## What the strap allows without a subscription
This comes from the docs above and still has to be verified on the user's strap.

- **Pairing needs no WHOOP account.**
  1. Scan for the strap's service: `61080001-…` for WHOOP 4.0, `fd4b0001-…` for 5.0/MG.
  2. Connect.
  3. One confirmed write of `GET_BATTERY_LEVEL` (command 26) triggers "just works" bonding.

  A strap bonds to one phone or app at a time, so it must be unpaired from the WHOOP app first.
- **WHOOP 4.0:** live heart rate, plus **historical offload**. The strap stores heart rate, R-R intervals, temperature and motion while disconnected. NOOP reports this as validated on a real 4.0.
- **WHOOP 5.0 / MG:** different framing and commands. NOOP's Android app has live heart rate only, and history support is less mature.
- **Standard Bluetooth services:** the strap also exposes the standard heart-rate (`0x2A37`) and battery (`0x2A19`) characteristics.
- **Never send destructive commands:** firmware load, force-trim, ship mode, power-cycle, fuel-gauge reset or DFU. NOOP excludes them so a strap can't be bricked, and we would allow only a list of known-safe commands.

## Proposed app, v0 to v1
1. **Spike (days):** scan, connect, bond, read live heart rate and battery, and log raw packets to a file, all on the user's strap. This decides feasibility before any UI work.
2. **Offload and storage:**
   - Pull the stored history, starting with 4.0.
   - Keep both the raw frames and the decoded samples (heart rate, R-R, temperature, motion) in Room.
   - Sync in a foreground service, so it can resume after interruption and never duplicates data.
3. **Metrics:** our own code based on published methods, with unit tests.
   - **Resting heart rate:** the lowest sustained 5-minute level overnight.
   - **HRV:** RMSSD (Task Force 1996), with artefact filtering.
   - **Sleep:** detection and staging from motion plus heart rate.
   - **Strain:** TRIMP (Edwards / Banister).
   - **Recovery:** HRV and resting heart rate against a personal baseline. It shows "calibrating" for the first nights.
   - **Respiratory rate,** if the data allows.
4. **Health Connect export:** heart rate series, HRV (RMSSD), resting heart rate, sleep sessions with stages, and exercise.
5. **Small UI:**
   - Screens: Today (recovery, sleep, strain), a strap screen (battery, last sync) and Settings.
   - Same principles as this app: local only, no network, estimates labelled as estimates.
6. **Later:**
   - WHOOP CSV import, for history from before the switch.
   - Standard Bluetooth heart-rate straps (Polar H10 and similar) for live heart rate and HRV.

## Rules the new repo would start with
- **AGPL-3.0,** which also lets us reuse Gadgetbridge code.
- **Clean-room implementation** from protocol *facts*. Never copy NOOP, my-whoop or goose code, and credit sources.
- **No network access.** No WHOOP trademarks or logos in the app name or icon, and a clear "unofficial, not a medical device" notice.
- **An allow-list of safe commands.** Nothing that changes firmware or strap state beyond what's needed to read data.
- **Legal note** (not legal advice): reading your own device for interoperability is broadly allowed under EU Software Directive 2009/24/EC Art. 6 and US 17 U.S.C. §1201(f). WHOOP's terms may say otherwise; that's the user's call.

## Answers so far (2026-10-24)
1. **Device:** a Fitbit Air, and the app should work across multiple devices. That makes Health Connect the first source, as above.
2. **Pairing:** "still active", which presumably means the Fitbit / Google Health app, since there's no WHOOP to pair.
3. **Name:** Strapless. AGPL-3.0 and public, like this repo (to confirm when the repo is created).
4. **Integration:** self-contained first. Strapless keeps its scores inside the app and doesn't write to Health Connect yet.

## Proposed v0 for Strapless (needs the user's go-ahead)
- **App shell:** an Android app (Kotlin, Compose, Room) with no network permission and an AGPL-3.0 licence, in a new public repo `strapless`.
- **Source:** a Health Connect reader for heart rate (the series), resting heart rate, HRV, sleep sessions with stages, steps, and exercise.
- **Scores:** our own code, unit-tested, based on published methods.
  - **Recovery:** HRV and resting heart rate against a personal 30-day baseline. It shows "calibrating" for the first nights.
  - **Strain:** a heart-rate TRIMP from the heart-rate series, using heart-rate zones from age or measured maximum.
  - **Sleep performance:** time asleep vs need, efficiency, and consistency.
- **UI:** Today (the three scores and how they're computed), trends, a sources screen (which device and app fed each metric), and settings.
- **Test with:** the user's Fitbit Air data, which the Health app already shows is present (sleep stages, resting heart rate, HRV, steps).
- **Then:** the standard Bluetooth heart-rate driver, and WHOOP drivers once there's hardware to test on.
