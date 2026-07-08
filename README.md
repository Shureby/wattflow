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
- **Zero permissions** — no network, no ads, no tracking. Everything stays on your device.

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

## Build

Requirements: JDK 17+, Android SDK 34.

```bash
./gradlew assembleDebug
# APK at app/build/outputs/apk/debug/app-debug.apk
```

Minimum Android version: 8.0 (API 26).

## Support

If this app is useful to you, consider buying me a coffee ☕

<a href="https://buymeacoffee.com/williamoz"><img src="https://img.buymeacoffee.com/button-api/?text=Buy me a coffee&emoji=☕&slug=williamoz&button_colour=FFDD00&font_colour=000000&font_family=Inter&outline_colour=000000&coffee_colour=ffffff" alt="Buy Me A Coffee" height="40"></a>

## License

[MIT](LICENSE) — free to use, modify, and distribute.
