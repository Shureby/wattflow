# Changelog

## [1.8.0] - 2026-07-23

- Added an "Autostart permission" row in Background Recording settings,
  aimed at Android skins with aggressive background-process management
  (Xiaomi/HyperOS, Huawei, Oppo, vivo, and similar) — these add their own
  autostart block on top of stock battery-optimization exemption, which
  can silently stop overnight recording even when everything else is
  configured correctly. Opens the manufacturer's autostart manager
  directly where a verified path exists (Xiaomi/HyperOS); points to the
  right settings screen manually elsewhere
- If background recording and battery-optimization exemption are both
  on but several consecutive nights still show no data (and the phone
  wasn't simply powered off), WattFlow now shows a one-time note
  pointing at the new Autostart setting — shown at most once, not a
  recurring nag

## [1.7.2] - 2026-07-22

- Fixed the Medium/Large home screen widgets clipping text mid-character
  at narrower resized widths — `widget_line1` (source/ETA) and the
  "Today's Peaks" header now ellipsize properly instead of hard-cutting
  a character, and both peak-value lines got a smaller text size that
  no longer clips even at the narrowest legal width
- Added a "P-In"/"P-Out" abbreviated label for the peak-values line when
  the widget is at its default (narrower) placement size, switching to
  the full "Peak In"/"Peak Out" once resized wider — avoids the numbers
  getting clipped at the common default size
- The Large widget's 7-day bar chart now has a title ("Last 7 days (Wh)")
  so the bar heights have a stated unit instead of being unlabeled

## [1.7.1] - 2026-07-22

- Fixed the Live tab's stats row (Voltage/Current/Temp/Peak In/Peak Out)
  breaking into garbled, overlapping text at window widths just above
  the Compact→Medium breakpoint in landscape — the two-pane layout's
  stats column can be too narrow for all 5 items on one line right
  where the nav rail appears, mainly hit on freeform-resizable windows
  (Chromebook) rather than any fixed phone/tablet size. `StatsRow` now
  wraps cleanly onto a second line (`FlowRow`) instead of splitting
  words mid-item

## [1.7.0] - 2026-07-22

- Large-screen adaptation: navigation now follows `WindowWidthSizeClass`
  instead of always being a bottom bar — tablets and foldables at
  Medium/Expanded width get a side `NavigationRail` in either
  orientation, phones keep the bottom `NavigationBar`
- History and Reports content is capped to 640dp and centered on wide
  screens, instead of filter chips and list rows stretching edge-to-edge
  into a mostly-empty full-width line
- Fixed the rail layout drawing its top content under the status bar in
  landscape — it bypassed `Scaffold` entirely and passed a zero inset,
  so the stats card collided with the status/cutout area
- Fixed Settings nesting its own `Scaffold` inside the already-padded
  content area, double-applying the top inset and sitting visibly lower
  than every other tab
- Dropped the in-page "Reports"/"Settings" titles for consistency with
  History and Live, which never had one — the nav already labels the
  current tab
- Live tab content is now vertically centered on tall viewports instead
  of sticking to the top with empty space below; the power graph grows
  from 160dp to 260dp in the landscape two-pane layout, where the stats
  column has the headroom to make a taller chart useful

## [1.6.1] - 2026-07-22

- Sleep drain: a night tracked to within 1% of the full 8-hour window
  now reads "Tracked the whole night" instead of a minute-precise
  count that could read as nitpicky over a gap of a few seconds
  (`formatDuration` truncates to whole minutes, so even near-total
  coverage could display as "7h 59m of 8h 0m"). The info icon is
  hidden too, since there's nothing to explain once coverage is
  effectively complete. Partial nights still show the precise count.

## [1.6.0] - 2026-07-22

- Added a 4th bottom-nav tab, Reports, alongside Live/History/Settings.
  Energy Ledger, Sleep Drain, and Battery Health — previously three
  chips mixed in with History's filter/export controls — now live there
  as a simple list, each unlocking the same dialog as before
- History's top row now only has Charging/Discharging + CSV export —
  back to one job (browsing and exporting the current filter) instead
  of mixing in three unrelated report entry points
- Charger benchmark's saved results move to Reports too, as a
  view/delete-only list; running a new benchmark stays on the Live tab
  where it's tied to the active charge
- Locked (non-Pro) report rows show a 🔒 and open the existing paywall
  on tap, same behavior as before, just relocated
- No changes to any report's underlying calculations — this release is
  navigation/IA only

## [1.5.3] - 2026-07-22

- Battery health trend redesigned: baseline is now the running max of
  non-anomalous readings (not the first-ever reading), so a later
  reading that's higher than an earlier one no longer looks like the
  battery is impossibly healing itself
- Outlier detection uses a rate-of-change-from-baseline modified
  z-score (MAD-based) over the last 7 readings, so a long calendar gap
  isn't judged by the same tolerance as a short one — a real drop over
  many months reads as normal, not a false anomaly
- A same-direction jump of 5%+ versus the immediately preceding reading,
  while that reading is still the most recent one, prompts an inline
  "did you replace your battery?" question instead of guessing; once
  newer data exists the point is no longer asked about and defaults to
  excluded
- Excluded readings stay visible (struck through, with an explanation),
  never deleted — only skipped from the baseline/trend math
- Today's headline falls back to the last trusted reading if today's
  own value is itself flagged, instead of showing an alarming swing
  caused by one noisy reading
- Fewer than 7 readings so far shows every point as-is with detection
  off, explained via a tappable ⓘ instead of repeating the same note on
  every row
- New shared semantic tag colors (success/info/warning) for readings
  that don't fit the existing primary/error roles

## [1.5.2] - 2026-07-21

- Energy ledger redesigned: daily in/out shown as a signed percentage of
  a full charge instead of raw Wh (same baseline as Sleep Drain), with
  the same `→` arrow — prefix for in, suffix for out — instead of the
  old backwards `↓`/`↑` pairing
- A day whose in or out total exceeds 100% (multiple charge/discharge
  cycles) gets a tappable ⓘ breaking down the real per-session
  percentages that add up to it; sessions that round to 0% are left out
  of the list (they don't change the total either way); a single
  session alone above 100% gets an explanation instead of a redundant
  one-item list — the full-charge baseline behind the estimate may just
  be a bit low right now
- In/out/net columns are now equal-width and consistently aligned
  (previously ragged, width tracked content length); in/out colored to
  match the existing charge/discharge convention used in History
- Fixed: a History session row with identical start/end level (e.g. a
  brief discharge blip) could be colored as if it were a charge, because
  the color was picked from the level delta instead of the session's
  actual direction

## [1.5.1] - 2026-07-21

- Sleep drain redesigned: nightly drain now shown as a percentage of a
  full charge (once you've charged to 100% once) instead of raw Wh, with
  a signed headline (`−20%`) and Wh/avg-W as subtext
- Nights spent held on the charger get their own card, showing exactly
  when 100% was reached and how long it stayed there — tracked live
  going forward, or inferred from the stored charging curve for older
  sessions (trickle-charge detection)
- A battery-health tip appears when a night was spent at full for over
  an hour, pointing at the existing Charge alert setting
- Nights that were part discharge, part charging (unplugged/replugged
  overnight) show the discharge story as normal, with a small aside
  noting the charging portion's time range
- Coverage line reads as plain time ("Tracked 6h 40m of 8h") with a ⓘ
  explaining why a night might not be fully covered, instead of a bare
  percentage
- Fixed: the floating watts overlay showed `▲`/`▼` for charging/
  discharging with no explanation — now uses `+`/`−`, matching the small
  widget's existing convention

## [1.4.0] - 2026-07-21

- Targets Android 16 (API 36): compileSdk/targetSdk 35 → 36, AGP 8.6.1 →
  8.13.0, Gradle 8.9 → 8.13; Kotlin/KSP/Compose left untouched (not
  required for the bump). No user-visible changes — internal testing
  only this cycle, not pushed to the production track

## [1.3.10] - 2026-07-20

- Fixed: small/medium/large widget definitions were missing (or
  inconsistent about) `minResizeWidth`/`minResizeHeight`, so resize
  floors weren't declared the same way across all three sizes
- Fixed: the small widget's preview image briefly carried a
  placeholder temperature (99.9°C) left over from a caching test —
  back to a realistic mock value

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
