# Changelog

## [1.2.2] - 2026-07-10

- **Session merging** — same-direction sessions separated by sampling gaps
  under 5 minutes now display as one (with a ×N segment badge). Screen-off
  power saving fragments sampling; a continuous drain no longer shows as
  a confusing pile of short rows. Stored data stays raw.
- **Raw sessions (strict)** geek toggle in Settings restores exact
  60-second-gap segmentation — presentation-only, switch anytime
- **Uninterrupted background sampling** (shown in geek mode): optional
  battery-optimization exemption with clear side-effect disclosure;
  declining it is fully respected
- Fixed energy over-counting across short sampling gaps

## [1.2.1] - 2026-07-10

Fixes from v1.2 field testing:

- History filter now uses proper charge/discharge terminology in all
  12 languages (was "On battery" phrasing, awkward in most)
- Settings moved to a dedicated screen (gear icon); alert sliders
  redesigned — full width, 5% detents, live bold percentage, purple
  charge / red low-battery
- Live tab is scrollable: no more overlapping content on tall screens
- Direction-specific empty state in history
- "Background recording" toggle explains foreground recording
- Sub-minute session durations show as <1m

## [1.2] - 2026-07-09

- **Battery alerts** — charge-full (80%) and low-battery (20%) notifications;
  thresholds adjustable in Pro; ⓘ explains why 80/20 protects battery health
- **Runtime estimate** — time to full while charging, time left while draining
- **Discharge history** — sessions now recorded in both directions, with a
  charging / on-battery filter
- **Session detail** — tap any session for its full power curve
- **Home screen widget** — watts, level and temperature at a glance
- **CSV export** (Pro)
- Full-charge baseline logging (groundwork for battery health trend)

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
