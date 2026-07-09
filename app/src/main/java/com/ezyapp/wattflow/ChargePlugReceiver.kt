package com.ezyapp.wattflow

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * ACTION_POWER_CONNECTED / DISCONNECTED / BATTERY_LOW are exempt implicit
 * broadcasts — manifest registration works on Android 8+.
 */
class ChargePlugReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        WattWidgetProvider.update(context)
        when (intent.action) {
            Intent.ACTION_POWER_CONNECTED -> {
                if (AlertPrefs.enabled(context)) AlertCheckWorker.enqueue(context)
            }
            Intent.ACTION_POWER_DISCONNECTED -> {
                AlertCheckWorker.cancel(context)
                // Unplugging resets the charge alert for the next session and
                // gives the low alert one immediate look.
                AlertPrefs.setChargeFired(context, false)
                if (AlertPrefs.enabled(context)) {
                    BatteryReader(context).read()?.let {
                        AlertEngine.onSample(context, it)
                    }
                }
            }
            Intent.ACTION_BATTERY_LOW -> {
                if (AlertPrefs.enabled(context)) {
                    BatteryReader(context).read()?.let {
                        AlertEngine.onSample(context, it)
                    }
                }
            }
        }
    }
}
