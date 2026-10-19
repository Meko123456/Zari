package io.github.meko123456.zari.data

import io.github.meko123456.zari.call.CallDirection
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class RecoveredRecordingsTest {

    private val zone = ZoneId.of("Asia/Tbilisi")
    private val duration: (String) -> Long = { 45_098L }

    @Test
    fun `a name written by forCall reads back as its call`() {
        val name = FileNames.forCall(0, "+995322123456", "Giorgi", CallDirection.INCOMING) { "2026-09-01_14-30-00" }

        val row = RecoveredRecordings.fromName(name, zone, duration)!!

        assertEquals("Giorgi", row.contactName)
        assertNull(row.number)
        assertEquals(CallDirection.INCOMING, row.direction)
        assertEquals(LocalDateTime.of(2026, 9, 1, 14, 30).atZone(zone).toInstant().toEpochMilli(), row.startedAtMillis)
        assertEquals(45_098L, row.durationMillis)
        // Nobody measured it, so it must not be called silent.
        assertFalse(row.isSilent)
    }

    @Test
    fun `a number stays a number and an unknown caller stays withheld`() {
        val number = RecoveredRecordings.fromName("2026-09-01_14-30-00_out_+971501234567.m4a", zone, duration)!!
        assertEquals("+971501234567", number.number)
        assertNull(number.contactName)
        assertEquals(CallDirection.OUTGOING, number.direction)

        val unknown = RecoveredRecordings.fromName("2026-10-07_12-56-20_call_unknown.m4a", zone, duration)!!
        assertEquals("Number withheld", unknown.displayName)
        assertEquals(CallDirection.UNKNOWN, unknown.direction)
    }

    @Test
    fun `a contact's spaces come back from the dashes`() {
        val row = RecoveredRecordings.fromName("2026-09-01_14-30-00_in_Nino-Beridze.m4a", zone, duration)!!
        assertEquals("Nino Beridze", row.contactName)
    }

    @Test
    fun `everything else in the directory is left alone`() {
        listOf(
            "index.json", "index.json.tmp", "index.json.unreadable",
            "pending.m4a", "pending-manual.m4a", "selftest-1791363380123.m4a",
            "2026-13-45_99-99-99_in_Nobody.m4a",
        ).forEach { assertNull(RecoveredRecordings.fromName(it, zone, duration), it) }
    }

    @Test
    fun `only the recordings the index lost are added`() {
        val listed = RecoveredRecordings.fromName("2026-09-01_14-30-00_in_Giorgi.m4a", zone, duration)!!
            .copy(durationMillis = 61_000, peakAmplitude = 9_000)
        val files = listOf("2026-09-01_14-30-00_in_Giorgi.m4a", "2026-09-02_09-00-00_out_Nino.m4a", "index.json")

        val merged = RecoveredRecordings.merge(listOf(listed), files, zone, duration)

        assertEquals(listOf("2026-09-01_14-30-00_in_Giorgi.m4a", "2026-09-02_09-00-00_out_Nino.m4a"), merged.map { it.fileName })
        // The listed row keeps what only the index knew.
        assertEquals(61_000L, merged.first().durationMillis)
        assertEquals(9_000, merged.first().peakAmplitude)
    }
}
