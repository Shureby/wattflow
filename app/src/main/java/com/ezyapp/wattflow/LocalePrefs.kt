package com.ezyapp.wattflow

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Stores the user's manual language choice. Empty string = follow system.
 */
object LocalePrefs {
    private const val PREFS_NAME = "settings"
    private const val KEY_LANG = "language_tag"

    fun get(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LANG, "") ?: ""

    fun set(context: Context, tag: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_LANG, tag).apply()
    }

    fun wrap(base: Context): Context {
        val tag = get(base)
        if (tag.isEmpty()) return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }
}

/**
 * Geek mode: show raw 60-second-granularity session segments instead of the
 * default display-time merging (same direction, gaps under 5 minutes).
 * Presentation-only — stored data is always raw.
 */
/**
 * The user's INTENT to record in the background — persisted, unlike the
 * service's live state. The service can be killed by installs, vendor task
 * managers or swipes; on next app open the intent restarts it.
 */
object MonitorPrefs {
    fun enabled(context: Context): Boolean =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getBoolean("monitor_enabled", false)

    fun setEnabled(context: Context, on: Boolean) {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit().putBoolean("monitor_enabled", on).apply()
    }
}

object RawModePrefs {
    fun enabled(context: Context): Boolean =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getBoolean("raw_sessions", false)

    fun setEnabled(context: Context, on: Boolean) {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit().putBoolean("raw_sessions", on).apply()
    }
}

/**
 * Opt-in all-time best Peak In/Out. Off by default (peaks stay scoped to the
 * current charge/discharge streak, see ChargingViewModel). On: never
 * auto-resets, survives app restarts — only the user's manual Reset in
 * Settings clears it.
 */
object PeakPrefs {
    fun allTimeEnabled(context: Context): Boolean =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getBoolean("peak_alltime_enabled", false)

    fun setAllTimeEnabled(context: Context, on: Boolean) {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit().putBoolean("peak_alltime_enabled", on).apply()
    }

    fun allTimeIn(context: Context): Double =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getFloat("peak_alltime_in", 0f).toDouble()

    fun allTimeOut(context: Context): Double =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getFloat("peak_alltime_out", 0f).toDouble()

    fun setAllTimeIn(context: Context, value: Double) {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit().putFloat("peak_alltime_in", value.toFloat()).apply()
    }

    fun setAllTimeOut(context: Context, value: Double) {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit().putFloat("peak_alltime_out", value.toFloat()).apply()
    }

    fun reset(context: Context) {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit()
            .putFloat("peak_alltime_in", 0f)
            .putFloat("peak_alltime_out", 0f)
            .apply()
    }
}
