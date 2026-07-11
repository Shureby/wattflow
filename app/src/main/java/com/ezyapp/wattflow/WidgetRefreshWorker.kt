package com.ezyapp.wattflow

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Bounds widget staleness at ~15 minutes for users without the monitor
 * service: the widget otherwise only refreshes on the ~30-minute system
 * tick, plug events, or while something is sampling. Scheduled while at
 * least one widget exists (see [WattWidgetProvider.onEnabled]).
 */
class WidgetRefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        WattWidgetProvider.updateNow(applicationContext)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "widget_refresh"

        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<WidgetRefreshWorker>(15, TimeUnit.MINUTES)
                    .build(),
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
