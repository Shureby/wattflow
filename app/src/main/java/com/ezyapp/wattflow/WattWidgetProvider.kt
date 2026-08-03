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
 *  - M (4x1 or smaller): + source, ETA, today's peaks
 *  - L (4x2 or smaller): + 7-day charged-energy mini chart
 * Android 12+ picks the layout via a SizeF -> RemoteViews map; older versions
 * pick it from the widget options in [onAppWidgetOptionsChanged]. Each
 * picker entry's map/threshold check is capped to its own tier (S/M/L) —
 * a widget never renders a bigger sibling's layout just because a launcher
 * reports a larger box for it than another OEM would (seen on Samsung One
 * UI: all three picker entries were resolving to the L layout, clipped to
 * whatever box each one actually got).
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

        // dp thresholds matching the SizeF map below. WIDE_DP matches the
        // 4x1 widget's minResizeWidth so a user-shrunk widget still renders
        // the M/L layout instead of falling back to S. ROOMY_DP picks between
        // short ("P-In") and full ("Peak In") peak labels within that same
        // M/L layout — it never affects which layout is chosen, only label
        // length, so misjudging it just over/under-abbreviates text rather
        // than showing the wrong content. Measured on a real device: default
        // M/L placements land around 260dp (need the short labels) and a
        // manually-grown widget around 356dp (full labels fit).
        private const val WIDE_DP = 170
        private const val ROOMY_DP = 300
        private const val TALL_DP = 100

        // widget_watt_l.xml's root padding (10dp each side) and the fixed
        // stats-row block above the chart (two text lines + 8dp margin).
        private const val ROOT_PADDING_DP = 20
        private const val CHART_HEADER_DP = 72

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
            val sIds = manager.getAppWidgetIds(ComponentName(context, WattWidgetProvider::class.java))
            val mIds = manager.getAppWidgetIds(ComponentName(context, WattWidgetProviderMedium::class.java))
            val lIds = manager.getAppWidgetIds(ComponentName(context, WattWidgetProviderLarge::class.java))
            if (sIds.isEmpty() && mIds.isEmpty() && lIds.isEmpty()) return

            val sample = BatteryReader(context).read() ?: return
            val peaks = updateDailyPeaks(context, sample)

            for (id in sIds) {
                manager.updateAppWidget(id, build(context, R.layout.widget_watt, sample, peaks, null))
            }
            for (id in mIds) {
                manager.updateAppWidget(id, mediumViews(context, manager, id, sample, peaks))
            }
            for (id in lIds) {
                manager.updateAppWidget(id, largeViews(context, manager, id, sample, peaks))
            }
        }

        /** M's own map/threshold never reaches into L's chart layout. */
        private fun mediumViews(
            context: Context,
            manager: AppWidgetManager,
            id: Int,
            sample: BatterySample,
            peaks: Pair<Double, Double>,
        ): RemoteViews = if (Build.VERSION.SDK_INT >= 31) {
            RemoteViews(
                mapOf(
                    SizeF(WIDE_DP.toFloat(), 40f) to
                        build(context, R.layout.widget_watt_m, sample, peaks, null, compact = true),
                    SizeF(ROOMY_DP.toFloat(), 40f) to
                        build(context, R.layout.widget_watt_m, sample, peaks, null),
                )
            )
        } else {
            val w = manager.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
            build(context, R.layout.widget_watt_m, sample, peaks, null, compact = w < ROOMY_DP)
        }

        /**
         * L's own map/threshold never falls back to S/M's smaller layouts.
         * The chart is rendered per-instance at that instance's own current
         * box size, so a resized L widget gets a genuinely bigger chart
         * instead of a fixed bitmap stretched to fill more room.
         */
        private suspend fun largeViews(
            context: Context,
            manager: AppWidgetManager,
            id: Int,
            sample: BatterySample,
            peaks: Pair<Double, Double>,
        ): RemoteViews {
            val opts = manager.getAppWidgetOptions(id)
            val w = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, ROOMY_DP)
            val h = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, TALL_DP)
            // Subtract root padding + the stats row above the chart (~72dp)
            // so the bitmap matches what's actually left for the ImageView;
            // floored at the original design size, never smaller than before.
            val chartWidthDp = (w - ROOT_PADDING_DP).coerceAtLeast(260)
            val chartHeightDp = (h - CHART_HEADER_DP).coerceAtLeast(80)
            val chart = renderWeekChart(context, chartWidthDp, chartHeightDp)

            return if (Build.VERSION.SDK_INT >= 31) {
                RemoteViews(
                    mapOf(
                        SizeF(WIDE_DP.toFloat(), TALL_DP.toFloat()) to
                            build(context, R.layout.widget_watt_l, sample, peaks, chart, compact = true),
                        SizeF(ROOMY_DP.toFloat(), TALL_DP.toFloat()) to
                            build(context, R.layout.widget_watt_l, sample, peaks, chart),
                    )
                )
            } else {
                build(context, R.layout.widget_watt_l, sample, peaks, chart, compact = w < ROOMY_DP)
            }
        }

        private fun build(
            rawContext: Context,
            layout: Int,
            sample: BatterySample,
            peaks: Pair<Double, Double>,
            chart: Bitmap?,
            compact: Boolean = false,
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
                    R.id.widget_peak_header,
                    context.getString(R.string.widget_today_peaks),
                )
                views.setTextViewText(
                    R.id.widget_line2,
                    String.format(
                        Locale.US, "%s %.1f W • %s %.1f W",
                        context.getString(
                            if (compact) R.string.widget_peak_in_short else R.string.stat_peak_in
                        ),
                        peaks.first,
                        context.getString(
                            if (compact) R.string.widget_peak_out_short else R.string.stat_peak_out
                        ),
                        peaks.second,
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

        /**
         * Bar chart of charged Wh per day, last 7 days (today rightmost).
         * Rendered at the widget's own current box size (not a fixed
         * bitmap) so growing the L widget actually yields a bigger, clearer
         * chart via the layout's fitXY ImageView, instead of a fixed-size
         * bitmap getting non-uniformly stretched to fill more room.
         */
        private suspend fun renderWeekChart(
            rawContext: Context,
            widthDp: Int = 300,
            heightDp: Int = 80,
        ): Bitmap {
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
            val w = (widthDp * density).toInt()
            val h = (heightDp * density).toInt()
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

            val realMaxWh = wh.max()
            canvas.drawText(
                String.format(Locale.US, context.getString(R.string.widget_last7_wh), realMaxWh),
                w / 14f, 12 * density,
                Paint(labelPaint).apply { textAlign = Paint.Align.LEFT },
            )

            val labelArea = 14 * density
            val topArea = 16 * density
            val plotBottom = h - labelArea
            val plotHeight = plotBottom - topArea
            val slot = w / 7f
            val barWidth = slot * 0.5f
            val maxWh = realMaxWh.coerceAtLeast(0.001)
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
