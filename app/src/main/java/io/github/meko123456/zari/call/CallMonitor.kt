package io.github.meko123456.zari.call

/** The three states Android reports through `PHONE_STATE`. */
enum class PhoneState { IDLE, RINGING, OFFHOOK }

/** Which way the call went, as far as the state sequence can tell. */
enum class CallDirection { INCOMING, OUTGOING, UNKNOWN }

/** What the service should do about it. */
sealed interface CallAction {
    data class Start(val direction: CallDirection, val atMillis: Long) : CallAction

    /** The call ended normally — keep whatever was recorded. */
    data class Stop(val atMillis: Long) : CallAction

    /**
     * Nothing worth keeping: a call that rang and was never answered. Distinguished from [Stop]
     * so the service deletes the file instead of leaving a pile of empty recordings behind.
     */
    data class Discard(val atMillis: Long) : CallAction
}

/**
 * Turns the sequence of phone states into start/stop decisions.
 *
 * This exists as a separate, pure class because the sequences are not obvious and every one of
 * them happens in ordinary use:
 *
 * | Sequence | Meaning |
 * |---|---|
 * | IDLE → RINGING → OFFHOOK → IDLE | incoming call, answered |
 * | IDLE → RINGING → IDLE | missed or rejected — nothing to keep |
 * | IDLE → OFFHOOK → IDLE | outgoing call (no ringing state on this side) |
 * | OFFHOOK → RINGING → OFFHOOK | a second call arriving during the first |
 *
 * The last one is why direction has to be remembered from the transition that *began* the call
 * rather than recomputed: mid-call the state briefly says RINGING, and a naive implementation
 * would decide the call it is already recording is a new incoming one.
 *
 * Android also re-broadcasts the same state repeatedly (once per SIM, among other reasons), so
 * every transition here is idempotent: only genuine changes produce an action.
 */
class CallMonitor {

    var state: PhoneState = PhoneState.IDLE
        private set

    /** True between a [CallAction.Start] and its matching stop. */
    var isRecording: Boolean = false
        private set

    private var sawRinging = false

    fun onState(next: PhoneState, atMillis: Long): CallAction? {
        if (next == state) return null
        val previous = state
        state = next

        return when (next) {
            PhoneState.RINGING -> {
                // Only mark "this call started by ringing" when it is actually a new call. During
                // an established call, RINGING means a second caller is waiting.
                if (!isRecording) sawRinging = true
                null
            }

            PhoneState.OFFHOOK -> {
                if (isRecording) return null // returning from call waiting, already recording
                isRecording = true
                val direction = if (sawRinging || previous == PhoneState.RINGING) {
                    CallDirection.INCOMING
                } else {
                    CallDirection.OUTGOING
                }
                CallAction.Start(direction, atMillis)
            }

            PhoneState.IDLE -> {
                val wasRecording = isRecording
                val ringingOnly = sawRinging && !wasRecording
                isRecording = false
                sawRinging = false
                when {
                    wasRecording -> CallAction.Stop(atMillis)
                    ringingOnly -> CallAction.Discard(atMillis)
                    else -> null
                }
            }
        }
    }

    /** Forget everything — used when the service is torn down mid-call. */
    fun reset() {
        state = PhoneState.IDLE
        isRecording = false
        sawRinging = false
    }

    companion object {
        /** Maps the platform's string constants without dragging Android into this class. */
        fun parse(state: String?): PhoneState? = when (state) {
            "IDLE" -> PhoneState.IDLE
            "RINGING" -> PhoneState.RINGING
            "OFFHOOK" -> PhoneState.OFFHOOK
            else -> null
        }
    }
}
