package com.ezyapp.wattflow

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.min

private val QA_ITEMS = listOf(
    R.string.qa_q1 to R.string.qa_a1,
    R.string.qa_q2 to R.string.qa_a2,
    R.string.qa_q3 to R.string.qa_a3,
    R.string.qa_q4 to R.string.qa_a4,
    R.string.qa_q5 to R.string.qa_a5,
    R.string.qa_q6 to R.string.qa_a6,
    R.string.qa_q7 to R.string.qa_a7,
    R.string.qa_q8 to R.string.qa_a8,
    R.string.qa_q9 to R.string.qa_a9,
    R.string.qa_q10 to R.string.qa_a10,
    R.string.qa_q11 to R.string.qa_a11,
    R.string.qa_q12 to R.string.qa_a12,
    R.string.qa_q13 to R.string.qa_a13,
    R.string.qa_q14 to R.string.qa_a14,
)

private val CHART_SERIES = listOf(
    ReferenceCurves.Series.DEVICE_A_WIRED to R.string.qa_device_a_wired,
    ReferenceCurves.Series.DEVICE_B_WIRED to R.string.qa_device_b_wired,
    ReferenceCurves.Series.DEVICE_C_WIRED to R.string.qa_device_c_wired,
    ReferenceCurves.Series.DEVICE_A_WIRELESS to R.string.qa_device_a_wireless,
)

private data class SeriesColor(val light: Color, val dark: Color)

// Validated categorical palette, fixed order — see dataviz skill palette.md.
private val SERIES_COLORS = listOf(
    SeriesColor(Color(0xFF2A78D6), Color(0xFF3987E5)),
    SeriesColor(Color(0xFFEB6834), Color(0xFFD95926)),
    SeriesColor(Color(0xFF1BAF7A), Color(0xFF199E70)),
    SeriesColor(Color(0xFFEDA100), Color(0xFFC98500)),
)

private const val CHART_Y_MAX = 90f
private const val CHART_X_MAX = 100f

@Composable
fun ChargingQaDialog(onDismiss: () -> Unit) {
    var showChartExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.qa_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ChargeCurvePreview(onExpand = { showChartExpanded = true })
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                QA_ITEMS.forEach { (qRes, aRes) ->
                    QaItemRow(question = stringResource(qRes), answer = stringResource(aRes))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.ok)) }
        },
    )

    if (showChartExpanded) {
        ChargeCurveExpandedDialog(onDismiss = { showChartExpanded = false })
    }
}

