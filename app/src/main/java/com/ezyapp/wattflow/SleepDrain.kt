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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * One night's drain, 23:00 - 07:00. Coverage says how much of that
 * window actually had recorded discharge samples — without it a night
 * with one 20-minute session would look impossibly efficient.
 */
data class SleepNight(
    val nightEndDayTs: Long,   // midnight of the morning the window ends on
    val drainWh: Double,
    val avgW: Double,          // over covered time, not the whole window
    val coveragePct: Double,
)

private const val NIGHT_START_HOUR = 23
private const val NIGHT_END_HOUR = 7

/** Overlap-prorated drain for the last [nights] nights. */
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
    ).filter { it.direction == DIRECTION_DISCHARGE }

    repeat(nights) {
        morning.add(Calendar.DAY_OF_YEAR, -1)
        val dayStart = morning.timeInMillis
        val windowEnd = dayStart + NIGHT_END_HOUR * 3600_000L
        val windowStart = dayStart - (24 - NIGHT_START_HOUR) * 3600_000L
        if (windowStart > System.currentTimeMillis()) return@repeat

        var coveredMs = 0L
        var wh = 0.0
        sessions.forEach { s ->
            val a = maxOf(s.startTs, windowStart)
            val b = minOf(s.endTs, windowEnd)
            if (b > a) {
                val frac = (b - a).toDouble() / (s.endTs - s.startTs)
                coveredMs += b - a
                wh += s.energyWh * frac
            }
        }
        if (coveredMs > 0) {
            out.add(
                SleepNight(
                    nightEndDayTs = dayStart,
                    drainWh = wh,
                    avgW = wh / (coveredMs / 3600_000.0),
                    coveragePct = coveredMs * 100.0 /
                        (windowEnd - windowStart),
                )
            )
        }
    }
    out
}

@Composable
fun SleepDrainDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val nightsState by produceState<List<SleepNight>?>(initialValue = null) {
        value = buildSleepReport(AppDatabase.get(context).sessionDao())
    }
    val dateFmt = SimpleDateFormat("EEE, MMM d", Locale.getDefault())

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
                val nights = nightsState
                when {
                    nights == null -> {}
                    nights.isEmpty() -> Text(
                        text = stringResource(R.string.sleep_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else -> nights.forEach { n ->
                        Column(Modifier.padding(vertical = 6.dp)) {
                            Text(
                                text = dateFmt.format(Date(n.nightEndDayTs)),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                            Spacer(Modifier.height(2.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = String.format(
                                        Locale.US, "%.2f Wh", n.drainWh
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    text = String.format(
                                        Locale.US, "%.2f W", n.avgW
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    text = stringResource(
                                        R.string.sleep_coverage,
                                        n.coveragePct.toInt(),
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
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
}
