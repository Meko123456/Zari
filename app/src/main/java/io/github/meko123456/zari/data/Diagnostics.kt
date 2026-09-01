package io.github.meko123456.zari.data

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray

/**
 * A short, visible log of what the recorder tried and what the platform said.
 *
 * This is not developer debris — it is a feature. On Android 14 a third-party call recorder can
 * fail in three completely invisible ways: the foreground service is refused, the audio source is
 * refused, or the microphone is handed over and returns silence. Without a record of which, the
 * app just "doesn't work", and the user is left guessing at settings. So every attempt writes a
 * line, and the UI shows the last few.
 */
class Diagnostics(context: Context) {

    private val prefs = context.getSharedPreferences("zari-diagnostics", Context.MODE_PRIVATE)

    fun log(message: String, atMillis: Long = System.currentTimeMillis()) {
        val entries = read().toMutableList()
        entries.add(0, Entry(atMillis, message))
        while (entries.size > LIMIT) entries.removeAt(entries.size - 1)
        val array = JSONArray()
        for (entry in entries) {
            array.put(JSONArray().put(entry.atMillis).put(entry.message))
        }
        prefs.edit { putString(KEY, array.toString()) }
    }

    fun read(): List<Entry> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val row = array.optJSONArray(index) ?: return@mapNotNull null
            Entry(row.optLong(0), row.optString(1))
        }
    }

    data class Entry(val atMillis: Long, val message: String)

    private companion object {
        const val KEY = "log"
        const val LIMIT = 25
    }
}
