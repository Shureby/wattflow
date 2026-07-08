package com.ezyapp.wattflow

import android.content.Context
import android.os.BatteryManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocalePrefs.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val colorScheme =
                if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
            MaterialTheme(colorScheme = colorScheme) {
                ChargingScreen()
            }
        }
    }
}

@Composable
fun ChargingScreen(viewModel: ChargingViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    val sample = state.sample
    val context = LocalContext.current
    var showLanguageDialog by remember { mutableStateOf(false) }

    Scaffold { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (sample == null) {
                    Text(
                        text = stringResource(R.string.reading_battery),
                        style = MaterialTheme.typography.titleMedium,
                    )
                } else {
                    ChargingContent(sample = sample, state = state)
                }
            }

            IconButton(
                onClick = { showLanguageDialog = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = stringResource(R.string.language_title),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showLanguageDialog) {
        LanguageDialog(
            current = LocalePrefs.get(context),
            onSelect = { tag ->
                showLanguageDialog = false
                LocalePrefs.set(context, tag)
                (context as? ComponentActivity)?.recreate()
            },
            onDismiss = { showLanguageDialog = false },
        )
    }
}

// Native names on purpose: each option must be readable to its own speakers.
private val SUPPORTED_LANGUAGES = listOf(
    "en" to "English",
    "zh-CN" to "简体中文",
    "zh-TW" to "繁體中文",
    "es" to "Español",
    "ar" to "العربية",
    "in" to "Bahasa Indonesia",
    "pt" to "Português",
    "fr" to "Français",
    "ja" to "日本語",
    "ko" to "한국어",
    "ru" to "Русский",
    "de" to "Deutsch",
)

@Composable
private fun LanguageDialog(
    current: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.language_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                LanguageOption("", stringResource(R.string.language_system), current, onSelect)
                SUPPORTED_LANGUAGES.forEach { (tag, nativeName) ->
                    LanguageOption(tag, nativeName, current, onSelect)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

@Composable
private fun LanguageOption(
    tag: String,
    optionName: String,
    current: String,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect(tag) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = tag == current, onClick = { onSelect(tag) })
        Text(optionName, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ChargingContent(sample: BatterySample, state: ChargingUiState) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ChargeVisual(
            sample = sample,
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = stringResource(sourceLabelRes(sample.plugged)),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = String.format(Locale.US, "%.1f W", abs(sample.watts)),
            fontSize = 72.sp,
            fontWeight = FontWeight.Bold,
            color = if (sample.isCharging) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )

        Text(
            text = stringResource(
                if (sample.isCharging) R.string.into_battery else R.string.battery_drain
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(24.dp))

        StatsRow(
            sample = sample,
            peakInWatts = state.peakInWatts,
            peakOutWatts = state.peakOutWatts,
        )

        Spacer(Modifier.height(24.dp))

        PowerGraph(
            history = state.history,
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
        )
    }
}

// ---------------------------------------------------------------------------
// Animated charge visual: wired cable with flowing current dots, wireless
// pulsing waves, or plain battery with outflow dots when draining.
// ---------------------------------------------------------------------------

@Composable
private fun ChargeVisual(sample: BatterySample, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "chargeAnim")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "flow",
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val lineColor = MaterialTheme.colorScheme.outline
    val drainColor = MaterialTheme.colorScheme.error
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val level = sample.levelPercent.coerceIn(0, 100) / 100f

    val textMeasurer = rememberTextMeasurer()
    // White on the colored fill, theme color on the empty part.
    val percentLayout = textMeasurer.measure(
        text = "${sample.levelPercent}%",
        style = TextStyle(
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = if (level >= 0.6f) Color.White else onSurfaceColor,
        ),
    )

    Canvas(modifier = modifier) {
        when {
            sample.plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS ->
                drawWirelessVisual(
                    progress, primaryColor, lineColor, level, sample.isCharging, percentLayout,
                )

            sample.plugged != 0 ->
                drawWiredVisual(
                    progress, primaryColor, lineColor, level, sample.isCharging, percentLayout,
                )

            else ->
                drawOnBatteryVisual(
                    progress, drainColor, lineColor, level,
                    draining = sample.watts < -0.05,
                    percentLayout = percentLayout,
                )
        }
    }
}

private fun DrawScope.drawBatteryIcon(
    center: Offset,
    width: Float,
    height: Float,
    level: Float,
    outlineColor: Color,
    fillColor: Color,
    showBolt: Boolean,
    percentLayout: TextLayoutResult? = null,
) {
    val stroke = height * 0.07f
    val topLeft = Offset(center.x - width / 2, center.y - height / 2)

    drawRoundRect(
        color = outlineColor,
        topLeft = topLeft,
        size = Size(width, height),
        cornerRadius = CornerRadius(height * 0.18f),
        style = Stroke(stroke),
    )
    // terminal tip
    drawRoundRect(
        color = outlineColor,
        topLeft = Offset(topLeft.x + width + stroke * 0.7f, center.y - height * 0.2f),
        size = Size(height * 0.16f, height * 0.4f),
        cornerRadius = CornerRadius(height * 0.06f),
    )
    // charge level fill
    val inset = stroke * 1.7f
    if (level > 0f) {
        drawRoundRect(
            color = fillColor.copy(alpha = 0.85f),
            topLeft = Offset(topLeft.x + inset, topLeft.y + inset),
            size = Size((width - inset * 2) * level, height - inset * 2),
            cornerRadius = CornerRadius(height * 0.1f),
        )
    }
    // Bolt shifts left to leave room for the percent text.
    val boltCx = if (percentLayout != null) center.x - width * 0.27f else center.x
    if (showBolt) {
        val s = height
        val bolt = Path().apply {
            moveTo(boltCx + s * 0.06f, center.y - s * 0.32f)
            lineTo(boltCx - s * 0.14f, center.y + s * 0.06f)
            lineTo(boltCx - s * 0.01f, center.y + s * 0.06f)
            lineTo(boltCx - s * 0.06f, center.y + s * 0.32f)
            lineTo(boltCx + s * 0.14f, center.y - s * 0.06f)
            lineTo(boltCx + s * 0.01f, center.y - s * 0.06f)
            close()
        }
        drawPath(bolt, Color.White)
    }
    if (percentLayout != null) {
        val textCx = if (showBolt) center.x + width * 0.1f else center.x
        drawText(
            textLayoutResult = percentLayout,
            topLeft = Offset(
                textCx - percentLayout.size.width / 2f,
                center.y - percentLayout.size.height / 2f,
            ),
        )
    }
}

private fun DrawScope.drawWiredVisual(
    progress: Float,
    color: Color,
    lineColor: Color,
    level: Float,
    charging: Boolean,
    percentLayout: TextLayoutResult,
) {
    val cy = size.height / 2
    val batteryW = size.width * 0.28f
    val batteryH = batteryW * 0.45f
    val batteryCenter = Offset(size.width * 0.72f, cy)
    val cableEnd = Offset(batteryCenter.x - batteryW / 2 - batteryH * 0.35f, cy)

    // wall plug: two prongs + body
    val plugH = batteryH * 0.75f
    val plugW = size.width * 0.06f
    val plugRight = size.width * 0.15f
    for (dy in listOf(-plugH * 0.2f, plugH * 0.2f)) {
        drawLine(
            color = lineColor,
            start = Offset(size.width * 0.03f, cy + dy),
            end = Offset(plugRight - plugW, cy + dy),
            strokeWidth = plugH * 0.13f,
            cap = StrokeCap.Round,
        )
    }
    drawRoundRect(
        color = lineColor,
        topLeft = Offset(plugRight - plugW, cy - plugH / 2),
        size = Size(plugW, plugH),
        cornerRadius = CornerRadius(plugH * 0.2f),
    )

    // cable: gentle S-curve from plug to battery
    val cable = Path().apply {
        moveTo(plugRight, cy)
        cubicTo(
            size.width * 0.32f, cy - size.height * 0.3f,
            size.width * 0.42f, cy + size.height * 0.3f,
            cableEnd.x, cableEnd.y,
        )
    }
    drawPath(cable, lineColor, style = Stroke(width = batteryH * 0.09f, cap = StrokeCap.Round))

    drawBatteryIcon(batteryCenter, batteryW, batteryH, level, lineColor, color, charging, percentLayout)

    // current dots flowing along the cable toward the battery
    if (charging) {
        val pm = PathMeasure().apply { setPath(cable, false) }
        val len = pm.length
        for (i in 0 until 4) {
            val frac = (progress + i / 4f) % 1f
            val pos = pm.getPosition(len * frac)
            drawCircle(color = color, radius = batteryH * 0.12f, center = pos, alpha = 0.9f)
        }
    }
}

private fun DrawScope.drawWirelessVisual(
    progress: Float,
    color: Color,
    lineColor: Color,
    level: Float,
    charging: Boolean,
    percentLayout: TextLayoutResult,
) {
    val batteryW = size.width * 0.28f
    val batteryH = batteryW * 0.45f
    val batteryCenter = Offset(size.width / 2, size.height * 0.26f)
    drawBatteryIcon(batteryCenter, batteryW, batteryH, level, lineColor, color, charging, percentLayout)

    // charging pad
    val padW = batteryW * 1.1f
    val padY = size.height * 0.92f
    drawLine(
        color = lineColor,
        start = Offset(size.width / 2 - padW / 2, padY),
        end = Offset(size.width / 2 + padW / 2, padY),
        strokeWidth = batteryH * 0.13f,
        cap = StrokeCap.Round,
    )

    // waves rising from the pad toward the battery
    val waveCenter = Offset(size.width / 2, padY - batteryH * 0.1f)
    val maxR = (padY - (batteryCenter.y + batteryH / 2)) * 0.9f
    val count = 3
    for (i in 0 until count) {
        val frac = if (charging) (progress + i / count.toFloat()) % 1f
        else (i + 1) / (count + 1f)
        val alpha = if (charging) (1f - frac) * 0.9f else 0.35f
        val r = maxR * (0.25f + 0.75f * frac)
        drawArc(
            color = color.copy(alpha = alpha),
            startAngle = 225f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(waveCenter.x - r, waveCenter.y - r),
            size = Size(r * 2, r * 2),
            style = Stroke(width = batteryH * 0.1f, cap = StrokeCap.Round),
        )
    }
}

private fun DrawScope.drawOnBatteryVisual(
    progress: Float,
    drainColor: Color,
    lineColor: Color,
    level: Float,
    draining: Boolean,
    percentLayout: TextLayoutResult,
) {
    val batteryW = size.width * 0.28f
    val batteryH = batteryW * 0.45f
    val batteryCenter = Offset(size.width * 0.42f, size.height / 2)
    val fillColor = if (level <= 0.2f) drainColor else lineColor
    drawBatteryIcon(batteryCenter, batteryW, batteryH, level, lineColor, fillColor, false, percentLayout)

    // power flowing out (device usage or reverse charging an external device)
    if (draining) {
        val startX = batteryCenter.x + batteryW / 2 + batteryH * 0.4f
        val endX = size.width * 0.92f
        for (i in 0 until 3) {
            val frac = (progress + i / 3f) % 1f
            drawCircle(
                color = drainColor,
                radius = batteryH * 0.11f,
                center = Offset(startX + (endX - startX) * frac, batteryCenter.y),
                alpha = 1f - frac * 0.8f,
            )
        }
    }
}

// ---------------------------------------------------------------------------

@Composable
private fun StatsRow(sample: BatterySample, peakInWatts: Double, peakOutWatts: Double) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            StatItem(
                stringResource(R.string.stat_voltage),
                String.format(Locale.US, "%.2f V", sample.voltageV),
            )
            StatItem(
                stringResource(R.string.stat_current),
                String.format(Locale.US, "%.2f A", sample.currentA),
            )
            StatItem(
                stringResource(R.string.stat_temp),
                String.format(Locale.US, "%.1f°C", sample.temperatureC),
            )
            StatItem(
                stringResource(R.string.stat_peak_in),
                String.format(Locale.US, "%.1f W", peakInWatts),
            )
            StatItem(
                stringResource(R.string.stat_peak_out),
                String.format(Locale.US, "%.1f W", peakOutWatts),
            )
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PowerGraph(history: List<Double>, modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val zeroColor = MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            if (history.size < 2) return@Canvas

            // Scale symmetric around observed range so discharge (negative) is visible.
            val maxAbs = max(history.maxOf { abs(it) }, 1.0)
            val points = history.mapIndexed { i, w ->
                Offset(
                    x = size.width * i / (ChargingViewModel.HISTORY_SIZE - 1).toFloat(),
                    y = size.height * (1f - ((w / maxAbs).toFloat() + 1f) / 2f),
                )
            }

            // Zero line + quarter grid lines.
            for (frac in listOf(0.25f, 0.5f, 0.75f)) {
                val y = size.height * frac
                drawLine(
                    color = if (frac == 0.5f) zeroColor else gridColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx(),
                )
            }

            val path = Path().apply {
                moveTo(points.first().x, points.first().y)
                points.drop(1).forEach { lineTo(it.x, it.y) }
            }
            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(width = 3.dp.toPx()),
            )
        }
    }
}

private fun sourceLabelRes(plugged: Int): Int = when (plugged) {
    BatteryManager.BATTERY_PLUGGED_AC -> R.string.source_wired_ac
    BatteryManager.BATTERY_PLUGGED_USB -> R.string.source_wired_usb
    BatteryManager.BATTERY_PLUGGED_WIRELESS -> R.string.source_wireless
    BatteryManager.BATTERY_PLUGGED_DOCK -> R.string.source_dock
    else -> R.string.source_on_battery
}
