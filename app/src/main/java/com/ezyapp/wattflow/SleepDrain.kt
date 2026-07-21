package com.ezyapp.wattflow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class SleepNightKind { DISCHARGE, CHARGE_HELD, MIXED }

/**
 * One night, 23:00 - 07:00. A night can be spent draining, held on the
 * charger, or both (unplugged/plugged partway through) — kind picks
 * which story the dialog tells. trackedMs/windowMs replaces the old bare
 * coverage percentage with a real duration, so a fully-tracked short
 * window and a partially-tracked long one don't both read as "82%".
 */
data class SleepNight(
    val nightEndDayTs: Long,
    val kind: SleepNightKind,
    val drainWh: Double,       // 0 for a pure CHARGE_HELD night
    val avgW: Double,          // over covered discharge time; 0 if none
    val trackedMs: Long,
    val windowMs: Long,
    val chargeMs: Long = 0,    // total charging overlap, any state
    // ms between when a charging session actually reached 100% and
    // session end, overlapped with this night's window -- see
    // estimateReachedFullTs for how that moment is known (exact) or
    // inferred (trickle-detection fallback for older sessions).
    val heldAtFullMs: Long = 0,
    val heldSinceTs: Long? = null,       // earliest such moment, for display
    val chargeSpanStart: Long? = null,   // MIXED only: charge portion's clock range
    val chargeSpanEnd: Long? = null,
)

private const val NIGHT_START_HOUR = 23
private const val NIGHT_END_HOUR = 7
private const val HELD_AT_FULL_TIP_MS = 3_600_000L

/** Overlap-prorated report for the last [nights] nights. */
suspend fun buildSleepReport(
    dao: ChargeSessionDao,
    nights: Int = 7,
): List<SleepNight> = withContext(Dispatchers.IO) {
    val out = ArrayList<SleepNight>()
    val morning = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        add(Calendar.DAY_OF_YEAR, 1) // start with the coming/last night ended today
    }
    val sessions = dao.sessionsSince(
        morning.timeInMillis - nights.toLong() * 24 * 3600 * 1000 - 12 * 3600 * 1000
    )

    repeat(nights) {
        morning.add(Calendar.DAY_OF_YEAR, -1)
        val dayStart = morning.timeInMillis
        val windowEnd = dayStart + NIGHT_END_HOUR * 3600_000L
        val windowStart = dayStart - (24 - NIGHT_START_HOUR) * 3600_000L
        if (windowStart > System.currentTimeMillis()) return@repeat

        var dischargeMs = 0L
        var dischargeWh = 0.0
        var chargeMs = 0L
        var heldAtFullMs = 0L
        var heldSinceTs: Long? = null
        var chargeSpanStart: Long? = null
        var chargeSpanEnd: Long? = null

        sessions.forEach { s ->
            val a = maxOf(s.startTs, windowStart)
            val b = minOf(s.endTs, windowEnd)
            if (b > a) {
                val overlapMs = b - a
                if (s.direction == DIRECTION_DISCHARGE) {
                    val frac = overlapMs.toDouble() / (s.endTs - s.startTs)
                    dischargeMs += overlapMs
                    dischargeWh += s.energyWh * frac
                } else {
                    chargeMs += overlapMs
                    chargeSpanStart = chargeSpanStart?.let { minOf(it, a) } ?: a
                    chargeSpanEnd = chargeSpanEnd?.let { maxOf(it, b) } ?: b
                    val fullTs = estimateReachedFullTs(dao, s)
                    if (fullTs != null) {
                        val heldA = maxOf(fullTs, windowStart)
                        val heldB = minOf(s.endTs, windowEnd)
                        if (heldB > heldA) {
                            heldAtFullMs += heldB - heldA
                            heldSinceTs = heldSinceTs?.let { minOf(it, fullTs) } ?: fullTs
                        }
                    }
                }
            }
        }
        val trackedMs = dischargeMs + chargeMs
        if (trackedMs <= 0) return@repeat

        out.add(
            SleepNight(
                nightEndDayTs = dayStart,
                kind = when {
                    dischargeMs > 0 && chargeMs > 0 -> SleepNightKind.MIXED
                    chargeMs > 0 -> SleepNightKind.CHARGE_HELD
                    else -> SleepNightKind.DISCHARGE
                },
                drainWh = dischargeWh,
                avgW = if (dischargeMs > 0) {
                    dischargeWh / (dischargeMs / 3600_000.0)
                } else 0.0,
                trackedMs = trackedMs,
                windowMs = windowEnd - windowStart,
                chargeMs = chargeMs,
                heldAtFullMs = heldAtFullMs,
                heldSinceTs = heldSinceTs,
                chargeSpanStart = chargeSpanStart,
                chargeSpanEnd = chargeSpanEnd,
            )
        )
    }
    out
}