@Composable
private fun QaItemRow(question: String, answer: String) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(vertical = 12.dp),
    ) {
        Text(
            text = question,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
        if (expanded) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = answer,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun ChargeCurvePreview(onExpand: () -> Unit) {
    Column(Modifier.clickable(onClick = onExpand)) {
        Text(
            text = stringResource(R.string.qa_chart_title),
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.qa_chart_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Surface(
            modifier = Modifier.fillMaxWidth().height(150.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            ChargeCurveCanvas(
                showEstimated = false,
                interactive = false,
                modifier = Modifier.fillMaxSize().padding(8.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.qa_chart_expand_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun ChargeCurveExpandedDialog(onDismiss: () -> Unit) {
    val dark = isSystemInDarkTheme()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.qa_chart_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Surface(
                    modifier = Modifier.fillMaxWidth().height(240.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    ChargeCurveCanvas(
                        showEstimated = true,
                        interactive = true,
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = stringResource(R.string.qa_chart_style_measured),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = stringResource(R.string.qa_chart_style_estimated),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                CHART_SERIES.forEachIndexed { i, (_, labelRes) ->
                    val color = if (dark) SERIES_COLORS[i].dark else SERIES_COLORS[i].light
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 3.dp),
                    ) {
                        LegendSwatch(color)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(labelRes), style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.qa_chart_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.ok)) }
        },
    )
}

@Composable
private fun LegendSwatch(color: Color) {
    Box(modifier = Modifier.size(width = 14.dp, height = 3.dp).background(color))
}

@Composable
private fun ChargeCurveCanvas(showEstimated: Boolean, interactive: Boolean, modifier: Modifier) {
    val dark = isSystemInDarkTheme()
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val crosshairColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 9.sp, color = labelColor)
    var crosshairPct by remember { mutableStateOf<Float?>(null) }
    val estPrefix = stringResource(R.string.qa_chart_est_prefix)

    val canvasModifier = if (interactive) {
        modifier.pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { offset -> crosshairPct = (offset.x / size.width) * CHART_X_MAX },
                onDrag = { change, _ -> crosshairPct = (change.position.x / size.width) * CHART_X_MAX },
            )
        }
    } else {
        modifier
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(canvasModifier) {
            val bottomMargin = 18.dp.toPx()
            val plotHeight = size.height - bottomMargin

            var w = 0
            while (w <= 80) {
                val y = plotHeight * (1f - w / CHART_Y_MAX)
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                drawText(textMeasurer, "${w}W", topLeft = Offset(2f, y - 12.dp.toPx()), style = labelStyle)
                w += 20
            }

            var pct = 0
            while (pct <= 100) {
                val x = size.width * (pct / CHART_X_MAX)
                drawLine(
                    gridColor, Offset(x, 0f), Offset(x, plotHeight),
                    strokeWidth = 1.dp.toPx(), alpha = 0.5f,
                )
                val label = textMeasurer.measure("$pct%", labelStyle)
                drawText(
                    textMeasurer, "$pct%",
                    topLeft = Offset(
                        (x - label.size.width / 2f).coerceIn(0f, size.width - label.size.width),
                        plotHeight + 2.dp.toPx(),
                    ),
                    style = labelStyle,
                )
                pct += 20
            }

            CHART_SERIES.forEachIndexed { i, (series, _) ->
                val color = if (dark) SERIES_COLORS[i].dark else SERIES_COLORS[i].light
                val pts = series.points

                val path = Path()
                pts.forEachIndexed { j, (lvl, watts) ->
                    val x = size.width * (lvl / CHART_X_MAX)
                    val y = plotHeight * (1f - (min(watts, CHART_Y_MAX) / CHART_Y_MAX))
                    if (j == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, color, style = Stroke(2.dp.toPx()))

                if (showEstimated) {
                    val estPath = Path()
                    pts.forEachIndexed { j, (lvl, watts) ->
                        val est = watts / ReferenceCurves.efficiencyRatio(series, lvl)
                        val x = size.width * (lvl / CHART_X_MAX)
                        val y = plotHeight * (1f - (min(est, CHART_Y_MAX) / CHART_Y_MAX))
                        if (j == 0) estPath.moveTo(x, y) else estPath.lineTo(x, y)
                    }
                    drawPath(
                        estPath, color, alpha = 0.75f,
                        style = Stroke(
                            width = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
                        ),
                    )
                }
            }

            crosshairPct?.let { atPct ->
                val x = size.width * (atPct.coerceIn(0f, CHART_X_MAX) / CHART_X_MAX)
                drawLine(crosshairColor, Offset(x, 0f), Offset(x, plotHeight), strokeWidth = 1.dp.toPx())
            }
        }

        crosshairPct?.let { pct ->
            val levelAtCrosshair = pct.coerceIn(0f, CHART_X_MAX).toInt()
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text = "$levelAtCrosshair%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                CHART_SERIES.forEachIndexed { i, (series, _) ->
                    val nearest = series.points.minByOrNull { kotlin.math.abs(it.first - levelAtCrosshair) }
                    if (nearest != null) {
                        val color = if (dark) SERIES_COLORS[i].dark else SERIES_COLORS[i].light
                        val est = nearest.second / ReferenceCurves.efficiencyRatio(series, nearest.first)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LegendSwatch(color)
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "%.0fW / %s ~%.0fW".format(nearest.second, estPrefix, est),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
