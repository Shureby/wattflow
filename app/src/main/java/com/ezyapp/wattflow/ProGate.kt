package com.ezyapp.wattflow

import android.app.Activity
import android.content.Context
import kotlinx.coroutines.flow.StateFlow

/**
 * Pro feature gate. Implemented per flavor:
 * - foss: always unlocked (GitHub build)
 * - play: unlocked via Google Play Billing one-time purchase
 */
interface ProGate {
    val isPro: StateFlow<Boolean>

    /** Localized price of the Pro unlock, null until loaded (play flavor only). */
    val priceText: StateFlow<String?>

    fun init(context: Context)
    fun launchPurchase(activity: Activity)
}

/** Flavor-provided singleton (see src/foss and src/play). */
val Pro: ProGate = ProGateFactory.create()
