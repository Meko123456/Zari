package io.github.meko123456.zari.call

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The regression tests for the bug that made every answered incoming call say "Unknown number".
 */
class CallLogMatchTest {

    private val now = 1_756_000_000_000L

    @Test
    fun `an answered incoming call is matched even though its row predates the recording`() {
        // This is the bug. The row is stamped when the phone started ringing; recording starts
        // when the call is answered, twenty seconds later. Comparing the row against the
        // recording's start time rejected it, the number came back null, and a caller who was in
        // the address book was labelled "Unknown number".
        val ringingStarted = now - 20_000
        assertTrue(
            CallLogMatch.isOurs(
                rowDate = ringingStarted,
                rowDurationSeconds = 45,
                markBeforeCall = ringingStarted - 60_000,
                nowMillis = now,
            ),
        )
    }

    @Test
    fun `a long ring before answering is still matched`() {
        val mark = now - 300_000
        assertTrue(CallLogMatch.isOurs(now - 120_000, 30, mark, now), "two minutes of ringing")
    }

    @Test
    fun `the previous call's row is not mistaken for ours`() {
        // The platform writes our row when the call ends, so a read that happens first sees the
        // previous call. Accepting it would put the wrong person's number on the recording.
        val previousCall = now - 3_600_000
        assertFalse(CallLogMatch.isOurs(previousCall, 120, markBeforeCall = previousCall, nowMillis = now))
    }

    @Test
    fun `an outgoing call is matched`() {
        val mark = now - 500_000
        assertTrue(CallLogMatch.isOurs(now - 90_000, 88, mark, now))
    }

    @Test
    fun `with no mark a row that just ended is accepted`() {
        // The fallback, for when the call log could not be read before the call.
        assertTrue(CallLogMatch.isOurs(now - 60_000, 58, markBeforeCall = null, nowMillis = now))
    }

    @Test
    fun `with no mark a stale row is rejected`() {
        assertFalse(CallLogMatch.isOurs(now - 3_600_000, 60, markBeforeCall = null, nowMillis = now))
    }

    @Test
    fun `with no mark a missed call from minutes ago is rejected`() {
        // Duration zero, so it ended when it started - well outside the window.
        assertFalse(CallLogMatch.isOurs(now - 600_000, 0, markBeforeCall = null, nowMillis = now))
    }

    @Test
    fun `a row exactly at the mark is not ours because the mark was already there`() {
        val mark = now - 10_000
        assertFalse(CallLogMatch.isOurs(mark, 5, mark, now))
    }
}
