package io.github.meko123456.zari.data

import io.github.meko123456.zari.call.CallDirection
import org.json.JSONArray
import org.json.JSONObject

/**
 * Reads and writes the recording list as JSON.
 *
 * A file rather than a database: this is a list of a few hundred rows that is only ever appended
 * to and read whole, and `org.json` ships with Android. Room would add a compiler, a schema and a
 * migration story to a problem that does not have one.
 *
 * Unknown fields and malformed rows are skipped rather than fatal — a corrupt index must not cost
 * the recordings themselves, which are the actual valuables and are named well enough to survive
 * without it.
 */
object RecordingIndex {

    fun encode(recordings: List<Recording>): String {
        val array = JSONArray()
        for (recording in recordings) {
            array.put(
                JSONObject().apply {
                    put(FILE, recording.fileName)
                    put(NUMBER, recording.number ?: JSONObject.NULL)
                    put(CONTACT, recording.contactName ?: JSONObject.NULL)
                    put(DIRECTION, recording.direction.name)
                    put(STARTED, recording.startedAtMillis)
                    put(DURATION, recording.durationMillis)
                    put(PEAK, recording.peakAmplitude)
                },
            )
        }
        return array.toString()
    }

    fun decode(json: String): List<Recording> {
        if (json.isBlank()) return emptyList()
        val array = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
        val out = mutableListOf<Recording>()
        for (index in 0 until array.length()) {
            val row = array.optJSONObject(index) ?: continue
            val file = row.optString(FILE).takeIf { it.isNotBlank() } ?: continue
            out += Recording(
                fileName = file,
                number = row.optStringOrNull(NUMBER),
                contactName = row.optStringOrNull(CONTACT),
                direction = runCatching { CallDirection.valueOf(row.optString(DIRECTION)) }
                    .getOrDefault(CallDirection.UNKNOWN),
                startedAtMillis = row.optLong(STARTED),
                durationMillis = row.optLong(DURATION),
                peakAmplitude = row.optInt(PEAK),
            )
        }
        return out
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    private const val FILE = "file"
    private const val NUMBER = "number"
    private const val CONTACT = "contact"
    private const val DIRECTION = "direction"
    private const val STARTED = "started"
    private const val DURATION = "duration"
    private const val PEAK = "peak"
}
