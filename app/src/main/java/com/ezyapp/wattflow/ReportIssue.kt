package com.ezyapp.wattflow

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.BatteryManager
import android.os.Build

/**
 * User-initiated "this reading looks wrong" report. No telemetry — the user
 * reviews and sends the prefilled payload themselves via browser or mail app.
 * Only device/Android/app-version/charge-counter/live-reading are prefilled
 * automatically; everything else (meter reading, charger spec, screen state,
 * battery health, reproducibility) is a blank field the user fills in by
 * hand, entirely at their own discretion.
 *
 * Formerly backed a dual-cell (2S) detection/correction feature: real-device
 * testing with an independent USB power meter (17T Pro, K70 Pro on 3
 * chargers incl. 2 manufacturer-original 120W bricks, Redmi Note 13 Pro+)
 * found no under-reporting on any device, including a confirmed 2-cell
 * pack — so the correction and its device-list/heuristic detection were
 * removed rather than kept as a guess. This is now a generic escape hatch
 * for any future reading that looks wrong on some device we haven't seen.
 *
 * Fields beyond the original minimal set (device/Android/charge-counter)
 * were added after a real report came back too thin to act on — a test
 * engineer needs a ground-truth comparison (independent meter reading) and
 * charge-curve/thermal/charger context to tell "real bug" apart from
 * "normal taper" or "under-rated charger," same distinctions this whole
 * investigation had to work out the hard way with real hardware.
 */
object ReportIssue {

    private fun appVersion(c: Context): String {
        val versionName = runCatching {
            c.packageManager.getPackageInfo(c.packageName, 0).versionName
        }.getOrNull() ?: "?"
        val variant = if (c.packageName.endsWith(".foss")) "FOSS" else "Play"
        return "$versionName ($variant)"
    }

    private fun liveReadingLine(sample: BatterySample?): String {
        if (sample == null) return "(not available — app wasn't reading when this was generated)"
        return "%.1f W (%.2f V x %.2f A), %d%% battery".format(
            sample.watts, sample.voltageV, sample.currentA, sample.levelPercent,
        )
    }

    private fun reportBody(c: Context, sample: BatterySample?): String {
        val bm = c.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val counter = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        return """
            Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})
            Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})
            WattFlow version: ${appVersion(c)}
            Charge counter now: $counter uAh
            WattFlow's live reading now: ${liveReadingLine(sample)}

            --- Everything below is optional, but the more filled in the more useful this report is ---
            Independent USB power meter reading, same moment (W / V / A):
            Charger's rated max wattage:
            Is this the phone's original/bundled charger and cable? (yes/no/not sure):
            Cable rating, if known (e.g. 3A, 5A/6A E-marked):
            Wired or wireless:
            Screen on or off while charging:
            Was the phone warm, or charged repeatedly back-to-back recently?:
            Phone's rated battery capacity in mAh, if known:
            Battery Health Trend %, from Reports -> Health in the app, if checked:
            Does this happen every time, or just once?:

            Notes (anything else that helps judge the real power):
        """.trimIndent()
    }

    fun gitHubReportIntent(c: Context, sample: BatterySample?): Intent {
        // Prefills the report form by field id; the form itself (not any
        // query parameter) applies the "inaccurate-reading" label.
        val bm = c.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val counter = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        val url = Uri.parse("https://github.com/Shureby/wattflow/issues/new")
            .buildUpon()
            .appendQueryParameter("template", "inaccurate-reading-report.yml")
            .appendQueryParameter("title", "Inaccurate reading: ${Build.MANUFACTURER} ${Build.MODEL}")
            .appendQueryParameter("device", "${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
            .appendQueryParameter("android", "${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            .appendQueryParameter("app-version", appVersion(c))
            .appendQueryParameter("charge-counter", counter.toString())
            .appendQueryParameter("live-reading", liveReadingLine(sample))
            .build()
        return Intent(Intent.ACTION_VIEW, url)
    }

    fun emailReportIntent(c: Context, sample: BatterySample?): Intent {
        val subject = Uri.encode("WattFlow inaccurate reading: ${Build.MANUFACTURER} ${Build.MODEL}")
        val body = Uri.encode(reportBody(c, sample))
        return Intent(
            Intent.ACTION_VIEW,
            Uri.parse("mailto:william@ezyappco.com?subject=$subject&body=$body"),
        )
    }
}
