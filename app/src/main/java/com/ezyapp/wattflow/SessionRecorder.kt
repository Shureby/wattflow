package com.ezyapp.wattflow

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Process-wide charge session recorder. Fed samples by both the UI poll loop
 * and the monitor service; timestamps dedupe double-sampling. Detects
 * charging start/stop transitions and persists finished sessions to Room.
 */
object SessionRecorder {

    private class ActiveSession(val startTs: Long, val startLevel: Int, val plugged: Int) {
        var lastTs = startTs
        var lastLevel = startLevel
        var peakW = 0.0
        var energyWh = 0.0
        var wattSum = 0.0
        var sampleCount = 0
    }

    private var active: ActiveSession? = null
    private var lastSampleTs = 0L

    // Fire-and-forget insert scope; sessions are tiny and losing one on
    // process death is acceptable.
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Synchronized
    fun onSample(context: Context, sample: BatterySample) {
        val now = System.currentTimeMillis()
        if (now - lastSampleTs < 500) return
        lastSampleTs = now

        val charging = sample.isCharging && sample.plugged != 0
        val a = active
        when {
            charging && a == null -> {
                active = ActiveSession(now, sample.levelPercent, sample.plugged)
            }
            charging && a != null -> {
                val dtHours = (now - a.lastTs) / 3_600_000.0
                // Ignore gaps over 30s (app/service was not sampling).
                if (dtHours in 0.0..(30.0 / 3600.0)) {
                    a.energyWh += sample.watts * dtHours
                }
                a.lastTs = now
                a.lastLevel = sample.levelPercent
                a.wattSum += sample.watts
                a.sampleCount++
                if (sample.watts > a.peakW) a.peakW = sample.watts
            }
            !charging && a != null -> {
                finish(context, a)
                active = null
            }
        }
    }

    private fun finish(context: Context, a: ActiveSession) {
        val durationMs = a.lastTs - a.startTs
        if (durationMs < 30_000 || a.sampleCount == 0) return
        val session = ChargeSession(
            startTs = a.startTs,
            endTs = a.lastTs,
            startLevel = a.startLevel,
            endLevel = a.lastLevel,
            plugged = a.plugged,
            avgWatts = a.wattSum / a.sampleCount,
            peakWatts = a.peakW,
            energyWh = a.energyWh,
        )
        val dao = AppDatabase.get(context).sessionDao()
        ioScope.launch { dao.insert(session) }
    }
}
