package io.github.meko123456.zari.ui

import io.github.meko123456.zari.call.MicProbe

/**
 * What "Measure every source now" is doing and what it found, as one line. The per-source rows are
 * the detail; this is the answer, and the one thing TalkBack is told as it changes.
 */
object ProbeStatus {

    /**
     * [results] come in as each source is measured; [total] is how many there are. [outsideCall]
     * is whether the probe ran with no call up, in which case healthy numbers prove nothing.
     */
    fun text(
        isProbing: Boolean,
        results: List<Pair<String, Int?>>,
        total: Int,
        outsideCall: Boolean,
        audibleThreshold: Int = MicProbe.AUDIBLE_THRESHOLD,
    ): String = when {
        isProbing && results.isEmpty() -> "Measuring…"
        isProbing -> "Measuring: ${results.size} of $total sources done"
        results.isEmpty() -> ""
        outsideCall ->
            "Measured with no call in progress, so this says nothing about call recording — every " +
                "source works outside a call. Run it again during one."
        else -> {
            val winner = results.firstOrNull { (_, peak) -> peak != null && peak >= audibleThreshold }
            if (winner != null) {
                "Measured during a call: ${winner.first} hears audio, peak ${winner.second}."
            } else {
                "Measured during a call: every source is silent."
            }
        }
    }
}