private data class SleepReportData(
    val nights: List<SleepNight>,
    val capacityWh: Double?,
)

@Composable
fun SleepDrainDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val data by produceState<SleepReportData?>(initialValue = null) {
        val dao = AppDatabase.get(context).sessionDao()
        value = SleepReportData(
            nights = buildSleepReport(dao),
            capacityWh = estimateFullChargeCapacityWh(dao),
        )
    }
    val dateFmt = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
    val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    var showCoverageInfo by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sleep_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = stringResource(R.string.sleep_intro),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                val d = data
                when {
                    d == null -> {}
                    d.nights.isEmpty() -> Text(
                        text = stringResource(R.string.sleep_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else -> d.nights.forEach { n ->
                        SleepNightRow(
                            n = n,
                            capacityWh = d.capacityWh,
                            dateFmt = dateFmt,
                            timeFmt = timeFmt,
                            onCoverageInfoClick = { showCoverageInfo = true },
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.ok))
            }
        },
    )

    if (showCoverageInfo) {
        AlertDialog(
            onDismissRequest = { showCoverageInfo = false },
            title = { Text(stringResource(R.string.sleep_coverage_info_title)) },
            text = { Text(stringResource(R.string.sleep_coverage_info_body)) },
            confirmButton = {
                TextButton(onClick = { showCoverageInfo = false }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
        )
    }
}

@Composable
private fun SleepNightRow(
    n: SleepNight,
    capacityWh: Double?,
    dateFmt: SimpleDateFormat,
    timeFmt: SimpleDateFormat,
    onCoverageInfoClick: () -> Unit,
) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Text(
            text = dateFmt.format(Date(n.nightEndDayTs)),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(4.dp))

        when (n.kind) {
            SleepNightKind.CHARGE_HELD -> {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(text = "🔌 ", fontSize = 20.sp)
                    Text(
                        text = stringResource(R.string.sleep_held_label),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (n.heldSinceTs != null) {
                    Text(
                        text = stringResource(
                            R.string.sleep_held_since,
                            timeFmt.format(Date(n.heldSinceTs)),
                            formatDuration(n.heldAtFullMs),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (n.heldAtFullMs >= HELD_AT_FULL_TIP_MS) {
                    Text(
                        text = stringResource(R.string.sleep_held_tip, stringResource(R.string.alert_charge_label)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            SleepNightKind.MIXED, SleepNightKind.DISCHARGE -> {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(text = "🌙 ", fontSize = 20.sp)
                    Text(
                        text = if (capacityWh != null && capacityWh > 0) {
                            stringResource(
                                R.string.sleep_pct_headline,
                                (n.drainWh / capacityWh * 100).toInt(),
                            )
                        } else {
                            String.format(Locale.US, "−%.2f Wh", n.drainWh)
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    text = stringResource(
                        R.string.sleep_substats,
                        String.format(Locale.US, "%.2f Wh", n.drainWh),
                        String.format(Locale.US, "%.2f W", n.avgW),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (capacityWh == null) {
                    Text(
                        text = stringResource(R.string.no_baseline_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (n.kind == SleepNightKind.MIXED && n.chargeSpanStart != null &&
                    n.chargeSpanEnd != null
                ) {
                    Text(
                        text = stringResource(
                            R.string.sleep_mixed_aside,
                            formatDuration(n.chargeMs),
                            "${timeFmt.format(Date(n.chargeSpanStart))}" +
                                "–${timeFmt.format(Date(n.chargeSpanEnd))}",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(
                    R.string.sleep_coverage_line,
                    formatDuration(n.trackedMs),
                    formatDuration(n.windowMs),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconButton(onClick = onCoverageInfoClick, modifier = Modifier.size(22.dp)) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = stringResource(R.string.sleep_coverage_info_title),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}
