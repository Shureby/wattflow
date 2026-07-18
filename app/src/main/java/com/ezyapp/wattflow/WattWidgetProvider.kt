package com.ezyapp.wattflow

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * Home screen widget, responsive to launcher resize:
 *  - S (2x1): watts + level/temp
 *  - M (4x1): + source, ETA, today's peaks
 *  - L (4x2): + 7-day charged-energy mini chart
 * Android 12+ picks the layout via a SizeF -> RemoteViews map; older versions
 * pick it from the widget options in [onAppWidgetOptionsChanged].
 *
 * Refresh sources: system updatePeriod (~30 min), plug/unplug events, and
 * every sampling path via [maybeUpdate] — live while the app is open or the
 * Pro monitor service runs.
 */
open class WattWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        update(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        update(context)
    }

    override fun onEnabled(context: Context) {
        WidgetRefreshWorker.schedule(context)
    }

    override fun onDisabled(context: Context) {
        WidgetRefreshWorker.cancel(context)
    }

    companion object {
        private var lastUpdateTs = 0L
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        // dp thresholds matching the SizeF map below.
        private const val WIDE_DP = 230
        private const val TALL_DP = 100

        /** Throttled: at most one refresh per 5 s. */
        fun maybeUpdate(context: Context) {
            val now = System.currentTimeMillis()
            if (now - lastUpdateTs < 5_000) return
            lastUpdateTs = now
            update(context)
        }

        fun update(context: Context) {
            val appContext = context.applicationContext
            scope.launch { updateNow(appContext) }
        }

        suspend fun updateNow(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            // All picker entries share this implementation; collect every id.
            val ids = listOf(
                WattWidgetProvider::class.java,
                WattWidgetProviderMedium::class.java,
                WattWidgetProviderLarge::class.java,
            ).flatMap { cls ->
                manager.getAppWidgetIds(ComponentName(context, cls)).toList()
            }.toIntArray()
            if (ids.isEmpty()) return

            val sample = BatteryReader(context).read() ?: return
            val peaks = updateDailyPeaks(context, sample)

            // Chart only when some widget can show it: any pre-12 widget sized
            // large, or any 12+ widget (its size map always carries L).
            val needChart = Build.VERSION.SDK_INT >= 31 || ids.any { id ->
                val opts = manager.getAppWidgetOptions(id)
                opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) >= WIDE_DP &&
                    opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT) >= TALL_DP
            }
            val chart = if (needChart) renderWeekChart(context) else null

            for (id in ids) {
                val views = if (Build.VERSION.SDK_INT >= 31) {
                    RemoteViews(
                        mapOf(
                            SizeF(110f, 40f) to
                                build(context, R.layout.widget_watt, sample, peaks, null),
                            SizeF(WIDE_DP.toFloat(), 40f) to
                                build(context, R.layout.widget_watt_m, sample, peaks, null),
                            SizeF(WIDE_DP.toFloat(), TALL_DP.toFloat()) to
                                build(context, R.layout.widget_watt_l, sample, peaks, chart),
                        )
                    )
                } else {
                    val opts = manager.getAppWidgetOptions(id)
                    val w = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
                    val h = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
                    when {
                        w >= WIDE_DP && h >= TALL_DP ->
                            build(context, R.layout.widget_watt_l, sample, peaks, chart)
                        w >= WIDE_DP ->
                            build(context, R.layout.widget_watt_m, sample, peaks, null)
                        else ->
                            build(context, R.layout.widget_watt, sample, peaks, null)
                    }
                }
                manager.updateAppWidget(id, views)
            }
        }

        private fun build(
            rawContext: Context,
            layout: Int,
            sample: BatterySample,
            peaks: Pair<Double, Double>,
            chart: Bitmap?,
        ): RemoteViews {
            // Resource strings must follow the in-app language choice.
            val context = LocalePrefs.wrap(rawContext)
            val views = RemoteViews(context.packageName, layout)
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
            if (layout != R.layout.widget_watt) {
                val source = context.getString(sourceLabelRes(sample.plugged))
                val eta = batteryEtaMinutes(sample, abs(sample.currentA))?.let { minutes ->
                    context.getString(
                        if (sample.isCharging) R.string.eta_to_full else R.string.eta_left,
                        formatDuration(minutes * 60_000L),
                    )
                }
                views.setTextViewText(
                    R.id.widget_line1,
                    if (eta != null) "$source • $eta" else source,
                )
                views.setTextViewText(
                    R.id.widget_line2,
                    String.format(
                        Locale.US, "%s %.1f W • %s %.1f W",
                        context.getString(R.string.stat_peak_in), peaks.first,
                        context.getString(R.string.stat_peak_out), peaks.second,
                    ),
                )
            }
            if (chart != null) views.setImageViewBitmap(R.id.widget_chart, chart)
            views.setOnClickPendingIntent(
                R.id.widget_root,
                PendingIntent.getActivity(
                    context, 0,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            return views
        }

        /**
         * Today's peak in/out watts, persisted so they survive the widget's
         * sparse refresh cadence. Resets at local midnight.
         */
        private fun updateDailyPeaks(
            context: Context,
            sample: BatterySample,
        ): Pair<Double, Double> {
            val prefs = context.getSharedPreferences("widget_peaks", Context.MODE_PRIVATE)
            val cal = Calendar.getInstance()
            val today = cal.get(Calendar.YEAR) * 1000 + cal.get(Calendar.DAY_OF_YEAR)
            val sameDay = prefs.getInt("day", -1) == today
            val peakIn = maxOf(
                if (sameDay) prefs.getFloat("in", 0f).toDouble() else 0.0,
                sample.watts,
            )
            val peakOut = maxOf(
                if (sameDay) prefs.getFloat("out", 0f).toDouble() else 0.0,
                -sample.watts,
            )
            prefs.edit()
                .putInt("day", today)
                .putFloat("in", peakIn.toFloat())
                .putFloat("out", peakOut.toFloat())
                .apply()
            return peakIn to peakOut
        }

        /** Bar chart of charged Wh per day, last 7 days (today rightmost). */
        private suspend fun renderWeekChart(rawContext: Context): Bitmap {
            val context = LocalePrefs.wrap(rawContext)
            val dayMs = 86_400_000L
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            cal.add(Calendar.DAY_OF_YEAR, -6)
            val start = cal.timeInMillis

            val wh = DoubleArray(7)
            AppDatabase.get(context).sessionDao().sessionsSince(start).forEach { s ->
                if (s.direction != DIRECTION_CHARGE) return@forEach
                val day = ((s.endTs - start) / dayMs).toInt()
                if (day in 0..6) wh[day] += s.energyWh
            }

            val density = context.resources.displayMetrics.density.coerceAtMost(2f)
            val w = (300 * density).toInt()
            val h = (80 * density).toInt()
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)

            val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFA78BFA.toInt()
                textSize = 10 * density
                textAlign = Paint.Align.CENTER
            }
            val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFA78BFA.toInt()
            }

            canvas.drawText(
                context.getString(R.string.history_last7),
                w / 14f, 12 * density,
                Paint(labelPaint).apply { textAlign = Paint.Align.LEFT },
            )

            val labelArea = 14 * density
            val topArea = 16 * density
            val plotBottom = h - labelArea
            val plotHeight = plotBottom - topArea
            val slot = w / 7f
            val barWidth = slot * 0.5f
            val maxWh = wh.max().coerceAtLeast(0.001)
            val narrowDay = SimpleDateFormat("EEEEE", Locale.getDefault())

            for (i in 0..6) {
                val cx = slot * i + slot / 2
                val barH = (wh[i] / maxWh * plotHeight).toFloat()
                    .coerceAtLeast(2 * density)
                barPaint.alpha = if (wh[i] > 0) 255 else 64
                canvas.drawRoundRect(
                    cx - barWidth / 2, plotBottom - barH,
                    cx + barWidth / 2, plotBottom,
                    3 * density, 3 * density, barPaint,
                )
                labelPaint.alpha = if (i == 6) 255 else 160
                canvas.drawText(
                    narrowDay.format(Date(start + i * dayMs + dayMs / 2)),
                    cx, h - 3 * density, labelPaint,
                )
            }
            return bmp
        }
    }
}

/** Separate picker entries so each preset size is discoverable. */
class WattWidgetProviderMedium : WattWidgetProvider()

class WattWidgetProviderLarge : WattWidgetProvider()
