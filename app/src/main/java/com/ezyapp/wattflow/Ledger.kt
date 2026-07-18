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

/** One day of energy flow, battery side. */
data class LedgerDay(
    val dayStartTs: Long,
    val inWh: Double,
    val outWh: Double,
) {
    val netWh: Double get() = inWh - outWh
}

/** Groups the last [days] days of sessions into daily in/out totals. */
suspend fun buildLedger(dao: ChargeSessionDao, days: Int = 14): List<LedgerDay> =
    withContext(Dispatchers.IO) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, -(days - 1))
        }
        val since = cal.timeInMillis
        val byDay = LinkedHashMap<Long, DoubleArray>() // dayStart -> [in, out]
        dao.sessionsSince(since).forEach { s ->
            val day = Calendar.getInstance().apply {
                timeInMillis = s.endTs
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val slot = byDay.getOrPut(day) { DoubleArray(2) }
            if (s.direction == DIRECTION_CHARGE) slot[0] += s.energyWh
            else slot[1] += s.energyWh
        }
        byDay.entries
            .sortedByDescending { it.key }
            .map { (day, v) -> LedgerDay(day, v[0], v[1]) }
    }

@Composable
fun EnergyLedgerDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val days by produceState<List<LedgerDay>?>(initialValue = null) {
        value = buildLedger(AppDatabase.get(context).sessionDao())
    }
    val dateFmt = SimpleDateFormat("EEE, MMM d", Locale.getDefault())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ledger_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                val list = days
                when {
                    list == null -> {}
                    list.isEmpty() -> Text(
                        text = stringResource(R.string.ledger_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else -> list.forEach { d ->
                        Column(Modifier.padding(vertical = 6.dp)) {
                            Text(
                                text = dateFmt.format(Date(d.dayStartTs)),
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
                                        Locale.US, "↓ %.1f Wh", d.inWh
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = String.format(
                                        Locale.US, "↑ %.1f Wh", d.outWh
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                )
                                Text(
                                    text = String.format(
                                        Locale.US, "%+.1f Wh", d.netWh
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
