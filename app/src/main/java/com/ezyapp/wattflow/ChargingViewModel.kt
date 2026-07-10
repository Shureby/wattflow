package com.ezyapp.wattflow

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ChargingUiState(
    val sample: BatterySample? = null,
    val history: List<Double> = emptyList(),   // watts, newest last
    val peakInWatts: Double = 0.0,             // max charging power seen
    val peakOutWatts: Double = 0.0,            // max discharge power seen
    val etaMinutes: Int? = null,               // time to full (charging) or empty (draining)
)

class ChargingViewModel(app: Application) : AndroidViewModel(app) {

    private val reader = BatteryReader(app)

    private val _uiState = MutableStateFlow(ChargingUiState())
    val uiState: StateFlow<ChargingUiState> = _uiState.asStateFlow()

    private val dao = AppDatabase.get(app).sessionDao()

    val sessions: StateFlow<List<ChargeSession>> =
        dao.recent()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    suspend fun curveFor(sessionIds: List<Long>): List<SessionSample> =
        sessionIds.flatMap { dao.samplesFor(it) }.sortedBy { it.ts }

    /** All sessions as CSV, oldest first. */
    suspend fun sessionsCsv(): String {
        val sb = StringBuilder(
            "id,direction,start_iso,end_iso,start_level,end_level,plugged," +
                "avg_watts,peak_watts,energy_wh\n"
        )
        val iso = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
        dao.allSessions().forEach { s ->
            sb.append(s.id).append(',')
                .append(if (s.direction == DIRECTION_CHARGE) "charge" else "discharge")
                .append(',')
                .append(iso.format(java.util.Date(s.startTs))).append(',')
                .append(iso.format(java.util.Date(s.endTs))).append(',')
                .append(s.startLevel).append(',')
                .append(s.endLevel).append(',')
                .append(s.plugged).append(',')
                .append(String.format(java.util.Locale.US, "%.3f", s.avgWatts)).append(',')
                .append(String.format(java.util.Locale.US, "%.3f", s.peakWatts)).append(',')
                .append(String.format(java.util.Locale.US, "%.4f", s.energyWh)).append('\n')
        }
        return sb.toString()
    }

    // Exponential moving average of |current| smooths the ETA against
    // per-second charge controller jitter.
    private var emaCurrentA = 0.0

    init {
        viewModelScope.launch {
            while (isActive) {
                val sample = withContext(Dispatchers.IO) { reader.read() }
                if (sample != null) {
                    SessionRecorder.onSample(getApplication(), sample)
                    val absA = kotlin.math.abs(sample.currentA)
                    emaCurrentA = if (emaCurrentA == 0.0) absA
                    else 0.85 * emaCurrentA + 0.15 * absA
                    _uiState.value = _uiState.value.let { s ->
                        s.copy(
                            sample = sample,
                            history = (s.history + sample.watts).takeLast(HISTORY_SIZE),
                            peakInWatts = maxOf(s.peakInWatts, sample.watts),
                            peakOutWatts = maxOf(s.peakOutWatts, -sample.watts),
                            etaMinutes = etaMinutes(sample, emaCurrentA),
                        )
                    }
                }
                delay(POLL_MS)
            }
        }
    }

    /**
     * Charging: time until full, from the coulomb counter and level-implied
     * full capacity. Discharging: time until empty. Null when the device has
     * no coulomb counter or the estimate would be nonsense.
     */
    private fun etaMinutes(s: BatterySample, avgA: Double): Int? {
        if (s.chargeCounterUah <= 0 || avgA < 0.01 || s.levelPercent !in 1..100) return null
        val remainAh = s.chargeCounterUah / 1_000_000.0
        val hours = if (s.isCharging) {
            if (s.levelPercent >= 100) return null
            val fullAh = remainAh * 100.0 / s.levelPercent
            (fullAh - remainAh) / avgA
        } else {
            remainAh / avgA
        }
        if (hours <= 0 || hours > 99) return null
        return (hours * 60).toInt()
    }

    companion object {
        const val HISTORY_SIZE = 60
        const val POLL_MS = 1000L
    }
}
