# ⚡ WattFlow

[![Platform](https://img.shields.io/badge/platform-Android%208.0%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![License: MIT](https://img.shields.io/badge/license-MIT-yellow.svg)](LICENSE)
[![Buy Me a Coffee](https://img.shields.io/badge/Buy%20Me%20a%20Coffee-support-FFDD00?logo=buymeacoffee&logoColor=black)](https://buymeacoffee.com/williamoz)

**See the real watts flowing in and out of your battery — wired or wireless, in real time.**

[简体中文](README.zh-CN.md)

<p align="center">
  <img src="docs/screenshot.png" width="300" alt="App screenshot" />
</p>

## Features

- **Live power readout** — voltage × current sampled every second, shown in watts
- **Wired & wireless** — detects AC / USB / Wireless / Dock sources automatically
- **Animated visuals** — current dots flowing along the cable when wired, pulsing waves when wireless, outflow animation when discharging
- **Peak In / Peak Out** — tracks maximum charging and discharging power of the session
- **Live graph** — last 60 seconds of power history, discharge shown below the zero line
- **Battery stats** — voltage, current, temperature, and charge level inside the battery icon
- **12 languages** — auto-follows system, with a manual override in settings
- **No ads, no analytics** — nothing about you ever leaves this device.

## How It Works

Power is computed from Android's public `BatteryManager` API:

```
watts = EXTRA_VOLTAGE (mV) × BATTERY_PROPERTY_CURRENT_NOW (µA)
```

The app normalizes two well-known OEM quirks:

- Some devices report current in **mA instead of µA** (values are scaled by a plausibility heuristic)
- Sign conventions differ per vendor (normalized to: charging = positive)

### Accuracy Disclaimer

The displayed value is **battery-side power** — what actually enters or leaves the battery cell. Wall power is always higher due to:

- Conversion losses (~10–15% wired, ~30–40% wireless)
- Power consumed directly by the device (screen, CPU, radios) while charging

There is no public Android API for adapter-side wattage, so this is the most honest number an unrooted device can show.

### Reverse Charging

When your phone powers another device (reverse wireless charging, OTG), Android exposes no "reverse charging" flag — the app shows it as battery drain with the correct wattage.

## Languages

English, 简体中文, 繁體中文, Español, العربية, Bahasa Indonesia, Português, Français, 日本語, 한국어, Русский, Deutsch

Translations live in `app/src/main/res/values-*/strings.xml` — corrections and new languages are welcome via PR.

## What's New — v1.7

- **1.7.1**: Fixed the Live tab's stats row breaking into garbled text
  at window widths just above the Compact→Medium breakpoint in
  landscape — mainly hit on Chromebook's freeform-resizable windows,
  not any fixed phone/tablet size
- **1.7.0**: Large-screen adaptation for tablets and foldables. Navigation
  switches from a bottom bar to a side rail at tablet widths, in either
  orientation; History and Reports no longer stretch edge-to-edge on
  wide screens; a status-bar overlap bug and a Settings tab layout bug
  found during this pass are fixed too; Live tab content centers
  vertically on tall screens instead of sticking to the top

## What's New — v1.6

- **1.6.1**: Sleep drain's coverage line now says "Tracked the whole
  night" once a night is within 1% of the full window, instead of a
  minute-precise count that could look nitpicky over a few seconds
- **1.6.0**: Added a Reports tab (Live / History / Reports / Settings).
  Energy Ledger, Sleep Drain, and Battery Health move there from
  History's chip row, alongside a new view-only list of saved Charger
  Benchmark results. History goes back to one job — filtering and
  exporting sessions. Running a new benchmark still lives on Live.
  Navigation-only change; no report's calculations changed

## What's New — v1.5

- **1.5.3**: Battery health trend rebuilt — baseline is the running max
  of non-anomalous readings, not the first-ever one; outlier detection
  is now rate-of-change based (MAD) so a long calendar gap isn't judged
  as harshly as a short one; a big same-direction jump asks "did you
  replace your battery?" instead of guessing; excluded readings stay
  visible (struck through, explained), never deleted
- **1.5.2**: Energy ledger rebuilt — daily in/out shown as a signed % of
  a full charge instead of raw Wh, with a `→` that flips sides for in
  vs out; a day over 100% gets a tappable ⓘ breaking down the sessions
  behind it (tiny 0%-rounding ones left out); columns are now aligned
  and colored to match History's existing charge/discharge convention.
  Also fixed a History bug where a same-level session could show the
  wrong color
- **1.5.1**: Sleep drain report rebuilt — drain shown as a signed % of a
  full charge instead of raw Wh; nights held on the charger get their
  own card showing when it hit 100% and how long it stayed there, with
  a battery-health tip past an hour; part-discharge/part-charge nights
  show the charging portion as a time-stamped aside; coverage reads as
  plain time with a ⓘ. Floating overlay now uses `+`/`−` instead of
  `▲`/`▼`, matching the widget

## What's New — v1.4

- **1.4.0**: targets Android 16 (API 36) — no visible changes, internal
  testing only this cycle

## What's New — v1.3

- **Charger benchmark**: 60-second test grades your charger + cable
  (avg/peak watts, stability, A–F); Pro saves and compares results
- **Floating watts overlay** (Pro): draggable live-watts pill over other apps
- **Energy ledger, sleep drain report and battery health trend** (Pro)
- **Dual-cell (2S) hint + manual ×2 correction** for phones that
  under-report power during fast charging
- **Landscape two-pane layout**, size-adaptive widget, settings tab,
  readable chart peak labels

- **1.3.1–1.3.2**: in-app language now works on Play-installed builds;
  widget picker offers three preset sizes with previews
- **1.3.3–1.3.5**: battery alerts clear themselves once followed; widget
  picker entries renamed Small/Medium/Large with clean transparent
  previews; 4×1 widget squeezes to 3 columns (with a guaranteed gap
  between its text columns); notification follows a language switch
  instantly; dual-cell report opens a prefilled GitHub issue form
- **1.3.6**: a session cut short by the process being killed
  mid-recording is now recovered from a checkpoint and shown in
  History as interrupted, instead of silently vanishing
- **1.3.7**: a recovered checkpoint whose battery level moved the wrong
  way for its direction is discarded instead of recorded; any such
  sessions already recorded are purged on update
- **1.3.8**: merged History sessions that span a source change now say
  "Mixed" instead of guessing; Peak In/Out gets an ⓘ on the Live tab
  and a "Today's Peaks" header on the widget to clarify it's two
  different scopes (current streak vs. today), not a bug
- **1.3.9**: the small widget's picker-preview image now matches the
  medium/large ones — all three generated the same way instead of
  looking inconsistent next to each other
- **1.3.10**: widget resize floors (`minResizeWidth`/`Height`) declared
  consistently across all three sizes; small widget's preview mock
  temperature fixed (was briefly 99.9°C — nobody's phone should get
  that hot)

Full history in [CHANGELOG.md](CHANGELOG.md).


## FAQ

**Why is the number lower than what my charger shows?**

Three real reasons — not a bug:

1. **Different measurement points.** WattFlow shows battery-side power (what actually enters the battery). Your charger shows its own output, upstream of the whole conversion chain.
2. **Conversion losses.** ~10% wired, 30–40% wireless (coil coupling turns into heat). A 40 W wireless pad delivering ~25 W into the battery is completely normal.
3. **The system eats first.** Screen, SoC and radios draw power directly from the charge path while you watch the app — that share never reaches the battery.

Bonus: a 100 W charger doesn't force 100 W. The phone draws only what its charging protocol negotiates at the current battery level, and tapers hard past ~70%.

## Build

Requirements: JDK 17+, Android SDK 35.

```bash
./gradlew assembleDebug
# APK at app/build/outputs/apk/debug/app-debug.apk
```

Minimum Android version: 8.0 (API 26).

## Support

If this app is useful to you, consider buying me a coffee ☕

<a href="https://buymeacoffee.com/williamoz"><img src="https://img.shields.io/badge/Buy%20Me%20a%20Coffee-%E2%98%95%20support-FFDD00?style=for-the-badge&logo=buymeacoffee&logoColor=black" alt="Buy Me A Coffee"></a>

## License

[MIT](LICENSE) — free to use, modify, and distribute.
