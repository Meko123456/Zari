package io.github.meko123456.zari.data

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The one-time move of recordings off external storage.
 *
 * Recordings are the only thing in this app that cannot be recreated, so the migration that carries
 * them gets tested rather than reasoned about.
 */
class RecordingStoreMigrationTest {

    private lateinit var from: File
    private lateinit var to: File

    @BeforeTest
    fun setUp() {
        from = Files.createTempDirectory("zari-legacy").toFile()
        to = Files.createTempDirectory("zari-internal").toFile()
    }

    @AfterTest
    fun tearDown() {
        from.deleteRecursively()
        to.deleteRecursively()
    }

    private fun write(dir: File, name: String, body: String) =
        File(dir, name).apply { parentFile?.mkdirs(); writeText(body) }

    @Test
    fun `recordings and the index move across and the old directory goes`() {
        write(from, "2026-09-01_14-30-00_in_Giorgi.m4a", "audio")
        write(from, "index.json", "[]")

        RecordingStore.moveContents(from, to)

        assertEquals("audio", File(to, "2026-09-01_14-30-00_in_Giorgi.m4a").readText())
        assertEquals("[]", File(to, "index.json").readText())
        assertFalse(from.exists(), "the legacy directory should be gone once it is empty")
    }

    @Test
    fun `a second run is harmless`() {
        write(from, "call.m4a", "audio")
        RecordingStore.moveContents(from, to)
        RecordingStore.moveContents(from, to)

        assertEquals("audio", File(to, "call.m4a").readText())
        assertEquals(1, to.listFiles()?.size)
    }

    @Test
    fun `a file already at the destination is not overwritten`() {
        // The destination wins: it is the one the app has been reading and writing since the move.
        write(from, "call.m4a", "old")
        write(to, "call.m4a", "current")

        RecordingStore.moveContents(from, to)

        assertEquals("current", File(to, "call.m4a").readText())
        assertFalse(File(from, "call.m4a").exists(), "the stale copy should not be left behind")
    }

    @Test
    fun `nothing to move is not an error`() {
        RecordingStore.moveContents(File(from, "never-existed"), to)
        assertTrue(to.listFiles().isNullOrEmpty())
    }

    @Test
    fun `the destination is created if it is not there yet`() {
        val fresh = File(to, "recordings")
        write(from, "call.m4a", "audio")

        RecordingStore.moveContents(from, fresh)

        assertEquals("audio", File(fresh, "call.m4a").readText())
    }
}
