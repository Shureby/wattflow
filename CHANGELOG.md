# Changelog

## [1.0] - 2026-07-08

First stable release.

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
