package com.ezyapp.chargingpower

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
