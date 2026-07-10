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
object RawModePrefs {
    fun enabled(context: Context): Boolean =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getBoolean("raw_sessions", false)

    fun setEnabled(context: Context, on: Boolean) {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit().putBoolean("raw_sessions", on).apply()
    }
}
