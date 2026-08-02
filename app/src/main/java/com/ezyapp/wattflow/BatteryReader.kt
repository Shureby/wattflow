package com.ezyapp.wattflow

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import kotlin.math.abs

data class BatterySample(
    val watts: Double,
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

    // Persisted per-device: true once an unambiguous sample has proven this
    // device's BATTERY_PROPERTY_CURRENT_NOW is already true microamps, so the
    // mA-guess below must never fire again (see normalizeCurrentUa).
    private var nativeUaConfirmed: Boolean =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getBoolean(KEY_NATIVE_UA_CONFIRMED, false)

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
        val watts = voltageV * currentA

        // Backstop against any remaining single-tick driver glitch (unit
        // confusion before nativeUaConfirmed locks in, or unrelated ADC
        // noise): a real phone never legitimately hits these figures.
        val plausibleCapW = if (isCharging) MAX_PLAUSIBLE_CHARGE_W else MAX_PLAUSIBLE_DISCHARGE_W
        if (abs(watts) > plausibleCapW) return null

        val rawCounter =
            batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        val chargeCounterUah =
            if (rawCounter == Int.MIN_VALUE || rawCounter <= 0) -1L else rawCounter.toLong()

        return BatterySample(
            watts = watts,
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
     * - Spec says microamps, but some devices report milliamps. Raw values
     *   under 10,000 are ambiguous (could be a genuinely small µA reading,
     *   e.g. a few mA of idle drain, or a real mA value needing ×1000) --
     *   but a raw value >=10,000 is unambiguous proof of true µA, since no
     *   mA-native device legitimately reports a >10A draw. That proof is
     *   sticky per device: once seen, stop guessing mA for small readings,
     *   or genuine low idle current gets inflated ~1000x (seen on a Samsung
     *   A17 4G: ~8mA idle discharge misread as ~8A / ~34W).
     * - Sign convention differs per vendor. Normalize to: charging positive,
     *   discharging negative.
     */
    private fun normalizeCurrentUa(raw: Int, isCharging: Boolean): Long {
        var value = raw.toLong()
        if (abs(value) >= 10_000) {
            if (!nativeUaConfirmed) {
                nativeUaConfirmed = true
                context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                    .edit().putBoolean(KEY_NATIVE_UA_CONFIRMED, true).apply()
            }
        } else if (abs(value) in 1 until 10_000 && !nativeUaConfirmed) {
            value *= 1000
        }
        if (isCharging && value < 0) value = -value
        if (!isCharging && value > 0) value = -value
        return value
    }

    private companion object {
        const val KEY_NATIVE_UA_CONFIRMED = "battery_reader_native_ua_confirmed"
        const val MAX_PLAUSIBLE_CHARGE_W = 150.0
        const val MAX_PLAUSIBLE_DISCHARGE_W = 20.0
    }
}
