package com.ezyapp.wattflow

/**
 * Presentation-layer session: one or more stored [ChargeSession] segments of
 * the same direction, merged across short sampling gaps. Stored data stays
 * raw; merging is a reversible display preference (see [RawModePrefs]).
 */
data class DisplaySession(
    val ids: List<Long>,
    val startTs: Long,
    val endTs: Long,
    val startLevel: Int,
    val endLevel: Int,
    val plugged: Int,
    val direction: Int,
    val avgWatts: Double,      // weighted by sampled duration
    val peakWatts: Double,
    val energyWh: Double,      // sampled intervals only
    val sampledMs: Long,
    val segments: Int,
    val interrupted: Boolean,  // recording stopped without a clean end (process killed)
)

private fun ChargeSession.toDisplay() = DisplaySession(
    ids = listOf(id),
    startTs = startTs,
    endTs = endTs,
    startLevel = startLevel,
    endLevel = endLevel,
    plugged = plugged,
    direction = direction,
    avgWatts = avgWatts,
    peakWatts = peakWatts,
    energyWh = energyWh,
    sampledMs = endTs - startTs,
    segments = 1,
    interrupted = interrupted,
)

/**
 * Merge same-direction sessions separated by gaps of at most [gapMs].
 * Input in any order; output newest-first.
 */
fun mergeSessions(
    sessions: List<ChargeSession>,
    raw: Boolean,
    gapMs: Long = 5 * 60_000L,
): List<DisplaySession> {
    if (raw) return sessions.sortedByDescending { it.startTs }.map { it.toDisplay() }

    val out = ArrayList<DisplaySession>()
    for (s in sessions.sortedBy { it.startTs }) {
        val last = out.lastOrNull()
        if (last != null &&
            last.direction == s.direction &&
            s.startTs - last.endTs in 0..gapMs
        ) {
            val segMs = s.endTs - s.startTs
            val totalSampled = last.sampledMs + segMs
            out[out.size - 1] = last.copy(
                ids = last.ids + s.id,
                endTs = s.endTs,
                endLevel = s.endLevel,
                avgWatts = if (totalSampled > 0) {
                    (last.avgWatts * last.sampledMs + s.avgWatts * segMs) / totalSampled
                } else last.avgWatts,
                peakWatts = maxOf(last.peakWatts, s.peakWatts),
                energyWh = last.energyWh + s.energyWh,
                sampledMs = totalSampled,
                segments = last.segments + 1,
                interrupted = s.interrupted,
            )
        } else {
            out += s.toDisplay()
        }
    }
    out.reverse()
    return out
}
