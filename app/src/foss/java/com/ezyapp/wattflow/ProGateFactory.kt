package com.ezyapp.wattflow

import android.app.Activity
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** FOSS build: everything unlocked, no billing. */
object ProGateFactory {
    fun create(): ProGate = object : ProGate {
        override val isPro: StateFlow<Boolean> = MutableStateFlow(true)
        override val priceText: StateFlow<String?> = MutableStateFlow(null)
        override fun init(context: Context) {}
        override fun launchPurchase(activity: Activity) {}
    }
}
