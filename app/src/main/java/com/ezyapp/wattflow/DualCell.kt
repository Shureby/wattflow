package com.ezyapp.wattflow

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.BatteryManager
import android.os.Build

/**
 * Dual-cell (2S) battery correction.
 *
 * Phones with two cells in series often under-report power 2x — but only
 * in some charge modes: field data from the Redmi K70 Pro shows halved
 * readings during 120W direct charge yet true readings on an 11W brick.
 * A static multiplier is therefore never applied automatically. The
 * known-device list and the session heuristic only produce an advisory
 * "likely dual-cell" hint; the user flips [setEnabled] after comparing
 * against their charger, and [factor] doubles watts at the single choke
 * point in [BatteryReader]. Voltage/current tiles keep raw gauge values.
 */
object DualCell {

    /**
     * Build.DEVICE values (lowercase) of phones community-confirmed to
     * under-report 2x. Best-effort seed; the heuristic and user reports
     * cover the rest.
     */
    private val KNOWN_2S = setOf(
        "manet",      // Redmi K70 Pro, 120W
        "socrates",   // Redmi K60 Pro, 120W
        "vili",       // Xiaomi 11T Pro, 120W
        "diting",     // Xiaomi 12T Pro, 120W
        "corot",      // Xiaomi 13T Pro, 120W
        "kebab",      // OnePlus 8T, Warp 65
        "lemonade",   // OnePlus 9, Warp 65
        "lemonadep",  // OnePlus 9 Pro, Warp 65
        "ovaltine",   // OnePlus 10T, 150W
        "salami",     // OnePlus 11, 100W
    )

    /**
     * A charging session whose energy implies a pack smaller than this is
     * assumed to be under-reporting 2x. Real packs in phones this app runs
     * on are 12-25 Wh; half-reported ones compute to 6-9 Wh.
     */
    private const val IMPLIED_PACK_WH_THRESHOLD = 9.0
    private const val MIN_GAIN_PERCENT = 15

    private fun prefs(c: Context) =
        c.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** User-controlled x2 switch. Off by default on every device. */
    fun enabled(c: Context) = prefs(c).getBoolean("dual_cell_x2", false)
    fun setEnabled(c: Context, on: Boolean) =
        prefs(c).edit().putBoolean("dual_cell_x2", on).apply()

    /** Set once the device list or the session heuristic says 2S. */
    fun detected(c: Context) =
        prefs(c).getBoolean("dual_cell_detected", false) || deviceListed()
    private fun setDetected(c: Context) =
        prefs(c).edit().putBoolean("dual_cell_detected", true).apply()

    /** True after the heuristic fires until the user sees the notice. */
    fun noticePending(c: Context) =
        prefs(c).getBoolean("dual_cell_notice_pending", false)
    fun clearNotice(c: Context) =
        prefs(c).edit().putBoolean("dual_cell_notice_pending", false).apply()

    fun deviceListed() = Build.DEVICE.lowercase() in KNOWN_2S

    fun factor(c: Context): Double = if (enabled(c)) 2.0 else 1.0

    /**
     * Called by [SessionRecorder] when a charging session ends and the
     * correction was NOT applied to it (energy is raw). A pack that appears
     * implausibly small means watts are being halved.
     */
    fun onRawChargeSession(c: Context, gainPercent: Int, energyWh: Double) {
        if (gainPercent < MIN_GAIN_PERCENT || energyWh <= 0.5) return
        if (detected(c)) return
        val impliedPackWh = energyWh * 100.0 / gainPercent
        if (impliedPackWh < IMPLIED_PACK_WH_THRESHOLD) {
            setDetected(c)
            prefs(c).edit().putBoolean("dual_cell_notice_pending", true).apply()
        }
    }

    // --- User-initiated device report (no telemetry; user sees and sends
    // the payload themselves via browser or mail app) ---

    private fun reportBody(c: Context): String {
        val bm = c.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val counter = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        return """
            Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})
            Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})
            x2 correction enabled by user: ${enabled(c)}
            Advisory verdict: listed=${deviceListed()}, detected=${detected(c)}
            Charge counter now: $counter uAh
        """.trimIndent()
    }

    fun gitHubReportIntent(c: Context): Intent {
        // Prefills the dual-cell issue form by field id. The form (not the
        // URL) applies the "dual-cell" label — GitHub ignores a labels= query
        // parameter from users without triage permission.
        val bm = c.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val counter = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        val url = Uri.parse("https://github.com/Shureby/wattflow/issues/new")
            .buildUpon()
            .appendQueryParameter("template", "dual-cell-report.yml")
            .appendQueryParameter("title", "2S report: ${Build.MANUFACTURER} ${Build.MODEL}")
            .appendQueryParameter("device", "${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
            .appendQueryParameter("android", "${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            .appendQueryParameter("x2-enabled", enabled(c).toString())
            .appendQueryParameter("verdict", "listed=${deviceListed()}, detected=${detected(c)}")
            .appendQueryParameter("charge-counter", counter.toString())
            .build()
        return Intent(Intent.ACTION_VIEW, url)
    }

    fun emailReportIntent(c: Context): Intent {
        val subject = Uri.encode("WattFlow 2S report: ${Build.MANUFACTURER} ${Build.MODEL}")
        val body = Uri.encode(reportBody(c))
        return Intent(
            Intent.ACTION_VIEW,
            Uri.parse("mailto:william@ezyappco.com?subject=$subject&body=$body"),
        )
    }
}
