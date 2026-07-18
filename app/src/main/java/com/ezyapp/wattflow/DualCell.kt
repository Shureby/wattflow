package com.ezyapp.wattflow

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.BatteryManager
import android.os.Build

/**
 * Dual-cell (2S) battery correction.
 *
 * Phones with two cells in series (most 100W+ fast-charge designs) expose
 * per-cell voltage through the fuel gauge while current is the true pack
 * current, so battery-side watts read at half the real value. When active,
 * [factor] doubles the computed watts at the single choke point in
 * [BatteryReader]. Voltage/current tiles keep showing raw gauge values.
 *
 * Mode AUTO applies the correction when the device is on the known-2S list
 * or the session heuristic has fired; ON/OFF override both signals.
 */
object DualCell {

    const val MODE_AUTO = 0
    const val MODE_ON = 1
    const val MODE_OFF = 2

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

    fun mode(c: Context) = prefs(c).getInt("dual_cell_mode", MODE_AUTO)
    fun setMode(c: Context, m: Int) =
        prefs(c).edit().putInt("dual_cell_mode", m).apply()

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

    fun active(c: Context): Boolean = when (mode(c)) {
        MODE_ON -> true
        MODE_OFF -> false
        else -> detected(c)
    }

    fun factor(c: Context): Double = if (active(c)) 2.0 else 1.0

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
            App mode chosen: ${modeName(mode(c))}
            Auto verdict: listed=${deviceListed()}, detected=${detected(c)}
            Charge counter now: $counter uAh
        """.trimIndent()
    }

    private fun modeName(m: Int) = when (m) {
        MODE_ON -> "forced ON"
        MODE_OFF -> "forced OFF"
        else -> "auto"
    }

    fun gitHubReportIntent(c: Context): Intent {
        val title = Uri.encode("2S report: ${Build.MANUFACTURER} ${Build.MODEL}")
        val body = Uri.encode(reportBody(c))
        return Intent(
            Intent.ACTION_VIEW,
            Uri.parse(
                "https://github.com/Shureby/wattflow/issues/new?title=$title&body=$body&labels=dual-cell"
            ),
        )
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
