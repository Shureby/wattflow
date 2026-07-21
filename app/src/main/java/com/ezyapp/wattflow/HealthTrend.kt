package com.ezyapp.wattflow

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

enum class HealthTag {
    INSUFFICIENT, NORMAL, BASELINE, LONG_GAP, HIGH_EXCLUDED, LOW_EXCLUDED, BIG_JUMP_ASK, NO_DATA
}

data class HealthPoint(
    val ts: Long,
    val counterMah: Double?,
    val tag: HealthTag,
    val gapDaysBefore: Long? = null,
    val floorBoundaryAbove: Boolean = false,
    // Only set for BIG_JUMP_ASK, to phrase the inline question.
    val jumpPrevTs: Long? = null,
    val jumpPrevMah: Double? = null,
)

data class HealthReport(
    val points: List<HealthPoint>,   // newest-first, ready to render
    val trendPct: Double?,
    val baselineTs: Long?,
    val baselineMah: Double?,
    val headlineTs: Long?,
    val headlineFallback: Boolean,
)

private const val FLOOR_COUNT = 7
private const val WINDOW_SIZE = 7
private const val Z_THRESHOLD = 3.5
private const val BIG_JUMP_RATIO = 1.05
private const val GAP_DAYS_THRESHOLD = 60L
private const val DAY_MS = 86_400_000.0

private fun median(values: List<Double>): Double {
    val s = values.sorted()
    return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2.0
}

/** Modified z-score (Iglewicz & Hoaglin): 0.6745*(x-median)/MAD. Empty
 * sample can't judge an outlier, so it never flags. */
private fun modifiedZScore(x: Double, sample: List<Double>): Double {
    if (sample.isEmpty()) return 0.0
    val med = median(sample)
    val mad = median(sample.map { abs(it - med) })
    if (mad == 0.0) return if (x == med) 0.0 else Double.MAX_VALUE
    return 0.6745 * (x - med) / mad
}

/**
 * Builds the health trend report. Baseline is the running max of
 * non-anomalous readings (not the first reading); outlier detection
 * uses a rate-of-change-from-baseline modified z-score over the last
 * [WINDOW_SIZE] readings, so a long calendar gap doesn't get compared
 * at the same absolute tolerance as a short one. The first [FLOOR_COUNT]
 * readings don't get statistical treatment at all -- not enough sample
 * to trust MAD yet.
 *
 * A same-direction jump of >=5% versus the immediately preceding
 * reading, while that reading is still the latest one on record, is
 * flagged for the user to resolve directly (statistics can't tell
 * noise from a battery replacement) rather than guessed at. Answers
 * aren't persisted -- see [confirmedReplacements]/[declinedReplacements].
 */
