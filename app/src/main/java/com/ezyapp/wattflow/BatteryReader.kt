package com.ezyapp.wattflow

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import kotlin.math.abs

data class BatterySample(
    val watts: Double,          // dual-cell corrected when DualCell.active
    val rawWatts: Double,       // as computed from the fuel gauge, uncorrected
    val voltageV: Double,
    val currentA: Double,
    val plugged: Int,          // BatteryManager.BATTERY_PLUGGED_* or 0 when on battery
    val levelPercent: Int,
    val temperatureC: Double,
    val isCharging: Boolean,
    val chargeCounterUah: Long, // remaining charge in µAh, -1 when unsupported
)

class BatteryReader(private val context: Context) {

    private val batteryManager =
        context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager

    fun read(): BatterySample? {
        // Sticky broadcast: no receiver registration needed, returns latest snapshot.
        val intent = context.registerReceiver(
            null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        ) ?: return null

        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        val voltageMv = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val tempTenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)

        val rawCurrent =
            batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        if (rawCurrent == Int.MIN_VALUE || voltageMv <= 0) return null

        val currentUa = normalizeCurrentUa(rawCurrent, isCharging)

        val voltageV = voltageMv / 1000.0
        val currentA = currentUa / 1_000_000.0

        val rawCounter =
            batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        val chargeCounterUah =
            if (rawCounter == Int.MIN_VALUE || rawCounter <= 0) -1L else rawCounter.toLong()

        val rawWatts = voltageV * currentA
        return BatterySample(
            watts = rawWatts * DualCell.factor(context),
            rawWatts = rawWatts,
            voltageV = voltageV,
            currentA = currentA,
            plugged = plugged,
            levelPercent = if (level >= 0) level * 100 / scale else -1,
            temperatureC = tempTenths / 10.0,
            isCharging = isCharging,
            chargeCounterUah = chargeCounterUah,
        )
    }

    /**
     * OEM quirks:
     * - Spec says microamps, but some devices report milliamps. Values under
     *   10,000 are implausibly small for uA (10 mA), so treat them as mA.
     * - Sign convention differs per vendor. Normalize to: charging positive,
     *   discharging negative.
     */
    private fun normalizeCurrentUa(raw: Int, isCharging: Boolean): Long {
        var value = raw.toLong()
        if (abs(value) in 1 until 10_000) value *= 1000
        if (isCharging && value < 0) value = -value
        if (!isCharging && value > 0) value = -value
        return value
    }
}
