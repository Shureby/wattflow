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
    private const val CHECKPOINT_INTERVAL_MS = 15_000L
    private const val CHECKPOINT_PREFS = "session_checkpoint"

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
        var lastCheckpointTs = 0L
        var reachedFullTs = 0L  // first sample where level read 100, 0 = not yet
        var corrected = false   // any sample had the dual-cell factor applied
        val curve = ArrayList<Pair<Long, Double>>()   // (ts, |watts|)
    }

    private var active: ActiveSession? = null
    private var lastSampleTs = 0L
    private var recovered = false

    // Fire-and-forget insert scope; losing one session on process death is fine.
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Synchronized
    fun onSample(context: Context, sample: BatterySample) {
        val now = System.currentTimeMillis()
        if (now - lastSampleTs < 500) return
        lastSampleTs = now

        // Once per process lifetime: if the previous process incarnation was
        // killed mid-session (MIUI/OEM background killer, OOM, crash) rather
        // than ending normally, `active` was never persisted — it lived only
        // in this singleton's memory. Recover whatever was checkpointed so
        // the session shows up (marked interrupted) instead of vanishing.
        if (!recovered) {
            recovered = true
            recoverCheckpoint(context)
        }

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
        if (a.direction == DIRECTION_CHARGE && sample.levelPercent >= 100 && a.reachedFullTs == 0L) {
            a.reachedFullTs = now
        }
        if (sample.chargeCounterUah > 0) a.lastChargeCounter = sample.chargeCounterUah
        a.wattSum += magnitude
        a.sampleCount++
        if (sample.watts != sample.rawWatts) a.corrected = true
        if (magnitude > a.peakW) a.peakW = magnitude

        if (now - a.lastCurveTs >= CURVE_INTERVAL_MS && a.curve.size < MAX_CURVE_POINTS) {
            a.lastCurveTs = now
            a.curve.add(now to magnitude)
        }

        if (now - a.lastCheckpointTs >= CHECKPOINT_INTERVAL_MS) {
            a.lastCheckpointTs = now
            saveCheckpoint(context, a)
        }
    }

    private fun saveCheckpoint(context: Context, a: ActiveSession) {
        context.getSharedPreferences(CHECKPOINT_PREFS, Context.MODE_PRIVATE).edit()
            .putInt("direction", a.direction)
            .putLong("startTs", a.startTs)
            .putInt("startLevel", a.startLevel)
            .putInt("plugged", a.plugged)
            .putLong("lastTs", a.lastTs)
            .putInt("lastLevel", a.lastLevel)
            .putString("peakW", a.peakW.toString())
            .putString("energyWh", a.energyWh.toString())
            .putString("wattSum", a.wattSum.toString())
            .putInt("sampleCount", a.sampleCount)
            .putLong("reachedFullTs", a.reachedFullTs)
            .apply()
    }

    private fun clearCheckpoint(context: Context) {
        context.getSharedPreferences(CHECKPOINT_PREFS, Context.MODE_PRIVATE)
            .edit().clear().apply()
    }

    /** Turns a leftover checkpoint from a killed process into an interrupted session. */
    private fun recoverCheckpoint(context: Context) {
        val prefs = context.getSharedPreferences(CHECKPOINT_PREFS, Context.MODE_PRIVATE)
        val startTs = prefs.getLong("startTs", -1L)
        if (startTs < 0) return
        val lastTs = prefs.getLong("lastTs", startTs)
        val sampleCount = prefs.getInt("sampleCount", 0)
        val startLevel = prefs.getInt("startLevel", 0)
        val endLevel = prefs.getInt("lastLevel", startLevel)
        val direction = prefs.getInt("direction", DIRECTION_CHARGE)
        // A stale/corrupted checkpoint (e.g. battery level jumping while the
        // process was dead) can replay as a session whose level moves the
        // wrong way for its direction — discard rather than record garbage.
        val plausible = if (direction == DIRECTION_CHARGE) {
            endLevel >= startLevel - 1
        } else {
            endLevel <= startLevel + 1
        }
        if (lastTs - startTs >= MIN_SESSION_MS && sampleCount > 0 && plausible) {
            val session = ChargeSession(
                startTs = startTs,
                endTs = lastTs,
                startLevel = startLevel,
                endLevel = endLevel,
                plugged = prefs.getInt("plugged", 0),
                avgWatts = (prefs.getString("wattSum", "0")?.toDoubleOrNull() ?: 0.0) /
                    sampleCount,
                peakWatts = prefs.getString("peakW", "0")?.toDoubleOrNull() ?: 0.0,
                energyWh = prefs.getString("energyWh", "0")?.toDoubleOrNull() ?: 0.0,
                direction = direction,
                interrupted = true,
                reachedFullTs = prefs.getLong("reachedFullTs", 0L).takeIf { it > 0 },
            )
            val dao = AppDatabase.get(context).sessionDao()
            ioScope.launch { dao.insert(session) }
        }
        clearCheckpoint(context)
    }

    private fun finish(context: Context, a: ActiveSession) {
        clearCheckpoint(context)
        val durationMs = a.lastTs - a.startTs
        if (durationMs < MIN_SESSION_MS || a.sampleCount == 0) return
        // 2S heuristic needs raw energy, so only sessions recorded entirely
        // without the correction count as evidence.
        if (a.direction == DIRECTION_CHARGE && !a.corrected) {
            DualCell.onRawChargeSession(
                context,
                gainPercent = a.lastLevel - a.startLevel,
                energyWh = a.energyWh,
            )
        }
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
            reachedFullTs = a.reachedFullTs.takeIf { it > 0 },
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
