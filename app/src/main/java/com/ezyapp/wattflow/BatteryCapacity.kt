package com.ezyapp.wattflow

/**
 * Full-battery-capacity estimate in Wh, derived from the most recent
 * full-charge event with a large enough climb (>=20 points) to be
 * stable — energyWh / fraction climbed. A trickle top-off from 99% would
 * blow up this ratio, so those are excluded. Null until one qualifying
 * event exists.
 *
 * Deliberately Wh-based rather than mAh*voltage: energyWh already comes
 * from the same integrated V*A the rest of the app uses, so a device's
 * own systematic misreads (e.g. the dual-cell voltage bug) cancel out
 * when a later Wh figure is divided by this baseline, instead of
 * compounding through a second, independent measurement.
 */
suspend fun estimateFullChargeCapacityWh(dao: ChargeSessionDao): Double? {
    val latest = dao.fullChargeHistory()
        .filter { it.fromLevel <= 80 }
        .maxByOrNull { it.ts }
        ?: return null
    val climbedFraction = (100 - latest.fromLevel) / 100.0
    return latest.energyWh / climbedFraction
}

/**
 * When a charging session reached 100%, preferring the exact timestamp
 * recorded live (session.reachedFullTs) and falling back to trickle
 * detection off the stored watts curve for sessions recorded before that
 * field existed. Null if the session never reached ~100%, or the curve
 * doesn't show a clear enough tail to be confident.
 *
 * Trickle detection: charging tapers hard once the battery is full
 * (constant-current phase gives way to trickle/maintenance), so walking
 * the curve backward from session end while watts stays under a fraction
 * of the session's own peak finds where that taper began. Only a
 * *contiguous* low tail counts -- one noisy mid-session dip stops the
 * walk immediately rather than being mistaken for the finish. A weak or
 * short tail (low-power charger, or under 5 minutes of it) isn't enough
 * signal either way, so this returns null rather than guess.
 */
suspend fun estimateReachedFullTs(dao: ChargeSessionDao, session: ChargeSession): Long? {
    session.reachedFullTs?.let { return it }
    if (session.direction != DIRECTION_CHARGE || session.endLevel < 99) return null

    val samples = dao.samplesFor(session.id)
    if (samples.size < 5) return null
    val peak = samples.maxOf { it.watts }
    if (peak <= 0) return null
    val trickleCeiling = peak * 0.15

    var i = samples.size - 1
    while (i > 0 && samples[i - 1].watts <= trickleCeiling) i--
    val trickleStart = samples[i]
    val heldMs = session.endTs - trickleStart.ts
    return if (heldMs >= 5 * 60_000L) trickleStart.ts else null
}
