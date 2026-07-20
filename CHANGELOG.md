# Changelog

## [1.3.9] - 2026-07-20

- Fixed: the small widget's picker-preview image (1.3.8) still used a
  hand-mocked-up-elsewhere asset while the medium/large ones were
  regenerated to match — now all three are produced the same way, so
  they no longer look inconsistent side by side

## [1.3.8] - 2026-07-20

- Fixed: a merged History session spanning a source change (e.g. wired
  then wireless) silently showed only the first segment's source —
  now shows "Mixed" when segments disagree
- Clarified the difference between the Live tab's Peak In/Out (resets
  every time you unplug, plug back in, or switch source) and the
  widget's Peak In/Out (covers the whole day): Live tab gets an ⓘ
  explaining the scope, widget (M/L sizes) gains a "Today's Peaks"
  header above the figures

## [1.3.7] - 2026-07-20

- Fixed: a stale checkpoint recovered after a process kill (see 1.3.6)
  could replay as a session whose battery level moved the wrong way for
  its direction (e.g. a "charging" session that shows a level drop) —
  such checkpoints are now discarded instead of recorded, and any
  already-recorded sessions like this are purged on update

## [1.3.6] - 2026-07-20

- A charge/discharge session interrupted by the process being killed
  (OS/OEM background killer, OOM, crash) mid-recording no longer
  vanishes silently. Progress is checkpointed every 15s; on next
  launch, the leftover checkpoint is recovered and inserted as an
  interrupted session — History flags it with a warning label instead
  of the time gap simply disappearing

## [1.3.5] - 2026-07-19

- Fixed: on a 4×1 widget squeezed to 3 columns, the level/temperature
  text and the peak line could touch with no gap between the columns

## [1.3.4] - 2026-07-19

- Dual-cell (2S) device report now opens a structured GitHub issue form
  with the fields pre-filled — the "dual-cell" label is applied by the
  form itself, so it sticks for every reporter (the old URL label
  parameter only worked for maintainers)

## [1.3.3] - 2026-07-19

- Battery alerts now clear themselves once their advice is followed:
  plugging in dismisses the low-battery alert, unplugging dismisses the
  80% alert — the two can no longer nag side by side with stale advice
- Widget picker entries renamed to Small / Medium / Large (translated to
  all 12 languages) — launchers already show the app name and grid size,
  so the old "WattFlow 4×1"-style labels doubled both
- Widget picker previews redone as transparent rounded cards: no more
  wallpaper fragments in the corners or mismatched backing colors
- 4×1 widget can be squeezed to 3 columns: resize floor lowered to
  170 dp, layout switch threshold matched, peak line ellipsizes
- Monitor notification switches language immediately when the in-app
  language changes — no more restarting background recording

## [1.3.2] - 2026-07-18

- Widget picker now offers three preset sizes (2×1, 4×1, 4×2) with
  real previews instead of a single 2×1 entry that hid the larger
  layouts behind manual resizing

## [1.3.1] - 2026-07-18

- Fixed: the in-app language choice was ignored on Play-installed
  builds — Play's per-language splits stripped unselected locales, so
  the picker saved a language whose resources were absent. Language
  splits are now disabled
- Fixed: widget, monitor notification and alert texts now follow the
  in-app language instead of mixing it with the system language

## [1.3] - 2026-07-18

- Settings is now a third bottom tab instead of a gear icon that
  floated in different corners per page; the Live tab reclaims the
  top row the gear used to occupy
- Chart peak labels now sit on the empty side of the spike (above
  crests, below troughs) and get a backing pill, so the value stays
  readable even when auto-scaling pins the spike to the chart edge
- Settings: the uninterrupted-background-sampling (doze exemption) row
  moved from Raw sessions to Background recording, where it belongs
- Battery health trend (Pro): every full charge logs the fuel-gauge
  reading at 100%; the dialog shows readings over time and percent
  change vs the first baseline (data accumulates since v1.2)
- Sleep drain report (Pro): battery drain per night (23:00-07:00) for
  the last 7 nights, with a coverage figure showing how much of each
  night was actually recorded
- Energy ledger (Pro): daily battery in/out/net Wh for the last 14
  days, opened from the History tab
- Dual-cell (2S) battery correction: many fast-charge phones report
  only half the real power, but field data shows this varies by charge
  mode even on one device, so WattFlow never adjusts automatically.
  Detection (device list + session heuristic) only shows a "likely
  dual-cell" hint; a manual x2 switch (default off) applies the
  correction, plus a privacy-safe device report (pre-filled GitHub
  issue or email you review and send yourself)
- Landscape now uses a two-pane layout: charge visual left, stats and
  graph right
- Charger benchmark: 60-second test grades your charger + cable combo
  (avg / peak watts, stability, A-F). Pro saves named results and
  compares chargers
- Floating watts overlay (Pro): draggable live-watts pill over other
  apps while background recording is on; requires the optional
  display-over-apps permission
- Home screen widget now adapts to size: small (watts / % / temp),
  medium (+ source and ETA), large (+ 7-day energy chart)

- Live power graph and session detail curve now label their peak value
  (dot + wattage) — auto-scaled charts had no numeric reference before

- Monitor notification now shows live power in both directions
  ("Charging / Discharging • X.X W") with a second line of level, temp
  and ETA — replaces the useless "Waiting for charger" text when unplugged
- Monitor notification gains a Stop action to end background recording
  without opening the app

## [1.2.9] - 2026-07-11

- Background recording setting now persists: the switch stores your
  intent and the app restarts the service on open if a task killer,
  install or swipe stopped it (the switch previously mirrored the
  service's live state and "forgot" whenever the process died)
- Enabling background recording now explains WHY notification
  permission is requested before the system dialog appears

## [1.2.8] - 2026-07-11

- Widget freshness bounded at ~15 minutes via a periodic refresher that
  runs only while a widget exists (was up to 30 minutes stale for users
  without background recording)
- Widget size locked until the responsive multi-size widget ships in
  v1.3 — stretching it only revealed empty space

- Monitor notification channel no longer shows a launcher badge dot: it
  is a status indicator the user cannot clear, which read as a chronic
  nag on stock-Android launchers. Alert notifications keep their badge.
- CSV export filename now carries the export date
  (wattflow-sessions-2026-07-11.csv) — SAF never overwrites, so a fixed
  name only accumulated " (1)" suffixes

## [1.2.7] - 2026-07-11

- History header consolidated to one row: filter chips, a
  self-explanatory "CSV" export chip (was an ambiguous share icon),
  and the settings gear — which now lives in each tab instead of
  floating over content

## [1.2.6] - 2026-07-11

- Landscape fixed: live screen content is width-capped (the charge
  visual no longer scales off screen), and the history page is one
  scrollable list — headers used to consume the whole viewport leaving
  the session list invisible

## [1.2.5] - 2026-07-11

- Battery-optimization exemption row is now Pro-gated: it only benefits
  background recording, so letting free users grant it was misleading

## [1.2.4] - 2026-07-11

- History tab is now open to everyone: free users see their
  foreground-recorded sessions with a banner explaining that complete
  background recording is a Pro feature (was a fully locked tab)
- Paywall Unlock button disables with a "Store unavailable" note when
  Google Play billing can't load the product

## [1.2.3] - 2026-07-11

- Peak In / Peak Out now reset when the power source changes — no more
  stale wired peak lingering on the wireless screen

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
