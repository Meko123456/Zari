package io.github.meko123456.zari.data

import io.github.meko123456.zari.call.CallDirection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecordingIndexTest {

    private val recording = Recording(
        fileName = "2026-09-01_14-30-00_in_Giorgi.m4a",
        number = "+995322123456",
        contactName = "Giorgi",
        direction = CallDirection.INCOMING,
        startedAtMillis = 1_756_000_000_000,
        durationMillis = 92_000,
        peakAmplitude = 8_400,
    )

    @Test
    fun `a recording survives a round trip`() {
        val decoded = RecordingIndex.decode(RecordingIndex.encode(listOf(recording)))
        assertEquals(listOf(recording), decoded)
    }

    @Test
    fun `a withheld number round trips as null rather than as the string null`() {
        val anonymous = recording.copy(number = null, contactName = null)
        val decoded = RecordingIndex.decode(RecordingIndex.encode(listOf(anonymous))).single()
        assertNull(decoded.number)
        assertNull(decoded.contactName)
        assertEquals("Unknown number", decoded.displayName)
    }

    @Test
    fun `a corrupt index costs the metadata but never throws`() {
        // The recordings themselves are the valuables, and they are named well enough to survive
        // the index being unreadable.
        assertTrue(RecordingIndex.decode("not json at all").isEmpty())
        assertTrue(RecordingIndex.decode("").isEmpty())
        assertTrue(RecordingIndex.decode("{}").isEmpty())
    }

    @Test
    fun `a row missing its file name is skipped and the rest are kept`() {
        val json = """[{"number":"+1"},${RecordingIndex.encode(listOf(recording)).trim('[', ']')}]"""
        assertEquals(1, RecordingIndex.decode(json).size)
    }

    @Test
    fun `an unrecognised direction degrades to unknown instead of failing the whole list`() {
        val json = RecordingIndex.encode(listOf(recording)).replace("INCOMING", "SIDEWAYS")
        assertEquals(CallDirection.UNKNOWN, RecordingIndex.decode(json).single().direction)
    }

    @Test
    fun `a silent recording is recognisable after a round trip`() {
        val silent = recording.copy(peakAmplitude = 12)
        assertTrue(RecordingIndex.decode(RecordingIndex.encode(listOf(silent))).single().isSilent)
        assertTrue(!RecordingIndex.decode(RecordingIndex.encode(listOf(recording))).single().isSilent)
    }
}
