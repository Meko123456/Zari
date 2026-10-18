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
 * An index that will not parse is copied aside before anything replaces it. It read as no rows,
 * and the next recording saved an index of one over the list of every call before it.
 */
class RecordingIndexSafetyTest {

    private lateinit var dir: File
    private val corrupt = """[{"file":"2026-10-17_10-00-00_in_Nino.m4a","number":"+99559"""

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("zari-index").toFile()
    }

    @AfterTest
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun aTruncatedIndexIsUnreadableAndNothingOrAnArrayIsReadable() {
        assertFalse(RecordingIndex.readable(corrupt))
        assertTrue(RecordingIndex.readable(""))
        assertTrue(RecordingIndex.readable("[]"))
        assertTrue(RecordingIndex.readable(RecordingIndex.encode(emptyList())))
    }

    @Test
    fun theUnreadableIndexIsCopiedAsideOnce() {
        val index = File(dir, "index.json").apply { writeText(corrupt) }

        RecordingStore.keepUnreadable(index)
        index.writeText("also broken, later")
        RecordingStore.keepUnreadable(index)

        assertEquals(corrupt, File(dir, "index.json.unreadable").readText(), "the first copy is the one that was really the user's")
    }
}
