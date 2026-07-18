package com.ezyapp.wattflow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sqrt

/** Result of one finished benchmark run, before any save. */
data class BenchmarkOutcome(
    val avgWatts: Double,
    val peakWatts: Double,
    val stabilityPct: Double,
    val plugged: Int,
    val startLevel: Int,
    val endLevel: Int,
) {
    /** A-F, absolute battery-side watts scale. */
    val grade: String
        get() = when {
            avgWatts >= 25 -> "A"
            avgWatts >= 15 -> "B"
            avgWatts >= 8 -> "C"
            avgWatts >= 3 -> "D"
            else -> "F"
        }
}

object BenchmarkEngine {
    const val DURATION_S = 60

    /**
     * Samples 1 Hz for [DURATION_S] seconds. Returns null if charging stopped
     * mid-run. [onTick] reports (secondsElapsed, currentWatts) for the UI.
     */
    suspend fun run(
        reader: BatteryReader,
        onTick: (Int, Double) -> Unit,
    ): BenchmarkOutcome? {
        val watts = ArrayList<Double>(DURATION_S)
        var first: BatterySample? = null
        var last: BatterySample? = null
        repeat(DURATION_S) { i ->
            val s = reader.read()
            if (s == null || !s.isCharging || s.plugged == 0) return null
            if (first == null) first = s
            last = s
            watts.add(abs(s.watts))
            onTick(i + 1, abs(s.watts))
            delay(1000)
        }
        val avg = watts.average()
        val peak = watts.max()
        val stdev = sqrt(watts.sumOf { (it - avg) * (it - avg) } / watts.size)
        val stability =
            if (avg <= 0) 0.0 else ((1.0 - stdev / avg) * 100.0).coerceIn(0.0, 100.0)
        return BenchmarkOutcome(
            avgWatts = avg,
            peakWatts = peak,
            stabilityPct = stability,
            plugged = first!!.plugged,
            startLevel = first!!.levelPercent,
            endLevel = last!!.levelPercent,
        )
    }
}

private enum class BenchPhase { IDLE, RUNNING, DONE }

@Composable
fun BenchmarkDialog(
    isCharging: Boolean,
    isPro: Boolean,
    onLockedFeature: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dao = remember { AppDatabase.get(context).sessionDao() }
    val saved by dao.benchmarks().collectAsState(initial = emptyList())

    var phase by remember { mutableStateOf(BenchPhase.IDLE) }
    var seconds by remember { mutableIntStateOf(0) }
    var liveWatts by remember { mutableDoubleStateOf(0.0) }
    var outcome by remember { mutableStateOf<BenchmarkOutcome?>(null) }
    var label by remember { mutableStateOf("") }
    var aborted by remember { mutableStateOf(false) }

    if (phase == BenchPhase.RUNNING) {
        LaunchedEffect(Unit) {
            val result = BenchmarkEngine.run(BatteryReader(context)) { s, w ->
                seconds = s
                liveWatts = w
            }
            if (result == null) {
                aborted = true
                phase = BenchPhase.IDLE
            } else {
                outcome = result
                phase = BenchPhase.DONE
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (phase != BenchPhase.RUNNING) onDismiss() },
        title = { Text(stringResource(R.string.bench_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                when (phase) {
                    BenchPhase.IDLE -> {
                        Text(
                            text = stringResource(R.string.bench_intro),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (aborted) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.bench_aborted),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        if (!isCharging) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.bench_plug_first),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }

                    BenchPhase.RUNNING -> {
                        Text(
                            text = String.format(
                                java.util.Locale.US, "%.1f W", liveWatts
                            ),
                            fontSize = 40.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = {
                                seconds / BenchmarkEngine.DURATION_S.toFloat()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(
                                R.string.bench_running,
                                BenchmarkEngine.DURATION_S - seconds,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    BenchPhase.DONE -> outcome?.let { o ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = o.grade,
                                fontSize = 56.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.padding(horizontal = 12.dp))
                            Column {
                                BenchStat(
                                    stringResource(R.string.history_avg_power),
                                    String.format(java.util.Locale.US, "%.1f W", o.avgWatts),
                                )
                                BenchStat(
                                    stringResource(R.string.label_peak),
                                    String.format(java.util.Locale.US, "%.1f W", o.peakWatts),
                                )
                                BenchStat(
                                    stringResource(R.string.bench_stability),
                                    String.format(java.util.Locale.US, "%.0f%%", o.stabilityPct),
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        if (isPro) {
                            OutlinedTextField(
                                value = label,
                                onValueChange = { label = it },
                                label = { Text(stringResource(R.string.bench_name_hint)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(4.dp))
                            TextButton(
                                onClick = {
                                    val o2 = o
                                    scope.launch {
                                        dao.insertBenchmark(
                                            BenchmarkResult(
                                                ts = System.currentTimeMillis(),
                                                label = label.trim(),
                                                plugged = o2.plugged,
                                                avgWatts = o2.avgWatts,
                                                peakWatts = o2.peakWatts,
                                                stabilityPct = o2.stabilityPct,
                                                startLevel = o2.startLevel,
                                                endLevel = o2.endLevel,
                                            )
                                        )
                                    }
                                    phase = BenchPhase.IDLE
                                    label = ""
                                }
                            ) { Text(stringResource(R.string.bench_save)) }
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = stringResource(R.string.bench_save_pro),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .weight(1f)
                                        .align(Alignment.CenterVertically),
                                )
                                TextButton(onClick = onLockedFeature) { Text("🔒") }
                            }
                        }
                    }
                }

                if (saved.isNotEmpty() && phase != BenchPhase.RUNNING) {
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.bench_saved_title),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    saved.forEachIndexed { i, b ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = (if (i == 0) "🏆 " else "") +
                                        b.label.ifBlank {
                                            stringResource(R.string.bench_unnamed)
                                        },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (i == 0) FontWeight.Bold else null,
                                )
                                Text(
                                    text = String.format(
                                        java.util.Locale.US,
                                        "%.1f W avg • %.1f W peak • %.0f%%",
                                        b.avgWatts, b.peakWatts, b.stabilityPct,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(
                                onClick = { scope.launch { dao.deleteBenchmark(b.id) } }
                            ) { Text("✕") }
                        }
                    }
                }
            }
        },
        confirmButton = {
            when (phase) {
                BenchPhase.IDLE -> TextButton(
                    onClick = {
                        aborted = false
                        seconds = 0
                        phase = BenchPhase.RUNNING
                    },
                    enabled = isCharging,
                ) { Text(stringResource(R.string.bench_start)) }

                BenchPhase.RUNNING -> TextButton(onClick = {}, enabled = false) {
                    Text(stringResource(R.string.bench_start))
                }

                BenchPhase.DONE -> TextButton(
                    onClick = {
                        aborted = false
                        seconds = 0
                        phase = BenchPhase.RUNNING
                    },
                    enabled = isCharging,
                ) { Text(stringResource(R.string.bench_again)) }
            }
        },
        dismissButton = {
            if (phase != BenchPhase.RUNNING) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        },
    )
}

@Composable
private fun BenchStat(label: String, value: String) {
    Row {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}
