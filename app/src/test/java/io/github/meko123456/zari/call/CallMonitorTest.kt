package io.github.meko123456.zari.call

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Every test here is a sequence Android really produces. The awkward ones — repeated broadcasts
 * and call waiting — are the reason this class exists at all.
 */
class CallMonitorTest {

    private val monitor = CallMonitor()

    private fun feed(vararg states: PhoneState): List<CallAction> {
        val actions = mutableListOf<CallAction>()
        var at = 1_000L
        for (state in states) {
            monitor.onState(state, at)?.let(actions::add)
            at += 1_000
        }
        return actions
    }

    @Test
    fun `an answered incoming call records and is kept`() {
        val actions = feed(PhoneState.RINGING, PhoneState.OFFHOOK, PhoneState.IDLE)
        assertEquals(2, actions.size, "one start and one stop: $actions")
        val start = actions.first() as CallAction.Start
        assertEquals(CallDirection.INCOMING, start.direction)
        assertTrue(actions.last() is CallAction.Stop)
    }

    @Test
    fun `an outgoing call has no ringing state on this side`() {
        val actions = feed(PhoneState.OFFHOOK, PhoneState.IDLE)
        val start = actions.first() as CallAction.Start
        assertEquals(CallDirection.OUTGOING, start.direction)
        assertTrue(actions.last() is CallAction.Stop)
    }

    @Test
    fun `a missed call is discarded rather than saved as an empty recording`() {
        val actions = feed(PhoneState.RINGING, PhoneState.IDLE)
        assertEquals(1, actions.size)
        assertTrue(
            actions.single() is CallAction.Discard,
            "a call that only rang leaves nothing worth keeping: $actions",
        )
    }

    @Test
    fun `repeated broadcasts of the same state do nothing`() {
        // Android delivers PHONE_STATE more than once per change - once per SIM among other
        // reasons - and a second start would abandon the recording already in progress.
        val actions = feed(
            PhoneState.RINGING, PhoneState.RINGING,
            PhoneState.OFFHOOK, PhoneState.OFFHOOK, PhoneState.OFFHOOK,
            PhoneState.IDLE, PhoneState.IDLE,
        )
        assertEquals(2, actions.size, "still exactly one start and one stop: $actions")
    }

    @Test
    fun `a second call arriving mid-call does not restart the recording`() {
        // The state genuinely goes OFFHOOK -> RINGING -> OFFHOOK while call waiting beeps. A
        // naive reading calls that a new incoming call.
        val actions = feed(
            PhoneState.OFFHOOK,
            PhoneState.RINGING,
            PhoneState.OFFHOOK,
            PhoneState.IDLE,
        )
        assertEquals(2, actions.size, "one start, one stop: $actions")
        assertEquals(CallDirection.OUTGOING, (actions.first() as CallAction.Start).direction)
    }

    @Test
    fun `direction is remembered from the start of the call not recomputed at the end`() {
        feed(PhoneState.RINGING, PhoneState.OFFHOOK)
        assertTrue(monitor.isRecording)
        // Call waiting arrives; the call being recorded is still the incoming one.
        val duringWaiting = monitor.onState(PhoneState.RINGING, 9_000)
        assertNull(duringWaiting, "no action while already recording")
        assertTrue(monitor.isRecording)
    }

    @Test
    fun `going idle without a call produces nothing`() {
        assertNull(monitor.onState(PhoneState.IDLE, 1_000))
        assertFalse(monitor.isRecording)
    }

    @Test
    fun `a rejected call after ringing is discarded once and only once`() {
        val first = feed(PhoneState.RINGING, PhoneState.IDLE)
        assertTrue(first.single() is CallAction.Discard)
        // A later idle must not discard again.
        assertNull(monitor.onState(PhoneState.IDLE, 20_000))
    }

    @Test
    fun `reset clears a call in progress`() {
        feed(PhoneState.RINGING, PhoneState.OFFHOOK)
        monitor.reset()
        assertFalse(monitor.isRecording)
        assertEquals(PhoneState.IDLE, monitor.state)
        // And the next outgoing call is read as outgoing, not as the remembered incoming one.
        val actions = feed(PhoneState.OFFHOOK)
        assertEquals(CallDirection.OUTGOING, (actions.single() as CallAction.Start).direction)
    }

    @Test
    fun `platform state strings map to states and anything else is ignored`() {
        assertEquals(PhoneState.IDLE, CallMonitor.parse("IDLE"))
        assertEquals(PhoneState.RINGING, CallMonitor.parse("RINGING"))
        assertEquals(PhoneState.OFFHOOK, CallMonitor.parse("OFFHOOK"))
        assertNull(CallMonitor.parse("PRECISE_CALL_STATE"))
        assertNull(CallMonitor.parse(null))
    }
}
