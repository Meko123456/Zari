package io.github.meko123456.zari.ui

import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class FormatTest {

    private val tbilisi = ZoneId.of("Asia/Tbilisi")

    @Test
    fun `durations read the way a call log reads them`() {
        assertEquals("0:00", Format.duration(0))
        assertEquals("0:07", Format.duration(7_400))
        assertEquals("1:23", Format.duration(83_000))
        assertEquals("59:59", Format.duration(3_599_000))
        assertEquals("1:00:00", Format.duration(3_600_000))
        assertEquals("2:05:09", Format.duration(7_509_000))
    }

    @Test
    fun `a negative duration cannot produce a nonsense string`() {
        assertEquals("0:00", Format.duration(-5_000))
    }

    @Test
    fun `only today and yesterday are relative`() {
        val today = LocalDate.of(2026, 9, 1)
        val noonToday = today.atTime(12, 0).atZone(tbilisi).toInstant().toEpochMilli()
        val noonYesterday = today.minusDays(1).atTime(12, 0).atZone(tbilisi).toInstant().toEpochMilli()
        val earlier = today.minusDays(3).atTime(12, 0).atZone(tbilisi).toInstant().toEpochMilli()

        assertEquals("Today", Format.dayLabel(noonToday, today, tbilisi))
        assertEquals("Yesterday", Format.dayLabel(noonYesterday, today, tbilisi))
        assertEquals("29 August 2026", Format.dayLabel(earlier, today, tbilisi))
    }

    @Test
    fun `a call just before midnight belongs to that day and not to the next`() {
        val today = LocalDate.of(2026, 9, 1)
        val lateLastNight = today.minusDays(1).atTime(23, 55).atZone(tbilisi).toInstant().toEpochMilli()
        assertEquals("Yesterday", Format.dayLabel(lateLastNight, today, tbilisi))
        assertEquals(today.minusDays(1), Format.dayKey(lateLastNight, tbilisi))
    }
}
