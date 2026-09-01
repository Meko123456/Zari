package io.github.meko123456.zari.data

import io.github.meko123456.zari.call.CallDirection

/**
 * Builds the recording file name.
 *
 * The name carries the metadata on purpose: sorted date first, then who, then direction. If the
 * JSON index is ever lost the files are still self-describing in any file manager — which is the
 * difference between losing an index and losing the recordings.
 */
object FileNames {

    fun forCall(
        startedAtMillis: Long,
        number: String?,
        contactName: String?,
        direction: CallDirection,
        timestamp: (Long) -> String,
    ): String {
        val who = sanitise(contactName ?: number ?: "unknown")
        val arrow = when (direction) {
            CallDirection.INCOMING -> "in"
            CallDirection.OUTGOING -> "out"
            CallDirection.UNKNOWN -> "call"
        }
        return "${timestamp(startedAtMillis)}_${arrow}_$who.m4a"
    }

    /**
     * Strips anything that would break a file name on any of the filesystems these end up on
     * (including FAT32 on an SD card), collapses runs of separators, and caps the length so the
     * whole name stays well inside the 255-byte limit even with a long contact name.
     */
    fun sanitise(raw: String): String {
        val cleaned = raw.trim().map { character ->
            when {
                character.isLetterOrDigit() -> character
                character == '+' -> character
                else -> '-'
            }
        }.joinToString("")
        return cleaned
            .split('-')
            .filter { it.isNotEmpty() }
            .joinToString("-")
            .take(MAX_WHO)
            .ifEmpty { "unknown" }
    }

    private const val MAX_WHO = 48
}
