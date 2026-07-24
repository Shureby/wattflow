package com.ezyapp.wattflow

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.clickable
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
import kotlin.math.roundToInt
import kotlin.math.sqrt

// Above this end-of-run battery level, CV taper dominates the reading
// regardless of charger/cable quality (confirmed against a real 100 W
// charge log: grade only drops out of "A" around 93%+ on that device),
// so the grade is marked inconclusive instead of misleadingly low.
private const val GRADE_X_LEVEL = 90

/**
 * A-F, absolute battery-side watts scale; "X" (inconclusive) when
 * [endLevel] is high enough that charge-curve taper -- not the setup --
 * explains a low reading.
 */
fun benchmarkGrade(avgWatts: Double, endLevel: Int): String = when {
    endLevel >= GRADE_X_LEVEL -> "X"
    avgWatts >= 25 -> "A"
    avgWatts >= 15 -> "B"
    avgWatts >= 8 -> "C"
    avgWatts >= 3 -> "D"
    else -> "F"
}

/** Result of one finished benchmark run, before any save. */
data class BenchmarkOutcome(
    val avgWatts: Double,
    val peakWatts: Double,
    val stabilityPct: Double,
    val plugged: Int,
    val startLevel: Int,
    val endLevel: Int,
) {
    val grade: String get() = benchmarkGrade(avgWatts, endLevel)
}

val BenchmarkResult.grade: String get() = benchmarkGrade(avgWatts, endLevel)

private fun levelRangeText(start: Int, end: Int): String =
    if (start == end) "$start%" else "$start%–$end%"

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

/** Screen-off during the run risks the OS freezing the app before the OEM's
 * own screen-off charging speed can even be observed (confirmed on a Xiaomi
 * device: the sampling loop can stall for 60s+ once the screen sleeps, wake
 * locks and battery-optimization exemptions notwithstanding) -- so the test
 * keeps the screen on for its duration instead of trying to survive it
 * turning off. [bool] toggles [WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON].*/
private fun keepScreenOn(context: android.content.Context, on: Boolean) {
    val window = (context as? Activity)?.window ?: return
    if (on) {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    } else {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}

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
    var charger by remember { mutableStateOf("") }
    var cable by remember { mutableStateOf("") }
    var maxWText by remember { mutableStateOf("") }
    var aborted by remember { mutableStateOf(false) }
    var showResults by remember { mutableStateOf(false) }

    if (phase == BenchPhase.RUNNING) {
        LaunchedEffect(Unit) {
            keepScreenOn(context, true)
            val result = try {
                BenchmarkEngine.run(BatteryReader(context)) { s, w ->
                    seconds = s
                    liveWatts = w
                }
            } finally {
                keepScreenOn(context, false)
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
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.bench_screen_on_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                BenchStat(
                                    stringResource(R.string.bench_level),
                                    levelRangeText(o.startLevel, o.endLevel),
                                )
                            }
                        }
                        if (o.grade == "X") {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.bench_grade_x_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        if (isPro) {
                            OutlinedTextField(
                                value = charger,
                                onValueChange = { charger = it },
                                label = { Text(stringResource(R.string.bench_charger_hint)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(4.dp))
                            OutlinedTextField(
                                value = cable,
                                onValueChange = { cable = it },
                                label = { Text(stringResource(R.string.bench_cable_hint)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(4.dp))
                            OutlinedTextField(
                                value = maxWText,
                                onValueChange = { v -> maxWText = v.filter { it.isDigit() } },
                                label = { Text(stringResource(R.string.bench_max_w_hint)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            maxWText.toIntOrNull()?.takeIf { it > 0 }?.let { claimedMaxW ->
                                Text(
                                    text = stringResource(
                                        R.string.bench_pct_of_claimed,
                                        (o.avgWatts / claimedMaxW * 100).roundToInt(),
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            TextButton(
                                onClick = {
                                    val o2 = o
                                    // Snapshot before launch: the coroutine runs after
                                    // the state clears below, so reading it inside the
                                    // coroutine would always save blank values.
                                    val chargerText = charger.trim()
                                    val cableText = cable.trim()
                                    val labelText = if (cableText.isBlank()) {
                                        chargerText
                                    } else {
                                        "$chargerText + $cableText"
                                    }
                                    val maxW = maxWText.toIntOrNull()
                                    scope.launch {
                                        dao.insertBenchmark(
                                            BenchmarkResult(
                                                ts = System.currentTimeMillis(),
                                                label = labelText,
                                                charger = chargerText.ifBlank { null },
                                                cable = cableText.ifBlank { null },
                                                chargerMaxW = maxW,
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
                                    charger = ""
                                    cable = ""
                                    maxWText = ""
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
                    TextButton(onClick = { showResults = true }) {
                        Text(stringResource(R.string.bench_view_saved, saved.size))
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

    if (showResults) BenchmarkResultsDialog(onDismiss = { showResults = false })
}

@Composable
private fun BenchmarkSavedList(saved: List<BenchmarkResult>, onDelete: (Long) -> Unit) {
    var showXInfo by remember { mutableStateOf(false) }
    // avgWatts alone isn't a meaningful contest between two X-graded (inconclusive)
    // results, so the trophy skips them and goes to the best real grade instead.
    val trophyIndex = saved.indexOfFirst { it.grade != "X" }

    if (saved.size >= 2) {
        Text(
            text = stringResource(R.string.bench_compare_range_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }
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
                    text = (if (i == trophyIndex) "🏆 " else "") + b.grade +
                        (if (b.grade == "X") " ⓘ" else "") +
                        " (@${levelRangeText(b.startLevel, b.endLevel)}) · " +
                        b.label.ifBlank { stringResource(R.string.bench_unnamed) },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (i == trophyIndex) FontWeight.Bold else null,
                    modifier = if (b.grade == "X") {
                        Modifier.clickable { showXInfo = true }
                    } else Modifier,
                )
                Text(
                    text = String.format(
                        java.util.Locale.US,
                        "%.1f W avg • %.1f W peak • %.0f%% stable",
                        b.avgWatts, b.peakWatts, b.stabilityPct,
                    ) + (b.chargerMaxW?.let {
                        String.format(
                            java.util.Locale.US, " • ~%.0f%% of %dW",
                            b.avgWatts / it * 100, it,
                        )
                    } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = { onDelete(b.id) }) { Text("✕") }
        }
    }

    if (showXInfo) {
        AlertDialog(
            onDismissRequest = { showXInfo = false },
            text = { Text(stringResource(R.string.bench_grade_x_note)) },
            confirmButton = {
                TextButton(onClick = { showXInfo = false }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
        )
    }
}

/** View-only saved-results list for the Reports tab -- running a new
 * benchmark still lives on the Live tab, tied to the active charge. */
@Composable
fun BenchmarkResultsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dao = remember { AppDatabase.get(context).sessionDao() }
    val saved by dao.benchmarks().collectAsState(initial = emptyList())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.bench_saved_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (saved.isEmpty()) {
                    Text(
                        text = stringResource(R.string.bench_results_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    BenchmarkSavedList(saved) { id -> scope.launch { dao.deleteBenchmark(id) } }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.ok))
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