suspend fun buildHealthReport(
    dao: ChargeSessionDao,
    confirmedReplacements: Set<Long> = emptySet(),
    declinedReplacements: Set<Long> = emptySet(),
): HealthReport = withContext(Dispatchers.IO) {
    val events = dao.fullChargeHistory().sortedBy { it.ts }
    val analyzable = events.filter { it.chargeCounterUah > 0 }
    val noDataPoints = events.filter { it.chargeCounterUah <= 0 }
        .map { HealthPoint(it.ts, null, HealthTag.NO_DATA) }

    if (analyzable.isEmpty()) {
        return@withContext HealthReport(
            points = noDataPoints.sortedByDescending { it.ts },
            trendPct = null, baselineTs = null, baselineMah = null,
            headlineTs = null, headlineFallback = false,
        )
    }

    val tags = arrayOfNulls<HealthTag>(analyzable.size)
    val gapDays = arrayOfNulls<Long>(analyzable.size)
    val windowRates = ArrayDeque<Double>()
    var baselineIdx = 0
    var baselineMah = analyzable[0].chargeCounterUah / 1000.0

    for (i in analyzable.indices) {
        val mah = analyzable[i].chargeCounterUah / 1000.0
        if (i > 0) {
            val days = (analyzable[i].ts - analyzable[i - 1].ts) / DAY_MS
            if (days >= GAP_DAYS_THRESHOLD) gapDays[i] = days.toLong()
        }

        if (i < FLOOR_COUNT) {
            tags[i] = HealthTag.INSUFFICIENT
            if (mah > baselineMah) { baselineMah = mah; baselineIdx = i }
            continue
        }

        val prevMah = analyzable[i - 1].chargeCounterUah / 1000.0
        val isLatest = i == analyzable.lastIndex
        val bigJump = mah > prevMah * BIG_JUMP_RATIO
        val ts = analyzable[i].ts

        tags[i] = when {
            ts in confirmedReplacements -> {
                baselineMah = mah; baselineIdx = i
                windowRates.clear()
                HealthTag.BASELINE
            }
            bigJump && isLatest && ts !in declinedReplacements -> HealthTag.BIG_JUMP_ASK
            bigJump -> HealthTag.HIGH_EXCLUDED
            else -> {
                val daysSinceBaseline =
                    maxOf(1.0, (ts - analyzable[baselineIdx].ts) / DAY_MS)
                val rate = (mah - baselineMah) / daysSinceBaseline / baselineMah * 100.0
                val z = modifiedZScore(rate, windowRates.toList())
                val result = when {
                    abs(z) <= Z_THRESHOLD -> {
                        if (mah > baselineMah) {
                            if (baselineIdx >= FLOOR_COUNT) tags[baselineIdx] = HealthTag.NORMAL
                            baselineMah = mah; baselineIdx = i
                            HealthTag.BASELINE
                        } else HealthTag.NORMAL
                    }
                    rate > 0 -> HealthTag.HIGH_EXCLUDED
                    else -> HealthTag.LOW_EXCLUDED
                }
                windowRates.addLast(rate)
                if (windowRates.size > WINDOW_SIZE) windowRates.removeFirst()
                result
            }
        }
    }
    if (baselineIdx >= FLOOR_COUNT) tags[baselineIdx] = HealthTag.BASELINE

    val floorBoundary = analyzable.size > FLOOR_COUNT

    val points = analyzable.mapIndexed { i, e ->
        val displayTag = if (tags[i] == HealthTag.NORMAL && gapDays[i] != null) {
            HealthTag.LONG_GAP
        } else {
            tags[i]!!
        }
        HealthPoint(
            ts = e.ts,
            counterMah = e.chargeCounterUah / 1000.0,
            tag = displayTag,
            gapDaysBefore = gapDays[i],
            floorBoundaryAbove = floorBoundary && i == FLOOR_COUNT - 1,
            jumpPrevTs = if (tags[i] == HealthTag.BIG_JUMP_ASK && i > 0) analyzable[i - 1].ts else null,
            jumpPrevMah = if (tags[i] == HealthTag.BIG_JUMP_ASK && i > 0) {
                analyzable[i - 1].chargeCounterUah / 1000.0
            } else null,
        )
    }

    var headlineIdx = analyzable.lastIndex
    while (headlineIdx >= 0 &&
        (tags[headlineIdx] == HealthTag.HIGH_EXCLUDED ||
            tags[headlineIdx] == HealthTag.LOW_EXCLUDED ||
            tags[headlineIdx] == HealthTag.BIG_JUMP_ASK)
    ) headlineIdx--

    val trendPct = if (headlineIdx >= 0) {
        val headlineMah = analyzable[headlineIdx].chargeCounterUah / 1000.0
        (headlineMah - baselineMah) / baselineMah * 100.0
    } else null

    HealthReport(
        points = (points + noDataPoints).sortedByDescending { it.ts },
        trendPct = trendPct,
        baselineTs = analyzable[baselineIdx].ts,
        baselineMah = baselineMah,
        headlineTs = if (headlineIdx >= 0) analyzable[headlineIdx].ts else null,
        headlineFallback = headlineIdx in 0 until analyzable.lastIndex,
    )
}

