package io.github.meko123456.zari.data

import android.content.Context
import androidx.core.content.edit
import io.github.meko123456.zari.call.AudioSourceUsed

/**
 * Remembers which audio source actually produced sound during a call, and the verdict when none
 * did.
 *
 * Without this the app would re-probe every source at the start of every call, costing the first
 * seconds of audio forever. With it, the probe runs once: either a source is found and used
 * directly from then on, or the device is known to mute third-party recording during calls and the
 * app can say so plainly instead of producing a silent file every time and hoping.
 */
class SourceMemory(context: Context) {

    private val prefs = context.getSharedPreferences("zari-source", Context.MODE_PRIVATE)

    /** The source known to work during a call, or null if unknown or none. */
    fun working(): AudioSourceUsed? = prefs.getString(KEY_SOURCE, null)
        ?.let { name -> runCatching { AudioSourceUsed.valueOf(name) }.getOrNull() }
        ?.takeIf { it != AudioSourceUsed.NONE }

    /** True once every source has been tried during a real call and all of them read silence. */
    fun allSilent(): Boolean = prefs.getBoolean(KEY_ALL_SILENT, false)

    /** What each source measured on the last probe, for the UI to show as evidence. */
    fun lastProbe(): List<Pair<String, Int>> {
        val raw = prefs.getString(KEY_PROBE, null) ?: return emptyList()
        return raw.split('|').mapNotNull { entry ->
            val parts = entry.split('=')
            if (parts.size != 2) null else parts[0] to (parts[1].toIntOrNull() ?: return@mapNotNull null)
        }
    }

    fun remember(source: AudioSourceUsed) {
        prefs.edit {
            putString(KEY_SOURCE, source.name)
            putBoolean(KEY_ALL_SILENT, false)
        }
    }

    fun rememberAllSilent(results: List<Pair<AudioSourceUsed, Int?>>) {
        prefs.edit {
            remove(KEY_SOURCE)
            putBoolean(KEY_ALL_SILENT, true)
            putString(
                KEY_PROBE,
                results.joinToString("|") { (source, peak) -> "${source.name}=${peak ?: -1}" },
            )
        }
    }

    /** Forgets everything, so the next call probes again. */
    fun clear() {
        prefs.edit { clear() }
    }

    private companion object {
        const val KEY_SOURCE = "source"
        const val KEY_ALL_SILENT = "all-silent"
        const val KEY_PROBE = "probe"
    }
}
