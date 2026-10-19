package io.github.meko123456.zari.data

import io.github.meko123456.zari.call.CallDirection
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Rows for the recordings the index doesn't list, read back from their file names.
 *
 * The audio sits in the app's internal storage, where no file manager reaches it, so a recording
 * the index had lost — an index that wouldn't parse, or a save that never got its row — was on the
 * phone with no way to play, share or delete it, behind "No recordings yet". [FileNames] puts the
 * start, the direction and who into every name for exactly this. The duration is read from the
 * file; how loud it was exists only in the index, so a rebuilt row is never called silent.
 */
object RecoveredRecordings {

    /** [listed], plus a row for every recording in [files] that [listed] doesn't name. */
    fun merge(
        listed: List<Recording>,
        files: Collection<String>,
        zone: ZoneId,
        durationOf: (fileName: String) -> Long,
    ): List<Recording> {
        val known = listed.mapTo(HashSet()) { it.fileName }
        return listed + files.filter { it !in known }.mapNotNull { fromName(it, zone, durationOf) }
    }

    /**
     * The row [FileNames.forCall] described, or null for anything else in the directory: the index,
     * a recording still being written, the microphone self-test.
     */
    fun fromName(name: String, zone: ZoneId, durationOf: (fileName: String) -> Long): Recording? {
        val (stamp, arrow, who) = NAME.matchEntire(name)?.destructured ?: return null
        val started = runCatching { LocalDateTime.parse(stamp, STAMP).atZone(zone).toInstant().toEpochMilli() }
            .getOrNull() ?: return null
        val isNumber = NUMBER.matches(who)
        return Recording(
            fileName = name,
            number = who.takeIf { isNumber },
            // The name kept letters and digits and turned everything else into "-".
            contactName = who.takeUnless { isNumber || it == "unknown" }?.replace('-', ' '),
            direction = when (arrow) {
                "in" -> CallDirection.INCOMING
                "out" -> CallDirection.OUTGOING
                else -> CallDirection.UNKNOWN
            },
            startedAtMillis = started,
            durationMillis = durationOf(name),
            peakAmplitude = Recording.PEAK_NOT_MEASURED,
        )
    }

    private val NAME = Regex("""(\d{4}-\d{2}-\d{2}_\d{2}-\d{2}-\d{2})_(in|out|call)_(.+)\.m4a""")
    private val NUMBER = Regex("""\+?\d+""")
    private val STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss", Locale.US)
}
