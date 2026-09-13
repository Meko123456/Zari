package io.github.meko123456.zari.data

import android.content.Context
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The recordings and their index, on disk.
 *
 * Files live in the app's **internal** storage. That needs no storage permission, survives app
 * updates, and is removed if the app is uninstalled — all of which was also true of the app's
 * external directory this used to use, which is why the difference is easy to miss. The difference
 * that matters: on Android 8 and 9, `/sdcard/Android/data/<package>/files/` is readable by any app
 * holding `READ_EXTERNAL_STORAGE`, and by anything with adb or a file manager. For recordings of
 * somebody's phone calls that is the wrong default, and it quietly contradicted the backup rules in
 * this same app, which go out of their way to keep recordings off any cloud.
 *
 * Exporting a single recording anywhere else stays a deliberate act, via Share and the FileProvider.
 */
class RecordingStore(context: Context) {

    private val root: File = File(context.filesDir, "recordings")
    private val indexFile = File(root, "index.json")

    private val _recordings = MutableStateFlow<List<Recording>>(emptyList())
    val recordings: StateFlow<List<Recording>> = _recordings

    init {
        root.mkdirs()
        migrateOffExternalStorage(context)
        reload()
    }

    /**
     * Moves anything an older build left in external storage into the private directory, once.
     *
     * Without this, upgrading would not so much lose the recordings as abandon them: they would sit
     * in the world-readable directory this change exists to get them out of, while the app showed an
     * empty list. Copy-then-delete rather than [File.renameTo] because the two directories are
     * usually different mounts, where a rename fails; a file that cannot be copied is left where it
     * is rather than deleted.
     */
    private fun migrateOffExternalStorage(context: Context) {
        val legacy = File(context.getExternalFilesDir(null) ?: return, "recordings")
        moveContents(from = legacy, to = root)
    }

    internal companion object {

        /**
         * Moves every file in [from] into [to], then removes [from] if it is left empty.
         *
         * Separated from the Context so it can be tested against two temporary directories — this is
         * the one piece of this class whose failure mode is losing somebody's recordings, so it is
         * worth being able to prove rather than reason about.
         *
         * Copy-then-delete rather than [File.renameTo] because internal and external storage are
         * usually different mounts, where a rename fails silently. A file that already exists at the
         * destination is left alone and the source removed; a file that cannot be copied is left
         * exactly where it is, so a failed migration loses nothing.
         */
        internal fun moveContents(from: File, to: File) {
            if (!from.isDirectory) return
            to.mkdirs()

            from.listFiles()?.forEach { source ->
                val target = File(to, source.name)
                val safeToRemove = runCatching {
                    if (!target.exists()) source.copyTo(target)
                    true
                }.getOrDefault(false)
                if (safeToRemove) source.delete()
            }
            // Only succeeds once the directory is empty, which is exactly the condition to remove it.
            from.delete()
        }
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