@Composable
fun HealthTrendDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var answers by remember { mutableStateOf(mapOf<Long, Boolean>()) }
    val report by produceState<HealthReport?>(initialValue = null, answers) {
        val dao = AppDatabase.get(context).sessionDao()
        value = buildHealthReport(
            dao,
            confirmedReplacements = answers.filterValues { it }.keys,
            declinedReplacements = answers.filterValues { !it }.keys,
        )
    }
    val dateFmt = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    val today = Calendar.getInstance()
    var showInsufficientInfo by remember { mutableStateOf(false) }

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
                        if (r.trendPct != null) {
                            HealthHeadlineCard(r, dateFmt)
                            Spacer(Modifier.height(4.dp))
                        }
                        if (r.baselineTs != null && r.baselineMah != null) {
                            Text(
                                text = stringResource(
                                    R.string.health_baseline_line,
                                    dateFmt.format(Date(r.baselineTs)),
                                    r.baselineMah.toInt(),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(10.dp))
                        }
                        r.points.forEach { p ->
                            if (p.gapDaysBefore != null) {
                                GapMarker(
                                    stringResource(R.string.health_gap_marker, p.gapDaysBefore)
                                )
                            }
                            if (p.floorBoundaryAbove) {
                                GapMarker(stringResource(R.string.health_floor_marker))
                            }
                            HealthRow(
                                p, dateFmt, today, answers,
                                onAnswer = { ts, yes -> answers = answers + (ts to yes) },
                                onInsufficientInfoClick = { showInsufficientInfo = true },
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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

    if (showInsufficientInfo) {
        AlertDialog(
            onDismissRequest = { showInsufficientInfo = false },
            title = { Text(stringResource(R.string.health_tag_insufficient)) },
            text = { Text(stringResource(R.string.health_note_insufficient)) },
            confirmButton = {
                TextButton(onClick = { showInsufficientInfo = false }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
        )
    }
}

private fun isToday(ts: Long, today: Calendar): Boolean {
    val d = Calendar.getInstance().apply { timeInMillis = ts }
    return d.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
        d.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
}

@Composable
private fun HealthHeadlineCard(r: HealthReport, dateFmt: SimpleDateFormat) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(14.dp)
    ) {
        Text(
            text = String.format(Locale.US, "%+.1f%%", r.trendPct),
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        if (r.headlineFallback && r.headlineTs != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.health_headline_fallback, dateFmt.format(Date(r.headlineTs))),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun GapMarker(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun tagColor(tag: HealthTag): Pair<Color, Color> = when (tag) {
    HealthTag.NORMAL -> AppColors.success to AppColors.successContainer
    HealthTag.BASELINE -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.primaryContainer
    HealthTag.LONG_GAP -> AppColors.info to AppColors.infoContainer
    HealthTag.HIGH_EXCLUDED, HealthTag.LOW_EXCLUDED -> MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.errorContainer
    HealthTag.BIG_JUMP_ASK -> AppColors.warning to AppColors.warningContainer
    HealthTag.INSUFFICIENT, HealthTag.NO_DATA -> MaterialTheme.colorScheme.onSurfaceVariant to MaterialTheme.colorScheme.surfaceVariant
}

@Composable
private fun tagLabel(tag: HealthTag): String = stringResource(
    when (tag) {
        HealthTag.NORMAL -> R.string.health_tag_normal
        HealthTag.BASELINE -> R.string.health_tag_baseline
        HealthTag.LONG_GAP -> R.string.health_tag_long_gap
        HealthTag.HIGH_EXCLUDED -> R.string.health_tag_high
        HealthTag.LOW_EXCLUDED -> R.string.health_tag_low
        HealthTag.BIG_JUMP_ASK -> R.string.health_tag_jump
        HealthTag.INSUFFICIENT -> R.string.health_tag_insufficient
        HealthTag.NO_DATA -> R.string.health_tag_no_data
    }
)

@Composable
private fun HealthRow(
    p: HealthPoint,
    dateFmt: SimpleDateFormat,
    today: Calendar,
    answers: Map<Long, Boolean>,
    onAnswer: (Long, Boolean) -> Unit,
    onInsufficientInfoClick: () -> Unit,
) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = dateFmt.format(Date(p.ts)) +
                    if (isToday(p.ts, today)) " (${stringResource(R.string.health_today)})" else "",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = p.counterMah?.let { String.format(Locale.US, "%.0f mAh", it) }
                    ?: stringResource(R.string.health_tag_no_data),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                textDecoration = if (p.tag == HealthTag.HIGH_EXCLUDED || p.tag == HealthTag.LOW_EXCLUDED) {
                    TextDecoration.LineThrough
                } else null,
                color = if (p.tag == HealthTag.HIGH_EXCLUDED || p.tag == HealthTag.LOW_EXCLUDED) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.height(4.dp))
        val (fg, bg) = tagColor(p.tag)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(bg)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(tagLabel(p.tag), fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = fg)
            }
            if (p.tag == HealthTag.INSUFFICIENT) {
                IconButton(onClick = onInsufficientInfoClick, modifier = Modifier.size(20.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = stringResource(R.string.health_tag_insufficient),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
        }

        val noteRes = when (p.tag) {
            HealthTag.HIGH_EXCLUDED -> R.string.health_note_high
            HealthTag.LOW_EXCLUDED -> R.string.health_note_low
            else -> null
        }
        if (noteRes != null) {
            Spacer(Modifier.height(3.dp))
            Text(
                text = stringResource(noteRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (p.tag == HealthTag.LONG_GAP && p.gapDaysBefore != null) {
            Spacer(Modifier.height(3.dp))
            Text(
                text = stringResource(R.string.health_note_long_gap, p.gapDaysBefore),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (p.tag == HealthTag.BIG_JUMP_ASK && answers[p.ts] == null) {
            Spacer(Modifier.height(6.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(AppColors.warningContainer)
                    .padding(10.dp)
            ) {
                val pct = if (p.jumpPrevMah != null && p.counterMah != null) {
                    ((p.counterMah - p.jumpPrevMah) / p.jumpPrevMah * 100).toInt()
                } else 0
                Text(
                    text = stringResource(
                        R.string.health_jump_question,
                        pct,
                        p.jumpPrevTs?.let { dateFmt.format(Date(it)) } ?: "",
                        p.jumpPrevMah?.toInt() ?: 0,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { onAnswer(p.ts, true) }) {
                        Text(stringResource(R.string.health_jump_yes))
                    }
                    TextButton(onClick = { onAnswer(p.ts, false) }) {
                        Text(stringResource(R.string.health_jump_no))
                    }
                    TextButton(onClick = { onAnswer(p.ts, false) }) {
                        Text(stringResource(R.string.health_jump_unsure))
                    }
                }
            }
        }
    }
}
