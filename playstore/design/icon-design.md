# WattFlow Icon Design

Chosen: **Battery Pulse** (candidate B), 2026-07-09.

## Candidates considered

| | Concept | Verdict |
|---|---|---|
| A | Flow Bolt — bolt + wave lines on purple gradient | Runner-up; generic bolt |
| **B** | **Battery Pulse — battery outline + flow dots entering** | **Chosen: mirrors the in-app charge animation, icon = feature** |
| C | Waveform W — brand initial as oscilloscope trace | Distinctive but abstract |
| D | Power Gauge — meter arc + bolt needle, light background | Weak at small sizes |

## Battery Pulse spec

- Background: `#1E1B33` (deep indigo)
- Flow dots: `#A78BFA` at 35% / 65% / 100% alpha, entering from the left
- Battery outline + tip: `#EDE9FE`
- Charge fill: `#A78BFA`
- Geometry lives in `app/src/main/res/drawable/ic_launcher_foreground.xml`
  (108-viewport adaptive icon, content scaled 0.72 into the safe zone).
  A monochrome layer (`ic_launcher_monochrome.xml`) supports Android 13+
  themed icons.

## Derived assets

`playstore/assets/generate.py` re-renders the same geometry with Pillow:

- `icon-512.png` — Play Store listing icon (512×512)
- `feature-graphic-1024x500.png` — Play Store feature graphic

Regenerate after any icon change: `python generate.py` in `playstore/assets/`.
