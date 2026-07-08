package com.ezyapp.chargingpower

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ChargingUiState(
    val sample: BatterySample? = null,
    val history: List<Double> = emptyList(),   // watts, newest last
    val peakInWatts: Double = 0.0,             // max charging power seen
    val peakOutWatts: Double = 0.0,            // max discharge power seen
)

class ChargingViewModel(app: Application) : AndroidViewModel(app) {

    private val reader = BatteryReader(app)

    private val _uiState = MutableStateFlow(ChargingUiState())
    val uiState: StateFlow<ChargingUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            while (isActive) {
                val sample = withContext(Dispatchers.IO) { reader.read() }
                if (sample != null) {
                    _uiState.value = _uiState.value.let { s ->
                        s.copy(
                            sample = sample,
                            history = (s.history + sample.watts).takeLast(HISTORY_SIZE),
                            peakInWatts = maxOf(s.peakInWatts, sample.watts),
                            peakOutWatts = maxOf(s.peakOutWatts, -sample.watts),
                        )
                    }
                }
                delay(POLL_MS)
            }
        }
    }

    companion object {
        const val HISTORY_SIZE = 60
        const val POLL_MS = 1000L
    }
}
