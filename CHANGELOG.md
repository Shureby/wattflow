# Changelog

## [1.1] - 2026-07-09

- **Charge history & statistics** — sessions logged to a local database:
  time range, level change, average/peak watts, energy (Wh); summary card
  and 7-day energy chart
- **Background recording** (opt-in) — foreground service keeps recording
  while the app is closed, with live watts in the notification
- **"Why lower than the charger says?"** info dialog and README FAQ
- Split builds: **FOSS** (GitHub, everything free) and **Play**
  (Pro features via one-time in-app purchase)
- Privacy policy page; target SDK 35 (Android 15)

## [1.0] - 2026-07-08

First stable release of WattFlow.

- Real-time battery power monitoring (1s polling) via `BatteryManager`
- Wired (AC / USB / Dock) and wireless charging source detection
- Animated charge visuals: flowing current dots (wired), pulsing waves
  (wireless), outflow animation (discharging)
- Battery level percentage rendered inside the battery icon
- Peak In / Peak Out power tracking
- 60-second live power graph with discharge below the zero line
- Voltage / current / temperature stats
- OEM quirk normalization (µA vs mA units, current sign conventions)
- 12 languages with system-follow default and manual override
- Zero permissions required
