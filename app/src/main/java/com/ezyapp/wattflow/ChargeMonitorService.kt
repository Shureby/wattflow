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
    private var loopStarted = false

    override fun onCreate() {
        super.onCreate()
        reader = BatteryReader(this)
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
        val text = if (sample == null || !sample.isCharging) {
            getString(R.string.notif_waiting)
        } else {
            String.format(Locale.US, "%.1f W", sample.watts)
        }
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_bolt)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
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
