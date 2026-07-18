package com.ezyapp.wattflow

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Opt-in foreground service: samples the battery every second so charge
 * sessions get recorded while the app is in the background. Shows live
 * watts in a low-priority ongoing notification.
 */
class ChargeMonitorService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var reader: BatteryReader
    private lateinit var overlay: OverlayController
    private var loopStarted = false

    override fun onCreate() {
        super.onCreate()
        reader = BatteryReader(this)
        overlay = OverlayController(applicationContext)
        createChannel()
        isRunning.value = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        val notification = buildNotification(null)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIF_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIF_ID, notification)
        }
        if (!loopStarted) {
            loopStarted = true
            scope.launch {
                var tick = 0
                while (isActive) {
                    val sample = withContext(Dispatchers.IO) { reader.read() }
                    if (sample != null) {
                        SessionRecorder.onSample(applicationContext, sample)
                        overlay.update(sample)
                        if (tick % 3 == 0) {
                            getSystemService(NotificationManager::class.java)
                                .notify(NOTIF_ID, buildNotification(sample))
                        }
                    }
                    tick++
                    delay(1000)
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        overlay.hide()
        isRunning.value = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(sample: BatterySample?): Notification {
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, ChargeMonitorService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )

        // Live power in both directions — "Charging • 5.5 W" /
        // "Discharging • 5.5 W" — instead of a useless "waiting" line.
        val title: String
        val subLine: String
        if (sample == null) {
            title = getString(R.string.reading_battery)
            subLine = ""
        } else {
            val direction = getString(
                if (sample.isCharging) R.string.filter_charge
                else R.string.filter_discharge
            )
            title = String.format(
                Locale.US, "%s • %.1f W", direction, kotlin.math.abs(sample.watts)
            )
            // Second line reuses data we already have: level, temp, and the
            // matching ETA (to full / time left).
            val parts = mutableListOf(
                "${sample.levelPercent}%",
                String.format(Locale.US, "%.0f°C", sample.temperatureC),
            )
            monitorEta(sample)?.let { parts.add(it) }
            subLine = parts.joinToString(" • ")
        }

        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_bolt)
            .setContentTitle(title)
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(
                Notification.Action.Builder(
                    null, getString(R.string.notif_stop), stopIntent
                ).build()
            )
        if (subLine.isNotEmpty()) {
            builder.setContentText(subLine)
        }
        return builder.build()
    }

    /** Rough ETA for the notification's second line; null when not derivable. */
    private fun monitorEta(s: BatterySample): String? {
        if (s.chargeCounterUah <= 0 || s.levelPercent !in 1..100) return null
        val amps = kotlin.math.abs(s.currentA)
        if (amps < 0.05) return null
        val remainAh = s.chargeCounterUah / 1_000_000.0
        val hours = if (s.isCharging) {
            if (s.levelPercent >= 100) return null
            (remainAh * 100.0 / s.levelPercent - remainAh) / amps
        } else {
            remainAh / amps
        }
        if (hours <= 0 || hours > 99) return null
        val mins = (hours * 60).toInt()
        val dur = if (mins >= 60) "${mins / 60}h ${mins % 60}m" else "${mins}m"
        return getString(
            if (s.isCharging) R.string.eta_to_full else R.string.eta_left, dur
        )
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            // Status notification, not an attention request: a launcher badge
            // dot the user can never clear reads as a chronic nag. Alert
            // notifications keep their badge — those are actionable.
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "monitor"
        const val NOTIF_ID = 1
        const val ACTION_STOP = "com.ezyapp.wattflow.STOP_MONITOR"

        /** Observed by the UI toggle. */
        val isRunning: MutableStateFlow<Boolean> = MutableStateFlow(false)

        fun stateFlow(): StateFlow<Boolean> = isRunning
    }
}
