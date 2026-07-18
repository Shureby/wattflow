package com.ezyapp.wattflow

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * Alert thresholds. Free build/users: fixed 80 / 20 — the healthy defaults.
 * Pro users can adjust. Values live in the shared "settings" prefs file.
 */
object AlertPrefs {
    const val DEFAULT_CHARGE = 80
    const val DEFAULT_LOW = 20

    private fun prefs(c: Context) =
        c.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun enabled(c: Context) = prefs(c).getBoolean("alerts_enabled", false)
    fun setEnabled(c: Context, on: Boolean) =
        prefs(c).edit().putBoolean("alerts_enabled", on).apply()

    fun chargeThreshold(c: Context) = prefs(c).getInt("alert_charge", DEFAULT_CHARGE)
    fun setChargeThreshold(c: Context, v: Int) =
        prefs(c).edit().putInt("alert_charge", v.coerceIn(50, 100)).apply()

    fun lowThreshold(c: Context) = prefs(c).getInt("alert_low", DEFAULT_LOW)
    fun setLowThreshold(c: Context, v: Int) =
        prefs(c).edit().putInt("alert_low", v.coerceIn(5, 40)).apply()

    // Fired flags persist across process death so a Worker restart doesn't
    // re-notify. Reset on direction change.
    fun chargeFired(c: Context) = prefs(c).getBoolean("charge_alert_fired", false)
    fun setChargeFired(c: Context, v: Boolean) =
        prefs(c).edit().putBoolean("charge_alert_fired", v).apply()

    fun lowFired(c: Context) = prefs(c).getBoolean("low_alert_fired", false)
    fun setLowFired(c: Context, v: Boolean) =
        prefs(c).edit().putBoolean("low_alert_fired", v).apply()
}

object AlertEngine {
    private const val CHANNEL_ID = "alerts"
    private const val NOTIF_CHARGE = 2
    private const val NOTIF_LOW = 3

    /** Called from every sampling path (UI loop, monitor service, worker). */
    fun onSample(rawContext: Context, sample: BatterySample) {
        // Alert text must follow the in-app language choice regardless of
        // which component sampled.
        val context = LocalePrefs.wrap(rawContext)
        if (!AlertPrefs.enabled(context)) return
        val level = sample.levelPercent
        if (level !in 1..100) return

        if (sample.isCharging) {
            if (AlertPrefs.lowFired(context)) AlertPrefs.setLowFired(context, false)
            if (level >= AlertPrefs.chargeThreshold(context) &&
                !AlertPrefs.chargeFired(context)
            ) {
                AlertPrefs.setChargeFired(context, true)
                notify(
                    context, NOTIF_CHARGE,
                    context.getString(R.string.notif_charge_alert, level),
                )
            }
        } else {
            if (AlertPrefs.chargeFired(context)) AlertPrefs.setChargeFired(context, false)
            if (level <= AlertPrefs.lowThreshold(context) &&
                !AlertPrefs.lowFired(context)
            ) {
                AlertPrefs.setLowFired(context, true)
                notify(
                    context, NOTIF_LOW,
                    context.getString(R.string.notif_low_alert, level),
                )
            }
        }
    }

    private fun notify(context: Context, id: Int, text: String) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.alerts_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            )
        )
        val tap = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_bolt)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
        nm.notify(id, notification)
    }
}
