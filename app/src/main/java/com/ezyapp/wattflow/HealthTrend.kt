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
import java.util.Date
import java.util.Locale

/**
 * One full-charge calibration point. mAh comes from the OS coulomb
 * counter at 100% — absolute value may be per-cell on 2S phones, but
 * the trend over time is what matters.
 */
data class HealthPoint(
    val ts: Long,
    val counterMah: Double?,   // null when the device doesn't report it
)

data class HealthReport(
    val points: List<HealthPoint>,
    /** Percent change of latest vs first counter reading; null if <2 usable. */
    val trendPct: Double?,
)

suspend fun buildHealthReport(dao: ChargeSessionDao): HealthReport =
    withContext(Dispatchers.IO) {
        val events = dao.fullChargeHistory()
        val points = events.map { e ->
            HealthPoint(
                ts = e.ts,
                counterMah = if (e.chargeCounterUah > 0) {
                    e.chargeCounterUah / 1000.0
                } else null,
            )
        }
        val usable = points.mapNotNull { p -> p.counterMah?.let { p.ts to it } }
        val trend = if (usable.size >= 2) {
            (usable.last().second - usable.first().second) * 100.0 /
                usable.first().second
        } else null
        HealthReport(points, trend)
    }

@Composable
fun HealthTrendDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val report by produceState<HealthReport?>(initialValue = null) {
        value = buildHealthReport(AppDatabase.get(context).sessionDao())
    }
    val dateFmt = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.health_trend_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = stringResource(R.string.health_trend_intro),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                val r = report
                when {
                    r == null -> {}
                    r.points.isEmpty() -> Text(
                        text = stringResource(R.string.health_trend_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else -> {
                        r.trendPct?.let { pct ->
                            Text(
                                text = stringResource(
                                    R.string.health_trend_delta,
                                    String.format(Locale.US, "%+.1f%%", pct),
                                ),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (pct < -5) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        r.points.asReversed().forEach { p ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = dateFmt.format(Date(p.ts)),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    text = p.counterMah?.let {
                                        String.format(Locale.US, "%.0f mAh", it)
                                    } ?: "—",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                        }
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
