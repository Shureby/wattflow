package com.ezyapp.wattflow

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.util.Locale
import kotlin.math.abs

/**
 * Home screen widget: last known watts + level + temperature.
 * Refresh sources: system updatePeriod (~30 min), plug/unplug events, and
 * every sampling path via [maybeUpdate] — live while the app is open or the
 * Pro monitor service runs.
 */
class WattWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        update(context)
    }

    companion object {
        private var lastUpdateTs = 0L

        /** Throttled: at most one refresh per 5 s. */
        fun maybeUpdate(context: Context) {
            val now = System.currentTimeMillis()
            if (now - lastUpdateTs < 5_000) return
            lastUpdateTs = now
            update(context)
        }

        fun update(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, WattWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isEmpty()) return

            val sample = BatteryReader(context).read() ?: return
            val views = RemoteViews(context.packageName, R.layout.widget_watt)
            val sign = if (sample.isCharging) "+" else "−"
            views.setTextViewText(
                R.id.widget_watts,
                String.format(Locale.US, "%s%.1f W", sign, abs(sample.watts)),
            )
            views.setTextViewText(
                R.id.widget_sub,
                String.format(
                    Locale.US, "%d%% • %.1f°C",
                    sample.levelPercent, sample.temperatureC,
                ),
            )
            views.setOnClickPendingIntent(
                R.id.widget_root,
                PendingIntent.getActivity(
                    context, 0,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            manager.updateAppWidget(ids, views)
        }
    }
}
