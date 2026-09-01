package io.github.meko123456.zari.data

import io.github.meko123456.zari.call.CallDirection

/**
 * One saved recording.
 *
 * @property number the other party, as the call log reported it. Null when the call log had
 *   nothing to say (a withheld number, or the permission was refused).
 * @property contactName resolved from [number] at save time rather than at display time, so a
 *   contact deleted later does not silently turn old recordings anonymous.
 * @property peakAmplitude loudest sample seen while recording, 0..32767. The reason this is
 *   stored: on Android 10+ a third-party app may be handed a *silent* microphone during a call,
 *   and a file full of digital zeroes is indistinguishable from a successful recording until you
 *   play it. Storing the peak lets the app say "this one is silent" instead of pretending.
 */
data class Recording(
    val fileName: String,
    val number: String?,
    val contactName: String?,
    val direction: CallDirection,
    val startedAtMillis: Long,
    val durationMillis: Long,
    val peakAmplitude: Int,
) {
    /**
     * Who to show: the contact name if the number is in the address book, otherwise the number
     * itself — a number you can read and call back is far more use than the word "unknown".
     * Only a genuinely absent number (withheld by the caller, or a call log that had no row for
     * this call) falls through to text.
     */
    val displayName: String
        get() = contactName ?: number ?: "Number withheld"

    /**
     * Below this the file is silence, not a quiet call. A real recording of a phone call peaks in
     * the thousands even at arm's length; 400 is roughly the noise floor of a muted input.
     */
    val isSilent: Boolean get() = peakAmplitude < SILENT_THRESHOLD

    companion object {
        const val SILENT_THRESHOLD = 400
    }
}
