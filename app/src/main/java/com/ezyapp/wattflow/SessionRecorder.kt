package com.ezyapp.wattflow

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Process-wide battery session recorder, both directions. Fed samples by the
 * UI poll loop and the monitor service; timestamps dedupe double-sampling.
 *
 * A session is a continuous run of charging (direction 0) or discharging
 * (direction 1). Sampling gaps over [MAX_GAP_MS] finalize the current session
 * — better a truthful short session than a fabricated long one.
 */
object SessionRecorder {

    private const val MIN_SESSION_MS = 30_000L
    private const val MAX_GAP_MS = 60_000L
    private const val CURVE_INTERVAL_MS = 10_000L
    private const val MAX_CURVE_POINTS = 4000

    private class ActiveSession(
        val direction: Int,
        val startTs: Long,
        val startLevel: Int,
        val plugged: Int,
    ) {
        var lastTs = startTs
        var lastLevel = startLevel
        var lastChargeCounter = -1L
        var peakW = 0.0
        var energyWh = 0.0
        var wattSum = 0.0
        var sampleCount = 0
        var lastCurveTs = 0L
        val curve = ArrayList<Pair<Long, Double>>()   // (ts, |watts|)
    }

    private var active: ActiveSession? = null
    private var lastSampleTs = 0L

    // Fire-and-forget insert scope; losing one session on process death is fine.
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Synchronized
    fun onSample(context: Context, sample: BatterySample) {
        val now = System.currentTimeMillis()
        if (now - lastSampleTs < 500) return
        lastSampleTs = now

        // Single choke point every sampling path goes through — alerts and
        // the home screen widget ride along.
        AlertEngine.onSample(context, sample)
        WattWidgetProvider.maybeUpdate(context)

        val direction = if (sample.isCharging && sample.plugged != 0) {
            DIRECTION_CHARGE
        } else {
            DIRECTION_DISCHARGE
        }

        var a = active
        // Direction change or long sampling gap ends the current session.
        if (a != null && (a.direction != direction || now - a.lastTs > MAX_GAP_MS)) {
            finish(context, a)
            a = null
        }
        if (a == null) {
            a = ActiveSession(direction, now, sample.levelPercent, sample.plugged)
            active = a
            return
        }

        val magnitude = abs(sample.watts)
        // Energy only over continuously sampled intervals — never extrapolate
        // instantaneous watts across a sampling gap.
        val dtMs = now - a.lastTs
        if (dtMs <= 10_000) {
            a.energyWh += magnitude * dtMs / 3_600_000.0
        }
        a.lastTs = now
        a.lastLevel = sample.levelPercent
        if (sample.chargeCounterUah > 0) a.lastChargeCounter = sample.chargeCounterUah
        a.wattSum += magnitude
        a.sampleCount++
        if (magnitude > a.peakW) a.peakW = magnitude

        if (now - a.lastCurveTs >= CURVE_INTERVAL_MS && a.curve.size < MAX_CURVE_POINTS) {
            a.lastCurveTs = now
            a.curve.add(now to magnitude)
        }
    }

    private fun finish(context: Context, a: ActiveSession) {
        val durationMs = a.lastTs - a.startTs
        if (durationMs < MIN_SESSION_MS || a.sampleCount == 0) return
        val session = ChargeSession(
            startTs = a.startTs,
            endTs = a.lastTs,
            startLevel = a.startLevel,
            endLevel = a.lastLevel,
            plugged = a.plugged,
            avgWatts = a.wattSum / a.sampleCount,
            peakWatts = a.peakW,
            energyWh = a.energyWh,
            direction = a.direction,
        )
        val curve = a.curve.toList()
        val fullCharge = a.direction == DIRECTION_CHARGE && a.lastLevel >= 100
        val fromLevel = a.startLevel
        val counter = a.lastChargeCounter
        val dao = AppDatabase.get(context).sessionDao()
        ioScope.launch {
            val sessionId = dao.insert(session)
            if (curve.isNotEmpty()) {
                dao.insertSamples(
                    curve.map { (ts, w) ->
                        SessionSample(sessionId = sessionId, ts = ts, watts = w)
                    }
                )
            }
            // Baseline for the battery-health trend (v1.3): energy needed to
            // reach full, plus the OS coulomb counter reading at 100%.
            if (fullCharge) {
                dao.insertFullCharge(
                    FullChargeEvent(
                        ts = session.endTs,
                        fromLevel = fromLevel,
                        energyWh = session.energyWh,
                        chargeCounterUah = counter,
                    )
                )
            }
        }
    }
}
