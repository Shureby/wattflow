package com.ezyapp.wattflow

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * Autostart ("background auto-launch") is a separate permission layer that
 * several OEM Android skins bolt on top of stock battery-optimization
 * exemption -- without it, the OEM's own task killer can still stop the
 * foreground service after the screen has been off for a while. There is no
 * public Android API for it: no way to check whether it's granted, and the
 * settings screen to toggle it is a vendor-specific, undocumented Activity
 * that has moved across OS versions. We can only jump to it where a path is
 * known-good (Xiaomi/HyperOS, tested), and fall back to a manual pointer
 * everywhere else known to need one.
 */
object AutostartHelper {
    // Manufacturers whose skins are documented (dontkillmyapp.com et al.) to
    // kill background work beyond stock Doze/battery-optimization, i.e. worth
    // showing *some* guidance for even without a tested deep link.
    private val AGGRESSIVE_OEMS = setOf(
        "xiaomi", "redmi", "poco", "huawei", "honor", "oppo", "realme",
        "vivo", "iqoo", "oneplus", "meizu", "asus", "samsung", "letv",
    )

    fun isKnownAggressiveOem(): Boolean =
        Build.MANUFACTURER.lowercase() in AGGRESSIVE_OEMS

    /** Only Xiaomi/HyperOS has a verified-working deep link (tested devices). */
    private fun preciseIntent(context: Context): Intent? {
        if (Build.MANUFACTURER.lowercase() !in setOf("xiaomi", "redmi", "poco")) return null
        val intent = Intent().setComponent(
            ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity",
            )
        )
        return if (intent.resolveActivity(context.packageManager) != null) intent else null
    }

    /** True if it navigated straight to the OEM's autostart screen. */
    fun openSettings(context: Context): Boolean {
        val intent = preciseIntent(context) ?: return false
        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }
}

@Composable
fun AutostartRow() {
    if (!AutostartHelper.isKnownAggressiveOem()) return

    val context = LocalContext.current
    var showInfo by remember { mutableStateOf(false) }
    var showManualTip by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (!AutostartHelper.openSettings(context)) showManualTip = true
            }
            .padding(top = 10.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.autostart_row),
                style = MaterialTheme.typography.bodyMedium,
            )
            IconButton(onClick = { showInfo = true }, modifier = Modifier.size(28.dp)) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = stringResource(R.string.autostart_row),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }

    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            title = { Text(stringResource(R.string.autostart_row)) },
            text = { Text(stringResource(R.string.autostart_info_body)) },
            confirmButton = {
                TextButton(onClick = { showInfo = false }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
        )
    }

    if (showManualTip) {
        AlertDialog(
            onDismissRequest = { showManualTip = false },
            title = { Text(stringResource(R.string.autostart_row)) },
            text = { Text(stringResource(R.string.autostart_manual_tip)) },
            confirmButton = {
                TextButton(onClick = { showManualTip = false }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
        )
    }
}

/**
 * One-time, best-effort nudge: if background recording is on, the user
 * already granted battery-optimization exemption, and the last
 * [NIGHTS_TO_CHECK] nights all show zero tracked samples for a window the
 * device was awake through (not just powered off), the OEM's autostart
 * block is the most likely cause. Shown at most once ever, regardless of
 * whether the pattern recurs later -- a recurring low-key nag would be
 * worse than the gap itself.
 */
object AutostartNag {
    private const val PREFS = "autostart_nag"
    private const val KEY_SHOWN = "shown"
    private const val NIGHT_START_HOUR = 23
    private const val NIGHT_END_HOUR = 7
    private const val NIGHTS_TO_CHECK = 3

    private fun alreadyShown(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_SHOWN, false)

    private fun markShown(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_SHOWN, true).apply()
    }

    suspend fun shouldShow(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (alreadyShown(context)) return@withContext false
        if (!MonitorPrefs.enabled(context)) return@withContext false
        val pm = context.getSystemService(android.os.PowerManager::class.java)
        if (pm?.isIgnoringBatteryOptimizations(context.packageName) != true) {
            return@withContext false
        }

        val bootTimeMs = System.currentTimeMillis() - SystemClock.elapsedRealtime()
        val dao = AppDatabase.get(context).sessionDao()
        val morning = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val sessions = dao.sessionsSince(
            morning.timeInMillis - (NIGHTS_TO_CHECK + 1).toLong() * 24 * 3600 * 1000
        )

        var problemStreak = 0
        repeat(NIGHTS_TO_CHECK) {
            morning.add(Calendar.DAY_OF_YEAR, -1)
            val dayStart = morning.timeInMillis
            val windowEnd = dayStart + NIGHT_END_HOUR * 3600_000L
            val windowStart = dayStart - (24 - NIGHT_START_HOUR) * 3600_000L
            if (windowStart > System.currentTimeMillis()) return@repeat

            // Device was off for some/all of this window -- not our bug.
            if (bootTimeMs > windowStart) return@withContext false

            val tracked = sessions.any { s ->
                minOf(s.endTs, windowEnd) > maxOf(s.startTs, windowStart)
            }
            if (tracked) return@withContext false
            problemStreak++
        }
        problemStreak >= NIGHTS_TO_CHECK
    }

    suspend fun markShownNow(context: Context) = withContext(Dispatchers.IO) {
        markShown(context)
    }
}
