package io.github.meko123456.zari.call

/**
 * Decides whether the newest call-log row is the call we just recorded.
 *
 * This is a pure function because getting it wrong is silent and confusing, and it *was* wrong: the
 * first version rejected any row dated before the moment recording started. For an outgoing call
 * that is fine — the row is stamped when you dial, which is when recording starts. For an
 * **incoming** call the row is stamped when the phone started *ringing*, which is however many
 * seconds earlier you took to answer. So every answered incoming call was rejected, the number came
 * back null, the contact could not be looked up, and the recording was labelled "Unknown number"
 * even for people in the address book.
 *
 * The reliable discriminator is not the timestamp but *novelty*: read the newest row's timestamp
 * before recording starts, and afterwards accept only a row newer than that. The platform writes
 * the row when the call ends, so this also covers the race where the read happens first.
 */
object CallLogMatch {

    /**
     * @param rowDate the candidate row's `CallLog.Calls.DATE` — when the call began, meaning when
     *   it started ringing for an incoming call.
     * @param rowDurationSeconds the row's reported duration.
     * @param markBeforeCall newest row timestamp seen before recording began, or null when the
     *   call log could not be read then.
     * @param nowMillis the current time.
     */
    fun isOurs(
        rowDate: Long,
        rowDurationSeconds: Long,
        markBeforeCall: Long?,
        nowMillis: Long,
    ): Boolean {
        if (markBeforeCall != null) return rowDate > markBeforeCall
        // No mark to compare against, so fall back to "this call ended just now". Weaker, because
        // two calls in quick succession can both satisfy it, but far better than a guess.
        val endedAt = rowDate + rowDurationSeconds * 1_000
        return endedAt >= nowMillis - RECENTLY_ENDED_MILLIS
    }

    /** How recently a row must have ended to be plausibly the call that just finished. */
    const val RECENTLY_ENDED_MILLIS = 90_000L
}
