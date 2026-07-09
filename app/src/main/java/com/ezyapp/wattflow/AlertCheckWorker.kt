package com.ezyapp.wattflow

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Self-chaining battery check for alerts while nothing else is sampling.
 * Started when power connects (see [ChargePlugReceiver]); re-enqueues itself
 * every [INTERVAL_MIN] minutes while charging and below the alert threshold.
 * Android 12+ forbids starting a foreground service from that receiver, so a
 * worker chain is the reliable path for Free users; the Pro monitor service
 * and the open app deliver second-accurate alerts on top.
 */
class AlertCheckWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        if (!AlertPrefs.enabled(context)) return Result.success()

        val sample = BatteryReader(context).read() ?: return Result.success()
        AlertEngine.onSample(context, sample)

        // Keep watching while charging and the alert hasn't fired yet.
        if (sample.isCharging && !AlertPrefs.chargeFired(context)) {
            enqueue(context, delayMinutes = INTERVAL_MIN)
        }
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "alert_check"
        private const val INTERVAL_MIN = 5L

        fun enqueue(context: Context, delayMinutes: Long = 0) {
            val request = OneTimeWorkRequestBuilder<AlertCheckWorker>()
                .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
