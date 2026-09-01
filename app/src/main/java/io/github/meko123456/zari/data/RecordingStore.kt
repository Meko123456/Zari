package io.github.meko123456.zari.data

import android.content.Context
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The recordings and their index, on disk.
 *
 * Files live in the app's own external directory. That is "local storage" with no storage
 * permission at all, it survives app updates, and it is removed if the app is uninstalled —
 * which for recordings of your own phone calls is the right default. Exporting a single
 * recording anywhere else is a deliberate act, via Share.
 */
class RecordingStore(context: Context) {

    private val root: File = File(context.getExternalFilesDir(null) ?: context.filesDir, "recordings")
    private val indexFile = File(root, "index.json")

    private val _recordings = MutableStateFlow<List<Recording>>(emptyList())
    val recordings: StateFlow<List<Recording>> = _recordings

    init {
        root.mkdirs()
        reload()
    }

    fun fileFor(recording: Recording): File = File(root, recording.fileName)

    fun newFile(name: String): File {
        root.mkdirs()
        return File(root, name)
    }

    fun reload() {
        val stored = runCatching { indexFile.readText() }.getOrDefault("")
        // Only list recordings whose audio is actually still there. A row pointing at a deleted
        // file is worse than no row: it plays silence and looks like a bug in the recorder.
        _recordings.value = RecordingIndex.decode(stored)
            .filter { File(root, it.fileName).exists() }
            .sortedByDescending { it.startedAtMillis }
    }

    fun add(recording: Recording) {
        val updated = (_recordings.value + recording).sortedByDescending { it.startedAtMillis }
        persist(updated)
    }

    fun delete(recording: Recording) {
        File(root, recording.fileName).delete()
        persist(_recordings.value.filterNot { it.fileName == recording.fileName })
    }

    private fun persist(list: List<Recording>) {
        _recordings.value = list
        runCatching {
            root.mkdirs()
            // Write beside the target and rename, so a kill mid-write cannot leave a half-written
            // index — the recordings would survive it, but the metadata would not.
            val temp = File(root, "index.json.tmp")
            temp.writeText(RecordingIndex.encode(list))
            temp.renameTo(indexFile)
        }
    }
}
