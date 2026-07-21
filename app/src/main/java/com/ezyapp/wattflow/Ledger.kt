package com.ezyapp.wattflow

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** One day of energy flow, battery side. Per-session Wh is kept (not just
 * the daily sum) so a >100% day can show what actually made up that total. */
data class LedgerDay(
    val dayStartTs: Long,
    val inSessions: List<Double>,
    val outSessions: List<Double>,
) {
    val inWh: Double get() = inSessions.sum()
    val outWh: Double get() = outSessions.sum()
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
        val byDay = LinkedHashMap<Long, Pair<MutableList<Double>, MutableList<Double>>>()
        dao.sessionsSince(since).forEach { s ->
            val day = Calendar.getInstance().apply {
                timeInMillis = s.endTs
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val slot = byDay.getOrPut(day) { mutableListOf<Double>() to mutableListOf() }
            if (s.direction == DIRECTION_CHARGE) slot.first.add(s.energyWh) else slot.second.add(s.energyWh)
        }
        byDay.entries
            .sortedByDescending { it.key }
            .map { (day, v) -> LedgerDay(day, v.first, v.second) }
    }

private data class LedgerData(val days: List<LedgerDay>, val capacityWh: Double?)

/** State for the >100% breakdown dialog -- which direction, and each
 * session's already-converted percentage (so the dialog itself doesn't
 * need capacityWh in scope). */
private data class BreakdownInfo(val isIn: Boolean, val sessionsPct: List<Int>, val totalPct: Int)

@Composable
fun EnergyLedgerDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val data by produceState<LedgerData?>(initialValue = null) {
        val dao = AppDatabase.get(context).sessionDao()
        value = LedgerData(
            days = buildLedger(dao),
            capacityWh = estimateFullChargeCapacityWh(dao),
        )
    }
    val dateFmt = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
    var breakdown by remember { mutableStateOf<BreakdownInfo?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ledger_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                val d = data
                when {
                    d == null -> {}
                    d.days.isEmpty() -> Text(
                        text = stringResource(R.string.ledger_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else -> d.days.forEach { day ->
                        LedgerDayRow(day, d.capacityWh, dateFmt) { breakdown = it }
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
                if (data?.capacityWh == null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.no_baseline_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.ok))
            }
        },
    )

    breakdown?.let { info ->
        AlertDialog(
            onDismissRequest = { breakdown = null },
            title = {
                Text(
                    stringResource(
                        if (info.isIn) R.string.ledger_breakdown_in_title
                        else R.string.ledger_breakdown_out_title
                    )
                )
            },
            text = {
                Text(
                    if (info.sessionsPct.size >= 2) {
                        val joined = info.sessionsPct.joinToString(" + ") { "$it%" }
                        stringResource(
                            if (info.isIn) R.string.ledger_breakdown_in_multi_body
                            else R.string.ledger_breakdown_out_multi_body,
                            info.sessionsPct.size,
                            joined,
                            info.totalPct,
                        )
                    } else {
                        stringResource(R.string.ledger_breakdown_single_body)
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = { breakdown = null }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
        )
    }
}

@Composable
private fun LedgerDayRow(
    day: LedgerDay,
    capacityWh: Double?,
    dateFmt: SimpleDateFormat,
    onBreakdownClick: (BreakdownInfo) -> Unit,
) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Text(
            text = dateFmt.format(Date(day.dayStartTs)),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            LedgerStat(
                sessions = day.inSessions,
                totalWh = day.inWh,
                capacityWh = capacityWh,
                isIn = true,
                onBreakdownClick = onBreakdownClick,
                modifier = Modifier.weight(1f),
            )
            LedgerStat(
                sessions = day.outSessions,
                totalWh = day.outWh,
                capacityWh = capacityWh,
                isIn = false,
                onBreakdownClick = onBreakdownClick,
                modifier = Modifier.weight(1f),
            )
            LedgerNetStat(day.netWh, capacityWh, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun LedgerStat(
    sessions: List<Double>,
    totalWh: Double,
    capacityWh: Double?,
    isIn: Boolean,
    onBreakdownClick: (BreakdownInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cap = capacityWh
    val pct = if (cap != null && cap > 0) (totalWh / cap * 100).toInt() else null
    Column(modifier, horizontalAlignment = if (isIn) Alignment.Start else Alignment.End) {
        Text(
            text = when {
                pct != null && isIn -> "→ $pct%"
                pct != null -> "$pct% →"
                isIn -> String.format(Locale.US, "→ %.1f Wh", totalWh)
                else -> String.format(Locale.US, "%.1f Wh →", totalWh)
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (isIn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = String.format(Locale.US, "%.1f Wh", totalWh),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (pct != null && pct > 100 && cap != null) {
                IconButton(
                    onClick = {
                        onBreakdownClick(
                            BreakdownInfo(
                                isIn = isIn,
                                sessionsPct = sessions.map { (it / cap * 100).toInt() }
                                    .filter { it != 0 },
                                totalPct = pct,
                            )
                        )
                    },
                    modifier = Modifier.size(18.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = stringResource(
                            if (isIn) R.string.ledger_breakdown_in_title
                            else R.string.ledger_breakdown_out_title
                        ),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun LedgerNetStat(netWh: Double, capacityWh: Double?, modifier: Modifier = Modifier) {
    val cap = capacityWh
    val text = if (cap != null && cap > 0) {
        stringResource(R.string.ledger_net, String.format(Locale.US, "%+d%%", (netWh / cap * 100).toInt()))
    } else {
        stringResource(R.string.ledger_net, String.format(Locale.US, "%+.1f Wh", netWh))
    }
    Column(modifier, horizontalAlignment = Alignment.End) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
